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

import static uk.nhs.tis.trainee.usermanagement.enumeration.MfaType.NO_MFA;

import com.amazonaws.services.cognitoidp.model.AdminAddUserToGroupRequest;
import com.amazonaws.services.cognitoidp.model.AdminDeleteUserRequest;
import com.amazonaws.services.cognitoidp.model.AdminListUserAuthEventsRequest;
import com.amazonaws.services.cognitoidp.model.AdminListUserAuthEventsResult;
import com.amazonaws.services.cognitoidp.model.AdminRemoveUserFromGroupRequest;
import com.amazonaws.services.cognitoidp.model.AdminSetUserMFAPreferenceRequest;
import com.amazonaws.services.cognitoidp.model.AttributeType;
import com.amazonaws.services.cognitoidp.model.AuthEventType;
import com.amazonaws.services.cognitoidp.model.EventResponseType;
import com.amazonaws.services.cognitoidp.model.EventType;
import com.amazonaws.services.cognitoidp.model.ListUsersRequest;
import com.amazonaws.services.cognitoidp.model.ListUsersResult;
import com.amazonaws.services.cognitoidp.model.SMSMfaSettingsType;
import com.amazonaws.services.cognitoidp.model.SoftwareTokenMfaSettingsType;
import com.amazonaws.services.cognitoidp.model.TooManyRequestsException;
import com.amazonaws.services.cognitoidp.model.UserNotFoundException;
import com.amazonaws.services.cognitoidp.model.UserStatusType;
import com.amazonaws.services.cognitoidp.model.UserType;
import com.amazonaws.xray.spring.aop.XRayEnabled;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
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
  private static final Integer MAX_LOGIN_EVENTS = 10;

  private static final String ATTRIBUTE_EMAIL = "email";
  private static final String ATTRIBUTE_EMAIL_VERIFIED = "email_verified";
  private static final String ATTRIBUTE_MFA_TYPE = "custom:mfaType";
  private static final String ATTRIBUTE_SUB = "sub";
  private static final String ATTRIBUTE_TIS_ID = "custom:tisId";

  private final MetricsService metricsService;

  private final CognitoService cognitoService;
  private final String userPoolId;
  private final Cache cache;

  private final EventPublishService eventPublishService;

  private Instant lastUserCaching = null;

  UserAccountService(CognitoService cognitoService,
      @Value("${application.aws.cognito.user-pool-id}") String userPoolId,
      CacheManager cacheManager, EventPublishService eventPublishService,
      MetricsService metricsService) {
    this.cognitoService = cognitoService;
    this.userPoolId = userPoolId;
    cache = cacheManager.getCache(USER_ID_CACHE);
    this.eventPublishService = eventPublishService;
    this.metricsService = metricsService;
  }

  /**
   * Get the user account details for the account associated with the given username.
   *
   * <p><b>Warning</b>: this will contribute to monthly active user (MAU) count for the purposes of
   * billing.
   *
   * @param username The username for the account.
   * @return The user account details.
   */
  public UserAccountDetailsDto getUserAccountDetails(String username) {
    log.info("Retrieving user with username '{}'.", username);

    try {
      return cognitoService.getUserDetails(username);
    } catch (UserNotFoundException e) {
      log.info("User '{}' not found.", username);
      return UserAccountDetailsDto.builder()
          .mfaStatus(NO_ACCOUNT)
          .userStatus(NO_ACCOUNT)
          .build();
    }
  }

  /**
   * Update the Contact Details for the given user account.
   *
   * @param userId    The ID of the user account.
   * @param newEmail  The new email to be used.
   * @param forenames The new forenames to be used.
   * @param surname   The new surname to be used.
   */
  public void updateContactDetails(String userId, String newEmail, String forenames,
      String surname) {
    log.info("Updating email to '{}' for user '{}'.", newEmail, userId);

    List<AttributeType> attributeTypes = new ArrayList<>();
    attributeTypes.add(new AttributeType().withName("family_name").withValue(surname));
    attributeTypes.add(new AttributeType().withName("given_name").withValue(forenames));

    try {
      // Verify that the new email is not already used.
      UserAccountDetailsDto existingUser = cognitoService.getUserDetails(newEmail);
      String existingUserId = existingUser.getId();

      if (existingUserId.equals(userId)) {
        log.info("The email for this user has not changed, skipping email update.");
        cognitoService.updateAttributes(userId, attributeTypes);
      } else {
        String message = String.format("The email '%s' is already in use by user '%s'.", newEmail,
            existingUserId);
        throw new IllegalArgumentException(message);
      }
    } catch (UserNotFoundException e) {
      // If an existing user was not found then the new email address can be used.
      attributeTypes.add(new AttributeType().withName(ATTRIBUTE_EMAIL).withValue(newEmail));
      attributeTypes.add(new AttributeType().withName(ATTRIBUTE_EMAIL_VERIFIED).withValue("true"));
      cognitoService.updateAttributes(userId, attributeTypes);

      UserAccountDetailsDto existingUser = getUserAccountDetails(userId);
      String traineeId = existingUser.getTraineeId();
      String existingEmail = existingUser.getEmail();
      eventPublishService.publishEmailUpdateEvent(userId, traineeId, existingEmail, newEmail);
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
      AdminListUserAuthEventsResult result = cognitoService.adminListUserAuthEvents(request);
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
   * Reset the MFA for the given user.
   *
   * @param username The username of the user.
   */
  public void resetUserAccountMfa(String username) {
    log.info("Resetting MFA for user '{}'.", username);

    UserAccountDetailsDto user = cognitoService.getUserDetails(username);
    MfaType mfaType = MfaType.valueOf(user.getMfaStatus());
    metricsService.incrementMfaResetCounter(mfaType);

    AdminSetUserMFAPreferenceRequest request = new AdminSetUserMFAPreferenceRequest()
        .withUserPoolId(userPoolId)
        .withUsername(username)
        .withSMSMfaSettings(new SMSMfaSettingsType().withEnabled(false))
        .withSoftwareTokenMfaSettings(new SoftwareTokenMfaSettingsType().withEnabled(false));
    cognitoService.adminSetUserMfaPreference(request);

    cognitoService.updateAttributes(username,
        List.of(new AttributeType().withName(ATTRIBUTE_MFA_TYPE).withValue(NO_MFA.toString())));
    log.info("MFA reset for user '{}'.", username);
  }

  /**
   * Delete duplicate accounts for a trainee, leaving a single account. Deletion will only be
   * performed if there is enough certainty about which account to keep.
   *
   * @param traineeId    The ID of the trainee.
   * @param accountIds   The list of identified duplicates.
   * @param currentEmail The current email of the trainee.
   * @return The remaining account ID, empty if duplicates could not be deleted.
   */
  public Optional<String> deleteDuplicateAccounts(String traineeId, Set<String> accountIds,
      String currentEmail) {
    log.info("{} accounts found for trainee {}, deleting duplicates. Found: [{}]",
        accountIds.size(), traineeId, String.join(",", accountIds));
    String mainAccount = identifyMainAccount(traineeId, accountIds, currentEmail);

    if (mainAccount == null) {
      log.info("Could not determine the main account for trainee {}, skipping de-duplication.",
          traineeId);
      return Optional.empty();
    }

    accountIds.stream()
        .filter(accountId -> !accountId.equals(mainAccount))
        .forEach(this::deleteCognitoAccount);
    return Optional.of(mainAccount);
  }

  /**
   * Identifies the main account out of multiple duplicates.
   *
   * @param traineeId    The ID of the trainee.
   * @param accountIds   The list of identified duplicates.
   * @param currentEmail The current email of the trainee.
   * @return The main account, based on a set of rules. Null if it could not be determined.
   */
  private String identifyMainAccount(String traineeId, Set<String> accountIds,
      String currentEmail) {
    String currentEmailId = null;

    try {
      UserAccountDetailsDto currentEmailAccount = cognitoService.getUserDetails(currentEmail);
      currentEmailId = currentEmailAccount.getId();
    } catch (UserNotFoundException e) {
      log.info("No existing account found for trainee {} matching current TIS email '{}'.",
          traineeId, currentEmail);
    }

    if (currentEmailId != null && accountIds.contains(currentEmailId)) {
      log.info("Found existing account {} for trainee {} matching current TIS email '{}'.",
          currentEmailId, traineeId, currentEmail);
      return currentEmailId;
    }

    for (String accountId : accountIds) {
      Instant lastSignIn = getLastSuccessfulSignIn(accountId);

      if (lastSignIn == null) {
        log.info("Found successless account {} for trainee {}.", accountId, traineeId);
      }
    }

    return null;
  }

  /**
   * Get the last successful sign-in timestamp for a particular user.
   *
   * @param username The username for the account.
   * @return The timestamp of the last successful sign-in, or null if no success found.
   */
  private Instant getLastSuccessfulSignIn(String username) {
    String paginationToken = null;

    do {
      AdminListUserAuthEventsRequest listAuthRequest = new AdminListUserAuthEventsRequest()
          .withUserPoolId(userPoolId)
          .withUsername(username)
          .withNextToken(paginationToken);

      try {
        AdminListUserAuthEventsResult result = cognitoService.adminListUserAuthEvents(
            listAuthRequest);

        Optional<Date> lastSignIn = result.getAuthEvents().stream()
            .filter(event -> event.getEventType().equals(EventType.SignIn.toString()))
            .filter(event -> event.getEventResponse().equals(EventResponseType.Pass.toString()))
            .map(AuthEventType::getCreationDate)
            .findFirst();

        if (lastSignIn.isPresent()) {
          return lastSignIn.get().toInstant();
        } else {
          paginationToken = result.getNextToken();
        }
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

    return null;
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

    UserAccountDetailsDto user = cognitoService.getUserDetails(username);
    MfaType oldMfaType = MfaType.valueOf(user.getMfaStatus());
    UserStatusType userStatusType = UserStatusType.valueOf(user.getUserStatus());
    metricsService.incrementDeleteAccountCounter(oldMfaType, userStatusType);

    cognitoService.adminDeleteUser(request);
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

    cognitoService.adminAddUserToGroup(request);
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

    cognitoService.adminRemoveUserFromGroup(request);
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
        ListUsersResult result = cognitoService.listUsers(request);
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
          String tisId = attr.get(ATTRIBUTE_TIS_ID);
          Set<String> ids = cache.get(tisId, Set.class);

          if (ids == null) {
            ids = new HashSet<>();
          }

          ids.add(attr.get(ATTRIBUTE_SUB));
          cache.put(tisId, ids);
        });
  }
}
