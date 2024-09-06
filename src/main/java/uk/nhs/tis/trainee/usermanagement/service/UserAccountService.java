/*
 * The MIT License (MIT)
 *
 * Copyright 2022 Crown Copyright (Health Education England)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.nhs.tis.trainee.usermanagement.service;

import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.model.AdminAddUserToGroupRequest;
import com.amazonaws.services.cognitoidp.model.AdminDeleteUserRequest;
import com.amazonaws.services.cognitoidp.model.AdminGetUserRequest;
import com.amazonaws.services.cognitoidp.model.AdminGetUserResult;
import com.amazonaws.services.cognitoidp.model.AdminListGroupsForUserRequest;
import com.amazonaws.services.cognitoidp.model.AdminListGroupsForUserResult;
import com.amazonaws.services.cognitoidp.model.AdminListUserAuthEventsRequest;
import com.amazonaws.services.cognitoidp.model.AdminListUserAuthEventsResult;
import com.amazonaws.services.cognitoidp.model.AdminRemoveUserFromGroupRequest;
import com.amazonaws.services.cognitoidp.model.AdminSetUserMFAPreferenceRequest;
import com.amazonaws.services.cognitoidp.model.AdminUpdateUserAttributesRequest;
import com.amazonaws.services.cognitoidp.model.AttributeType;
import com.amazonaws.services.cognitoidp.model.AuthEventType;
import com.amazonaws.services.cognitoidp.model.GroupType;
import com.amazonaws.services.cognitoidp.model.ListUsersRequest;
import com.amazonaws.services.cognitoidp.model.ListUsersResult;
import com.amazonaws.services.cognitoidp.model.SMSMfaSettingsType;
import com.amazonaws.services.cognitoidp.model.SoftwareTokenMfaSettingsType;
import com.amazonaws.services.cognitoidp.model.TooManyRequestsException;
import com.amazonaws.services.cognitoidp.model.UserNotFoundException;
import com.amazonaws.services.cognitoidp.model.UserStatusType;
import com.amazonaws.services.cognitoidp.model.UserType;
import com.amazonaws.xray.spring.aop.XRayEnabled;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;
import uk.nhs.tis.trainee.usermanagement.dto.UserAccountDetailsDto;
import uk.nhs.tis.trainee.usermanagement.dto.UserLoginDetailsDto;
import uk.nhs.tis.trainee.usermanagement.enumeration.MfaType;

@Slf4j
@Service
@XRayEnabled
public class UserAccountService {

  private static final String USER_ID_CACHE = "UserId";

  private static final String NO_ACCOUNT = "NO_ACCOUNT";
  private static final String NO_MFA = "NO_MFA";
  private static final Integer MAX_LOGIN_EVENTS = 10;

  private static final String METRIC_NAME_MFA_RESET = "account.mfa.reset";
  private static final String METRIC_NAME_ACCOUNT_DELETE = "account.delete";

  private final AWSCognitoIdentityProvider cognitoIdp;
  private final String userPoolId;
  private final Cache cache;

  private final EventPublishService eventPublishService;

  private Instant lastUserCaching = null;

  private final Map<MfaType, Map<UserStatusType, Counter>> deleteAccountCounters;
  private final Map<MfaType, Counter> resetMfaCounters;

  UserAccountService(AWSCognitoIdentityProvider cognitoIdp,
      @Value("${application.aws.cognito.user-pool-id}") String userPoolId,
      CacheManager cacheManager, EventPublishService eventPublishService,
                     MeterRegistry meterRegistry,
                     @Value("${application.environment}") String environment) {
    this.cognitoIdp = cognitoIdp;
    this.userPoolId = userPoolId;
    cache = cacheManager.getCache(USER_ID_CACHE);
    this.eventPublishService = eventPublishService;

    this.deleteAccountCounters = new EnumMap<>(MfaType.class);
    this.resetMfaCounters = new EnumMap<>(MfaType.class);
    for (MfaType mfaType : MfaType.values()) {
      Map<UserStatusType, Counter> userStatusTypeMap = new EnumMap<>(UserStatusType.class);
      for (UserStatusType userStatusType : UserStatusType.values()) {
        userStatusTypeMap.put(userStatusType,
            meterRegistry.counter(METRIC_NAME_ACCOUNT_DELETE,
                "Environment", environment,
                "UserStatus", userStatusType.toString(),
                "MfaType", mfaType.name()));
      }
      this.deleteAccountCounters.put(mfaType, userStatusTypeMap);
      this.resetMfaCounters.put(mfaType, meterRegistry.counter(METRIC_NAME_MFA_RESET,
          "Environment", environment,
          "MfaType", mfaType.name()));
    }
  }

  /**
   * Get the user account details for the account associated with the given username.
   *
   * @param username The username for the account.
   * @return The user account details.
   */
  public UserAccountDetailsDto getUserAccountDetails(String username) {
    log.info("Retrieving user with username '{}'.", username);
    AdminGetUserRequest request = new AdminGetUserRequest();
    request.setUserPoolId(userPoolId);
    request.setUsername(username);

    UserAccountDetailsDto userAccountDetails;

    try {
      AdminGetUserResult result = cognitoIdp.adminGetUser(request);
      String preferredMfa = result.getPreferredMfaSetting();

      String mfaStatus = preferredMfa == null ? NO_MFA : preferredMfa;
      Instant createdAt = null;
      if (result.getUserCreateDate() != null) {
        createdAt = result.getUserCreateDate().toInstant();
      }
      userAccountDetails = new UserAccountDetailsDto(mfaStatus, result.getUserStatus(),
          getUserGroups(username), createdAt);
    } catch (UserNotFoundException e) {
      log.info("User '{}' not found.", username);
      userAccountDetails = new UserAccountDetailsDto(NO_ACCOUNT, NO_ACCOUNT, List.of(), null);
    }

    return userAccountDetails;
  }

  /**
   * Update the email for the given user account.
   *
   * @param userId   The ID of the user account.
   * @param newEmail The new email to be used.
   */
  public void updateEmail(String userId, String newEmail) {
    log.info("Updating email to '{}' for user '{}'.", newEmail, userId);

    try {
      // Verify that the new email is not already used.
      AdminGetUserRequest request = new AdminGetUserRequest();
      request.setUserPoolId(userPoolId);
      request.setUsername(newEmail);
      AdminGetUserResult result = cognitoIdp.adminGetUser(request);
      String existingUserId = result.getUsername();

      if (existingUserId.equals(userId)) {
        log.info("The email for this user has not changed, skipping update.");
      } else {
        String message = String.format("The email '%s' is already in use by user '%s'.", newEmail,
            existingUserId);
        throw new IllegalArgumentException(message);
      }
    } catch (UserNotFoundException e) {
      // If an existing user was not found then the new email address can be used.
      AdminGetUserRequest getRequest = new AdminGetUserRequest();
      getRequest.setUserPoolId(userPoolId);
      getRequest.setUsername(userId);
      AdminGetUserResult existingDetails = cognitoIdp.adminGetUser(getRequest);
      final Optional<String> existingEmail = existingDetails.getUserAttributes().stream()
          .filter(ua -> ua.getName().equals("email"))
          .map(AttributeType::getValue)
          .findFirst();
      final Optional<String> traineeId = existingDetails.getUserAttributes().stream()
          .filter(ua -> ua.getName().equals("custom:tisId"))
          .map(AttributeType::getValue)
          .findFirst();

      AdminUpdateUserAttributesRequest updateRequest = new AdminUpdateUserAttributesRequest();
      updateRequest.setUserPoolId(userPoolId);
      updateRequest.setUsername(userId);

      updateRequest.setUserAttributes(List.of(
          new AttributeType().withName("email").withValue(newEmail),
          new AttributeType().withName("email_verified").withValue("true")
      ));

      cognitoIdp.adminUpdateUserAttributes(updateRequest);
      eventPublishService.publishEmailUpdateEvent(userId, traineeId.orElse(null),
          existingEmail.orElse(null), newEmail);
      log.info("Successfully updated email to '{}' for user '{}'.", newEmail, userId);
    }
  }

  /**
   * Get the list of user login event details for the account associated with the given username.
   *
   * @param username The username for the account.
   * @return The list of user login event details.
   */
  public List<UserLoginDetailsDto> getUserLoginDetails(String username) {
    log.info("Retrieving login events for user with username '{}'.", username);
    AdminListUserAuthEventsRequest request = new AdminListUserAuthEventsRequest();
    request.setUserPoolId(userPoolId);
    request.setUsername(username);
    request.setMaxResults(MAX_LOGIN_EVENTS); //results are sorted in descending CreationDate order

    List<UserLoginDetailsDto> userLoginDetailsList;

    try {
      AdminListUserAuthEventsResult result = cognitoIdp.adminListUserAuthEvents(request);
      userLoginDetailsList = getLoginDetailsListFromAuthEvents(result);

    } catch (UserNotFoundException e) {
      log.info("User '{}' not found.", username);
      return List.of();
    }

    return userLoginDetailsList;
  }

  /**
   * Retrieve the list of UserLoginDetailsDtos from the user auth events list.
   *
   * @param authEventsResult the result of the auth events call.
   * @return the list of UserLoginDetailsDtos, or an empty list if no auth events exist.
   */
  private List<UserLoginDetailsDto> getLoginDetailsListFromAuthEvents(
      AdminListUserAuthEventsResult authEventsResult) {
    List<UserLoginDetailsDto> userLoginDetailsList = new ArrayList<>();
    List<AuthEventType> authEvents = authEventsResult.getAuthEvents();

    authEvents.forEach(authEvent -> {
      String eventId = authEvent.getEventId();
      Date eventDate = authEvent.getCreationDate();
      String event = authEvent.getEventType();
      String eventResult = authEvent.getEventResponse();
      String device = authEvent.getEventContextData().getDeviceName();
      String challenges = authEvent.getChallengeResponses().stream()
          .map(it -> it.getChallengeName() + ":" + it.getChallengeResponse())
          .collect(Collectors.joining(", "));

      UserLoginDetailsDto eventDto
          = new UserLoginDetailsDto(eventId, eventDate, event, eventResult, challenges, device);
      userLoginDetailsList.add(eventDto);
    });
    return userLoginDetailsList;
  }

  /**
   * Get the groups for the given user.
   *
   * @param username The username for the account.
   * @return A list of group names, or an empty list if the user was not found.
   */
  private List<String> getUserGroups(String username) {
    log.info("Retrieving groups for username '{}'.", username);
    AdminListGroupsForUserRequest request = new AdminListGroupsForUserRequest();
    request.setUserPoolId(userPoolId);
    request.setUsername(username);

    try {
      AdminListGroupsForUserResult result = cognitoIdp.adminListGroupsForUser(request);
      return result.getGroups().stream()
          .map(GroupType::getGroupName)
          .toList();
    } catch (UserNotFoundException e) {
      log.info("User '{}' not found while retrieving groups.", username);
      return List.of();
    }
  }

  /**
   * Reset the MFA for the given user.
   *
   * @param username The username of the user.
   */
  public void resetUserAccountMfa(String username) {
    log.info("Resetting MFA for user '{}'.", username);
    AdminSetUserMFAPreferenceRequest request = new AdminSetUserMFAPreferenceRequest();
    request.setUserPoolId(userPoolId);
    request.setUsername(username);

    MfaType oldMfaType = getUserMfaType(username);
    this.resetMfaCounters.get(oldMfaType).increment();

    request.setSMSMfaSettings(new SMSMfaSettingsType().withEnabled(false));
    request.setSoftwareTokenMfaSettings(new SoftwareTokenMfaSettingsType().withEnabled(false));

    cognitoIdp.adminSetUserMFAPreference(request);
    log.info("MFA reset for user '{}'.", username);
  }

  /**
   * Delete the account for the given user.
   *
   * @param username The username of the user.
   */
  public void deleteCognitoAccount(String username) {
    log.info("Deleting the Cognito account for user '{}'.", username);
    AdminDeleteUserRequest request = new AdminDeleteUserRequest();
    request.setUserPoolId(userPoolId);
    request.setUsername(username);

    MfaType oldMfaType = getUserMfaType(username);
    UserStatusType userStatusType = getUserStatus(username);
    deleteAccountCounters.get(oldMfaType).get(userStatusType).increment();

    cognitoIdp.adminDeleteUser(request);
    log.info("Deleted Cognito account for user '{}'.", username);
  }

  /**
   * Add the user to a user group.
   *
   * @param username  The username of the user.
   * @param groupName The group name to add the user to.
   */
  public void enrollToUserGroup(String username, String groupName) {
    log.info("Enrolling user '{}' to the '{}' group.", username, groupName);
    AdminAddUserToGroupRequest request = new AdminAddUserToGroupRequest();
    request.setUserPoolId(userPoolId);
    request.setGroupName(groupName);
    request.setUsername(username);

    cognitoIdp.adminAddUserToGroup(request);
    log.info("User '{}' has been enrolled to the {} group.", username, groupName);
  }

  /**
   * Remove the user from a user group.
   *
   * @param username  The username of the user.
   * @param groupName The group name to remove the user from.
   */
  public void withdrawFromUserGroup(String username, String groupName) {
    log.info("Withdrawing user '{}' from the '{}' group.", username, groupName);
    AdminRemoveUserFromGroupRequest request = new AdminRemoveUserFromGroupRequest();
    request.setUserPoolId(userPoolId);
    request.setGroupName(groupName);
    request.setUsername(username);

    cognitoIdp.adminRemoveUserFromGroup(request);
    log.info("User '{}' has been withdrawn from the {} group.", username, groupName);
  }

  /**
   * Get all user account IDs associated with the given person ID.
   *
   * @param personId The person ID to get the user IDs for.
   * @return The found user IDs, or empty if not found.
   */
  @Cacheable(cacheNames = USER_ID_CACHE, unless = "#result.isEmpty()")
  public Set<String> getUserAccountIds(String personId) {
    log.info("User account not found in the cache.");

    // Skip caching if we already cached in the last fifteen minutes.
    if (lastUserCaching == null || lastUserCaching.plus(Duration.ofMinutes(15))
        .isBefore(Instant.now())) {
      cacheAllUserAccountIds();
      lastUserCaching = Instant.now();
    }

    Set<String> userAccountIds = cache.get(personId, Set.class);
    return userAccountIds != null ? userAccountIds : Set.of();
  }

  /**
   * Retrieve and cache a mapping of all person IDs to user IDs.
   */
  private void cacheAllUserAccountIds() {
    log.info("Caching all user account ids from Cognito.");

    StopWatch cacheTimer = new StopWatch();
    cacheTimer.start();

    String paginationToken = null;

    do {
      ListUsersRequest request = new ListUsersRequest();
      request.setUserPoolId(userPoolId);
      request.setPaginationToken(paginationToken);

      try {
        ListUsersResult result = cognitoIdp.listUsers(request);
        cacheUserAccountIds(result);
        paginationToken = result.getPaginationToken();
      } catch (TooManyRequestsException tmre) {
        try {
          // Cognito requests are limited to 5 per second.
          log.warn("Cognito requests have exceed the limit.", tmre);
          Thread.sleep(200);
        } catch (InterruptedException ie) {
          log.warn("Unable to sleep thread.", ie);
          Thread.currentThread().interrupt();
        }
      }
    } while (paginationToken != null);

    cacheTimer.stop();
    log.info("Total time taken to cache all user accounts was: {}s",
        cacheTimer.getTotalTimeSeconds());
  }

  /**
   * Cache the user accounts ids for the given result.
   *
   * @param result The result of a ListUsersRequest.
   */
  private void cacheUserAccountIds(ListUsersResult result) {
    result.getUsers().stream()
        .map(UserType::getAttributes)
        .map(attributes -> attributes.stream()
            .collect(Collectors.toMap(AttributeType::getName, AttributeType::getValue))
        )
        .forEach(attr -> {
          String tisId = attr.get("custom:tisId");
          Set<String> ids = cache.get(tisId, Set.class);

          if (ids == null) {
            ids = new HashSet<>();
          }

          ids.add(attr.get("sub"));
          cache.put(tisId, ids);
        });
  }

  public MfaType getUserMfaType(String username) {
    AdminSetUserMFAPreferenceRequest request = new AdminSetUserMFAPreferenceRequest();
    request.setUserPoolId(userPoolId);
    request.setUsername(username);

    if (Boolean.TRUE.equals(request.getSoftwareTokenMfaSettings().getEnabled())) {
      return MfaType.TOTP;
    }
    if (Boolean.TRUE.equals(request.getSMSMfaSettings().getEnabled())) {
      return MfaType.SMS;
    }
    return MfaType.NO_MFA;
  }

  public UserStatusType getUserStatus(String username) {
    AdminGetUserRequest request = new AdminGetUserRequest();
    request.setUserPoolId(userPoolId);
    request.setUsername(username);
    AdminGetUserResult result = cognitoIdp.adminGetUser(request);
    return UserStatusType.valueOf(result.getUserStatus());
  }
}
