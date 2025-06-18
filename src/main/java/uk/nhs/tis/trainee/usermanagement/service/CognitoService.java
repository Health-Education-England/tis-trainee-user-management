/*
 * The MIT License (MIT)
 *
 * Copyright 2025 Crown Copyright (Health Education England)
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
import com.amazonaws.services.cognitoidp.model.AdminAddUserToGroupResult;
import com.amazonaws.services.cognitoidp.model.AdminDeleteUserRequest;
import com.amazonaws.services.cognitoidp.model.AdminDeleteUserResult;
import com.amazonaws.services.cognitoidp.model.AdminGetUserRequest;
import com.amazonaws.services.cognitoidp.model.AdminGetUserResult;
import com.amazonaws.services.cognitoidp.model.AdminListGroupsForUserRequest;
import com.amazonaws.services.cognitoidp.model.AdminListGroupsForUserResult;
import com.amazonaws.services.cognitoidp.model.AdminListUserAuthEventsRequest;
import com.amazonaws.services.cognitoidp.model.AdminListUserAuthEventsResult;
import com.amazonaws.services.cognitoidp.model.AdminRemoveUserFromGroupRequest;
import com.amazonaws.services.cognitoidp.model.AdminRemoveUserFromGroupResult;
import com.amazonaws.services.cognitoidp.model.AdminSetUserMFAPreferenceRequest;
import com.amazonaws.services.cognitoidp.model.AdminSetUserMFAPreferenceResult;
import com.amazonaws.services.cognitoidp.model.AdminUpdateUserAttributesRequest;
import com.amazonaws.services.cognitoidp.model.AttributeType;
import com.amazonaws.services.cognitoidp.model.GroupType;
import com.amazonaws.services.cognitoidp.model.ListUsersRequest;
import com.amazonaws.services.cognitoidp.model.ListUsersResult;
import com.amazonaws.services.cognitoidp.model.UserNotFoundException;
import com.amazonaws.services.cognitoidp.model.UserType;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.nhs.tis.trainee.usermanagement.dto.UserAccountDetailsDto;
import uk.nhs.tis.trainee.usermanagement.enumeration.MfaType;
import uk.nhs.tis.trainee.usermanagement.mapper.UserAccountDetailsMapper;

/**
 * A wrapper around common Cognito endpoints adding additional functionality and caching.
 */
@Slf4j
@Service
public class CognitoService {

  private static final String ATTRIBUTE_EMAIL = "email";
  private static final String ATTRIBUTE_MFA_TYPE = "custom:mfaType";
  private static final String ATTRIBUTE_SUB = "sub";

  private final AWSCognitoIdentityProvider cognitoIdp;
  private final String userPoolId;
  private final UserAccountDetailsMapper mapper;

  /**
   * Construct an instance of the CognitoService.
   *
   * @param cognitoIdp The AWSCognitoIdentityProvider to use.
   * @param userPoolId The user pool to connect to.
   * @param mapper     A user details mapper.
   */
  public CognitoService(AWSCognitoIdentityProvider cognitoIdp,
      @Value("${application.aws.cognito.user-pool-id}") String userPoolId,
      UserAccountDetailsMapper mapper) {
    this.cognitoIdp = cognitoIdp;
    this.userPoolId = userPoolId;
    this.mapper = mapper;
  }

  /**
   * A wrapper around {@link AWSCognitoIdentityProvider#adminGetUser(AdminGetUserRequest)} which
   * attempts to avoid increasing Monthly Active User (MAU) count with alternative endpoints. If all
   * data is not available via alternative endpoints, then {@code adminGetUser()} will still be used
   * as a fallback.
   *
   * <p><b>Warning</b>: this will contribute to monthly active user (MAU) count for the purposes of
   * billing if the fallback is used.
   *
   * @return The user account details, or empty if not found.
   */
  public UserAccountDetailsDto getUserDetails(String username) throws UserNotFoundException {
    log.info("Getting user details for username {}.", username);
    UserType user = getUser(username);
    List<String> groups = getUserGroups(username);

    // Exclude NO_MFA for now, there is no guarantee that the attribute is set when MFA is set up.
    boolean mfaTypeAvailable = user.getAttributes().stream()
        .filter(ua -> ua.getName().equals(ATTRIBUTE_MFA_TYPE))
        .map(AttributeType::getValue)
        .map(MfaType::valueOf)
        .anyMatch(mfa -> mfa != MfaType.NO_MFA);

    if (mfaTypeAvailable) {
      return mapper.toDto(user, groups);
    } else {
      log.info("MFA details not available via attributes, calling AdminGetUser endpoint.");
      AdminGetUserResult result = getUserFallback(username);

      // Populate the custom:mfaType attribute for easier retrieval next time.
      MfaType defaultMfaType = MfaType.fromAdminGetUserResult(result);

      updateAttributes(username, List.of(
          new AttributeType().withName(ATTRIBUTE_MFA_TYPE).withValue(defaultMfaType.toString())
      ));

      return mapper.toDto(result, groups);
    }
  }

  /**
   * Get a {@link UserType} for the given username.
   *
   * @param username The username to search for, should be an email or sub.
   * @return The user matching the username.
   * @throws UserNotFoundException If no users were found for the given username.
   */
  private UserType getUser(String username) {
    String attribute = username.contains("@") ? ATTRIBUTE_EMAIL : ATTRIBUTE_SUB;
    ListUsersRequest request = new ListUsersRequest()
        .withUserPoolId(userPoolId)
        .withFilter(String.format("%s=\"%s\"", attribute, username));

    ListUsersResult result = cognitoIdp.listUsers(request);
    List<UserType> users = result.getUsers();

    if (users.isEmpty()) {
      String message = String.format("User not found in user pool '%s' with the username '%s'.",
          userPoolId, username);
      throw new UserNotFoundException(message);
    }

    return users.get(0);
  }

  /**
   * Get a user using the {@link AWSCognitoIdentityProvider#adminGetUser(AdminGetUserRequest)}.
   *
   * <p><b>Warning</b>: this will contribute to monthly active user (MAU) count for the purposes of
   * billing.
   *
   * @return The {@link AdminGetUserResult}.
   */
  private AdminGetUserResult getUserFallback(String username) throws UserNotFoundException {
    AdminGetUserRequest request = new AdminGetUserRequest()
        .withUserPoolId(userPoolId)
        .withUsername(username);
    return cognitoIdp.adminGetUser(request);
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
   * Update the user attributes on Cognito for the given user account.
   *
   * @param userId         The ID of the user account.
   * @param attributeTypes The attributes to update.
   */
  public void updateAttributes(String userId, List<AttributeType> attributeTypes) {
    AdminUpdateUserAttributesRequest updateRequest = new AdminUpdateUserAttributesRequest();
    updateRequest.setUserPoolId(userPoolId);
    updateRequest.setUsername(userId);

    updateRequest.setUserAttributes(attributeTypes);
    cognitoIdp.adminUpdateUserAttributes(updateRequest);

    String attributes = attributeTypes.stream()
        .map(AttributeType::getName)
        .collect(Collectors.joining(", "));
    log.info("Attributes updated for user '{}'. Updated [{}]", userId, attributes);
  }

  /**
   * @see AWSCognitoIdentityProvider#adminAddUserToGroup(AdminAddUserToGroupRequest)
   */
  public AdminAddUserToGroupResult adminAddUserToGroup(AdminAddUserToGroupRequest request) {
    return cognitoIdp.adminAddUserToGroup(request);
  }

  /**
   * @see AWSCognitoIdentityProvider#adminDeleteUser(AdminDeleteUserRequest)
   */
  public AdminDeleteUserResult adminDeleteUser(AdminDeleteUserRequest request) {
    return cognitoIdp.adminDeleteUser(request);
  }

  /**
   * @see AWSCognitoIdentityProvider#adminListUserAuthEvents(AdminListUserAuthEventsRequest)
   */
  public AdminListUserAuthEventsResult adminListUserAuthEvents(
      AdminListUserAuthEventsRequest request) {
    return cognitoIdp.adminListUserAuthEvents(request);
  }

  /**
   * @see AWSCognitoIdentityProvider#adminRemoveUserFromGroup(AdminRemoveUserFromGroupRequest)
   */
  public AdminRemoveUserFromGroupResult adminRemoveUserFromGroup(
      AdminRemoveUserFromGroupRequest request) {
    return cognitoIdp.adminRemoveUserFromGroup(request);
  }

  /**
   * @see AWSCognitoIdentityProvider#adminSetUserMFAPreference(AdminSetUserMFAPreferenceRequest)
   */
  public AdminSetUserMFAPreferenceResult adminSetUserMfaPreference(
      AdminSetUserMFAPreferenceRequest request) {
    return cognitoIdp.adminSetUserMFAPreference(request);
  }

  /**
   * @see AWSCognitoIdentityProvider#listUsers(ListUsersRequest)
   */
  public ListUsersResult listUsers(ListUsersRequest request) {
    return cognitoIdp.listUsers(request);
  }
}
