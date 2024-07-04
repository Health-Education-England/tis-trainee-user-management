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
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListGroupsForUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListGroupsForUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListUserAuthEventsRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListUserAuthEventsResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRemoveUserFromGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserMfaPreferenceRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUpdateUserAttributesRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthEventType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GroupType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.TooManyRequestsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;
import uk.nhs.tis.trainee.usermanagement.dto.UserAccountDetailsDto;
import uk.nhs.tis.trainee.usermanagement.dto.UserLoginDetailsDto;

@Slf4j
@Service
@XRayEnabled
public class UserAccountService {

  private static final String USER_ID_CACHE = "UserId";

  private static final String NO_ACCOUNT = "NO_ACCOUNT";
  private static final String NO_MFA = "NO_MFA";
  private static final Integer MAX_LOGIN_EVENTS = 10;

  private final CognitoIdentityProviderClient cognitoClient;
  private final String userPoolId;
  private final Cache cache;

  private final EventPublishService eventPublishService;

  private Instant lastUserCaching = null;

  UserAccountService(CognitoIdentityProviderClient cognitoClient,
      @Value("${application.aws.cognito.user-pool-id}") String userPoolId,
      CacheManager cacheManager, EventPublishService eventPublishService) {
    this.cognitoClient = cognitoClient;
    this.userPoolId = userPoolId;
    cache = cacheManager.getCache(USER_ID_CACHE);
    this.eventPublishService = eventPublishService;
  }

  /**
   * Get the user account details for the account associated with the given username.
   *
   * @param username The username for the account.
   * @return The user account details.
   */
  public UserAccountDetailsDto getUserAccountDetails(String username) {
    log.info("Retrieving user with username '{}'.", username);
    AdminGetUserRequest request = AdminGetUserRequest.builder()
        .userPoolId(userPoolId)
        .username(username)
        .build();

    UserAccountDetailsDto userAccountDetails;

    try {
      AdminGetUserResponse response = cognitoClient.adminGetUser(request);
      String preferredMfa = response.preferredMfaSetting();

      String mfaStatus = preferredMfa == null ? NO_MFA : preferredMfa;
      Instant createdAt = null;
      if (response.userCreateDate() != null) {
        createdAt = response.userCreateDate();
      }
      userAccountDetails = new UserAccountDetailsDto(mfaStatus, response.userStatusAsString(),
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
      AdminGetUserRequest request = AdminGetUserRequest.builder()
          .userPoolId(userPoolId)
          .username(newEmail)
          .build();
      AdminGetUserResponse response = cognitoClient.adminGetUser(request);
      String existingUserId = response.username();

      if (existingUserId.equals(userId)) {
        log.info("The email for this user has not changed, skipping update.");
      } else {
        String message = String.format("The email '%s' is already in use by user '%s'.", newEmail,
            existingUserId);
        throw new IllegalArgumentException(message);
      }
    } catch (UserNotFoundException e) {
      // If an existing user was not found then the new email address can be used.
      AdminGetUserRequest getRequest = AdminGetUserRequest.builder()
          .userPoolId(userPoolId)
          .username(userId)
          .build();
      AdminGetUserResponse existingDetails = cognitoClient.adminGetUser(getRequest);
      final Optional<String> existingEmail = existingDetails.userAttributes().stream()
          .filter(ua -> ua.name().equals("email"))
          .map(AttributeType::value)
          .findFirst();
      final Optional<String> traineeId = existingDetails.userAttributes().stream()
          .filter(ua -> ua.name().equals("custom:tisId"))
          .map(AttributeType::value)
          .findFirst();

      AdminUpdateUserAttributesRequest updateRequest = AdminUpdateUserAttributesRequest.builder()
          .userPoolId(userPoolId)
          .username(userId)
          .userAttributes(List.of(
              AttributeType.builder().name("email").value(newEmail).build(),
              AttributeType.builder().name("email_verified").value("true").build()
          ))
          .build();

      cognitoClient.adminUpdateUserAttributes(updateRequest);
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
    AdminListUserAuthEventsRequest request = AdminListUserAuthEventsRequest.builder()
        .userPoolId(userPoolId)
        .username(username)
        .maxResults(MAX_LOGIN_EVENTS) //results are sorted in descending CreationDate order
        .build();

    List<UserLoginDetailsDto> userLoginDetailsList;

    try {
      AdminListUserAuthEventsResponse response = cognitoClient.adminListUserAuthEvents(request);
      userLoginDetailsList = getLoginDetailsListFromAuthEvents(response);

    } catch (UserNotFoundException e) {
      log.info("User '{}' not found.", username);
      return List.of();
    }

    return userLoginDetailsList;
  }

  /**
   * Retrieve the list of UserLoginDetailsDtos from the user auth events list.
   *
   * @param authEventsResponse the response of the auth events call.
   * @return the list of UserLoginDetailsDtos, or an empty list if no auth events exist.
   */
  private List<UserLoginDetailsDto> getLoginDetailsListFromAuthEvents(
      AdminListUserAuthEventsResponse authEventsResponse) {
    List<UserLoginDetailsDto> userLoginDetailsList = new ArrayList<>();
    List<AuthEventType> authEvents = authEventsResponse.authEvents();

    authEvents.forEach(authEvent -> {
      String eventId = authEvent.eventId();
      Date eventDate = Date.from(authEvent.creationDate());
      String event = authEvent.eventTypeAsString();
      String eventResponse = authEvent.eventResponseAsString();
      String device = authEvent.eventContextData().deviceName();
      String challenges = authEvent.challengeResponses().stream()
          .map(it -> it.challengeName() + ":" + it.challengeResponse())
          .collect(Collectors.joining(", "));

      UserLoginDetailsDto eventDto
          = new UserLoginDetailsDto(eventId, eventDate, event, eventResponse, challenges, device);
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
    AdminListGroupsForUserRequest request = AdminListGroupsForUserRequest.builder()
        .userPoolId(userPoolId)
        .username(username)
        .build();

    try {
      AdminListGroupsForUserResponse response = cognitoClient.adminListGroupsForUser(request);
      return response.groups().stream()
          .map(GroupType::groupName)
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
    AdminSetUserMfaPreferenceRequest request = AdminSetUserMfaPreferenceRequest.builder()
        .userPoolId(userPoolId)
        .username(username)
        .smsMfaSettings(smsMfa -> smsMfa.enabled(false))
        .softwareTokenMfaSettings(tokenMfa -> tokenMfa.enabled(false))
        .build();

    cognitoClient.adminSetUserMFAPreference(request);
    log.info("MFA reset for user '{}'.", username);
  }

  /**
   * Delete the account for the given user.
   *
   * @param username The username of the user.
   */
  public void deleteCognitoAccount(String username) {
    log.info("Deleting the Cognito account for user '{}'.", username);
    AdminDeleteUserRequest request = AdminDeleteUserRequest.builder()
        .userPoolId(userPoolId)
        .username(username)
        .build();

    cognitoClient.adminDeleteUser(request);
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
    AdminAddUserToGroupRequest request = AdminAddUserToGroupRequest.builder()
        .userPoolId(userPoolId)
        .groupName(groupName)
        .username(username)
        .build();

    cognitoClient.adminAddUserToGroup(request);
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
    AdminRemoveUserFromGroupRequest request = AdminRemoveUserFromGroupRequest.builder()
        .userPoolId(userPoolId)
        .groupName(groupName)
        .username(username)
        .build();

    cognitoClient.adminRemoveUserFromGroup(request);
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
      ListUsersRequest request = ListUsersRequest.builder()
          .userPoolId(userPoolId)
          .paginationToken(paginationToken)
          .build();

      try {
        ListUsersResponse response = cognitoClient.listUsers(request);
        cacheUserAccountIds(response);
        paginationToken = response.paginationToken();
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
   * Cache the user accounts ids for the given response.
   *
   * @param response The response of a ListUsersRequest.
   */
  private void cacheUserAccountIds(ListUsersResponse response) {
    response.users().stream()
        .map(UserType::attributes)
        .map(attributes -> attributes.stream()
            .collect(Collectors.toMap(AttributeType::name, AttributeType::value))
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
}
