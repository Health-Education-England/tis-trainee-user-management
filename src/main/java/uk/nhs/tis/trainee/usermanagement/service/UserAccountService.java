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
import com.amazonaws.services.cognitoidp.model.AuthEventType;
import com.amazonaws.services.cognitoidp.model.GroupType;
import com.amazonaws.services.cognitoidp.model.SMSMfaSettingsType;
import com.amazonaws.services.cognitoidp.model.SoftwareTokenMfaSettingsType;
import com.amazonaws.services.cognitoidp.model.UserNotFoundException;
import com.amazonaws.xray.spring.aop.XRayEnabled;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.nhs.tis.trainee.usermanagement.dto.UserAccountDetailsDto;
import uk.nhs.tis.trainee.usermanagement.dto.UserLoginDetailsDto;

@Slf4j
@Service
@XRayEnabled
public class UserAccountService {

  private static final String NO_ACCOUNT = "NO_ACCOUNT";
  private static final String NO_MFA = "NO_MFA";
  private static final Integer MAX_LOGIN_EVENTS = 10;

  private final AWSCognitoIdentityProvider cognitoIdp;
  private final String userPoolId;

  UserAccountService(AWSCognitoIdentityProvider cognitoIdp,
      @Value("${application.aws.cognito.user-pool-id}") String userPoolId) {
    this.cognitoIdp = cognitoIdp;
    this.userPoolId = userPoolId;
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
}
