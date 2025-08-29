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

import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListGroupsForUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListGroupsForUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListUserAuthEventsRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListUserAuthEventsResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRemoveUserFromGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRemoveUserFromGroupResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserMfaPreferenceRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserMfaPreferenceResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUpdateUserAttributesRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GroupType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;
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

  private final CognitoIdentityProviderClient cognitoClient;
  private final String userPoolId;
  private final UserAccountDetailsMapper mapper;

  /**
   * Construct an instance of the CognitoService.
   *
   * @param cognitoClient The CognitoIdentityProviderClient to use.
   * @param userPoolId    The user pool to connect to.
   * @param mapper        A user details mapper.
   */
  public CognitoService(CognitoIdentityProviderClient cognitoClient,
      @Value("${application.aws.cognito.user-pool-id}") String userPoolId,
      UserAccountDetailsMapper mapper) {
    this.cognitoClient = cognitoClient;
    this.userPoolId = userPoolId;
    this.mapper = mapper;
  }

  /**
   * A wrapper around {@link CognitoIdentityProviderClient#adminGetUser(AdminGetUserRequest)} which
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
    boolean mfaTypeAvailable = user.attributes().stream()
        .filter(ua -> ua.name().equals(ATTRIBUTE_MFA_TYPE))
        .map(AttributeType::value)
        .map(MfaType::valueOf)
        .anyMatch(mfa -> mfa != MfaType.NO_MFA);

    if (mfaTypeAvailable) {
      return mapper.toDto(user, groups);
    } else {
      log.info("MFA details not available via attributes, calling AdminGetUser endpoint.");
      AdminGetUserResponse response = getUserFallback(username);

      // Populate the custom:mfaType attribute for easier retrieval next time.
      MfaType defaultMfaType = MfaType.fromAdminGetUserResult(response);

      updateAttributes(username, List.of(
          AttributeType.builder().name(ATTRIBUTE_MFA_TYPE).value(defaultMfaType.toString()).build()
      ));

      return mapper.toDto(response, groups);
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
    ListUsersRequest request = ListUsersRequest.builder()
        .userPoolId(userPoolId)
        .filter(String.format("%s=\"%s\"", attribute, username))
        .build();

    ListUsersResponse response = cognitoClient.listUsers(request);
    List<UserType> users = response.users();

    if (users.isEmpty()) {
      String message = String.format("User not found in user pool '%s' with the username '%s'.",
          userPoolId, username);
      throw UserNotFoundException.builder().message(message).build();
    }

    return users.get(0);
  }

  /**
   * Get a user using the {@link CognitoIdentityProviderClient#adminGetUser(AdminGetUserRequest)}.
   *
   * <p><b>Warning</b>: this will contribute to monthly active user (MAU) count for the purposes of
   * billing.
   *
   * @return The {@link AdminGetUserResponse}.
   */
  private AdminGetUserResponse getUserFallback(String username) throws UserNotFoundException {
    AdminGetUserRequest request = AdminGetUserRequest.builder()
        .userPoolId(userPoolId)
        .username(username)
        .build();
    return cognitoClient.adminGetUser(request);
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
   * Update the user attributes on Cognito for the given user account.
   *
   * @param userId         The ID of the user account.
   * @param attributeTypes The attributes to update.
   */
  public void updateAttributes(String userId, List<AttributeType> attributeTypes) {
    AdminUpdateUserAttributesRequest updateRequest = AdminUpdateUserAttributesRequest.builder()
        .userPoolId(userPoolId)
        .username(userId)
        .userAttributes(attributeTypes)
        .build();
    cognitoClient.adminUpdateUserAttributes(updateRequest);

    String attributes = attributeTypes.stream()
        .map(AttributeType::name)
        .collect(Collectors.joining(", "));
    log.info("Attributes updated for user '{}'. Updated [{}]", userId, attributes);
  }

  /**
   * @see CognitoIdentityProviderClient#adminAddUserToGroup(AdminAddUserToGroupRequest)
   */
  public AdminAddUserToGroupResponse adminAddUserToGroup(AdminAddUserToGroupRequest request) {
    return cognitoClient.adminAddUserToGroup(request);
  }

  /**
   * @see CognitoIdentityProviderClient#adminDeleteUser(AdminDeleteUserRequest)
   */
  public AdminDeleteUserResponse adminDeleteUser(AdminDeleteUserRequest request) {
    return cognitoClient.adminDeleteUser(request);
  }

  /**
   * @see CognitoIdentityProviderClient#adminListUserAuthEvents(AdminListUserAuthEventsRequest)
   */
  public AdminListUserAuthEventsResponse adminListUserAuthEvents(
      AdminListUserAuthEventsRequest request) {
    return cognitoClient.adminListUserAuthEvents(request);
  }

  /**
   * @see CognitoIdentityProviderClient#adminRemoveUserFromGroup(AdminRemoveUserFromGroupRequest)
   */
  public AdminRemoveUserFromGroupResponse adminRemoveUserFromGroup(
      AdminRemoveUserFromGroupRequest request) {
    return cognitoClient.adminRemoveUserFromGroup(request);
  }

  /**
   * @see CognitoIdentityProviderClient#adminSetUserMFAPreference(AdminSetUserMfaPreferenceRequest)
   */
  public AdminSetUserMfaPreferenceResponse adminSetUserMfaPreference(
      AdminSetUserMfaPreferenceRequest request) {
    return cognitoClient.adminSetUserMFAPreference(request);
  }

  /**
   * @see CognitoIdentityProviderClient#listUsers(ListUsersRequest)
   */
  public ListUsersResponse listUsers(ListUsersRequest request) {
    return cognitoClient.listUsers(request);
  }
}
