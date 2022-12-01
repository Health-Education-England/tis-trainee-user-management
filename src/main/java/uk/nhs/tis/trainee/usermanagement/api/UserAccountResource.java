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

package uk.nhs.tis.trainee.usermanagement.api;

import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.model.AdminAddUserToGroupRequest;
import com.amazonaws.services.cognitoidp.model.AdminDeleteUserRequest;
import com.amazonaws.services.cognitoidp.model.AdminGetUserRequest;
import com.amazonaws.services.cognitoidp.model.AdminGetUserResult;
import com.amazonaws.services.cognitoidp.model.AdminRemoveUserFromGroupRequest;
import com.amazonaws.services.cognitoidp.model.AdminSetUserMFAPreferenceRequest;
import com.amazonaws.services.cognitoidp.model.SMSMfaSettingsType;
import com.amazonaws.services.cognitoidp.model.SoftwareTokenMfaSettingsType;
import com.amazonaws.services.cognitoidp.model.UserNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.tis.trainee.usermanagement.dto.UserAccountDetailsDto;

/**
 * An API for interacting with user accounts.
 */
@Slf4j
@RestController
@RequestMapping("/api/user-account")
public class UserAccountResource {

  private static final String NO_ACCOUNT = "NO_ACCOUNT";
  private static final String NO_MFA = "NO_MFA";
  private static final String DSP_CONSULTATION = "dsp-consultation";

  @Value("${application.aws.cognito.user-pool-id}")
  private String userPoolId;

  @Value("${application.aws.cognito.consultation-group}")
  private String consultationGroupName;

  private final AWSCognitoIdentityProvider cognitoIdp;

  UserAccountResource(AWSCognitoIdentityProvider cognitoIdp) {
    this.cognitoIdp = cognitoIdp;
  }

  /**
   * Get the user account details for the account associated with the given username.
   *
   * @param username The username for the account.
   * @return The user account details.
   */
  @GetMapping("/details/{username}")
  ResponseEntity<UserAccountDetailsDto> getUserAccountDetails(@PathVariable String username) {
    log.info("Account details requested for user '{}'.", username);
    AdminGetUserRequest request = new AdminGetUserRequest();
    request.setUserPoolId(userPoolId);
    request.setUsername(username);

    String mfaStatus;
    String userStatus;

    try {
      AdminGetUserResult result = cognitoIdp.adminGetUser(request);
      String preferredMfa = result.getPreferredMfaSetting();

      mfaStatus = preferredMfa == null ? NO_MFA : preferredMfa;
      userStatus = result.getUserStatus();
    } catch (UserNotFoundException e) {
      log.info("User '{}' not found.", username);
      mfaStatus = NO_ACCOUNT;
      userStatus = NO_ACCOUNT;
    }

    UserAccountDetailsDto response = new UserAccountDetailsDto(mfaStatus, userStatus);
    return ResponseEntity.ok(response);
  }

  /**
   * Reset the MFA for the given user.
   *
   * @param username The username of the user.
   * @return 204 No Content, if successful.
   */
  @PostMapping("/reset-mfa/{username}")
  ResponseEntity<Void> resetUserAccountMfa(@PathVariable String username) {
    log.info("MFA reset requested for user '{}'.", username);
    AdminSetUserMFAPreferenceRequest request = new AdminSetUserMFAPreferenceRequest();
    request.setUserPoolId(userPoolId);
    request.setUsername(username);
    request.setSMSMfaSettings(new SMSMfaSettingsType().withEnabled(false));
    request.setSoftwareTokenMfaSettings(new SoftwareTokenMfaSettingsType().withEnabled(false));

    cognitoIdp.adminSetUserMFAPreference(request);
    log.info("MFA reset for user '{}'.", username);
    return ResponseEntity.noContent().build();
  }

  /**
   * Delete TSS Cognito account for the given user.
   *
   * @param username The username of the user.
   * @return 204 No Content, if successful.
   */
  @DeleteMapping("/{username}")
  ResponseEntity<Void> deleteCognitoAccount(@PathVariable String username) {
    log.info("Delete Cognito account requested for user '{}'.", username);
    AdminDeleteUserRequest request = new AdminDeleteUserRequest();
    request.setUserPoolId(userPoolId);
    request.setUsername(username);

    cognitoIdp.adminDeleteUser(request);
    log.info("Deleted Cognito account for user '{}'.", username);
    return ResponseEntity.noContent().build();
  }

  /**
   * Add the given user into Cognito user group.
   *
   * @param usergroup The name of the Cognito user group.
   * @param username The username of the user.
   * @return 204 No Content, if successful.
   */
  @PostMapping("/add-usergroup")
  ResponseEntity<Void> addToUserGroup(
      @RequestParam(value = "usergroup", required = true) String usergroup,
      @RequestParam(value = "username", required = true) String username
  ) {
    String groupName;
    log.info("User '{}' enrolment to user group '{}' requested.", username, usergroup);
    if (usergroup.equals(DSP_CONSULTATION)) {
      groupName = consultationGroupName;
    } else {
      log.warn("Invalid user group: '{}'", usergroup);
      return ResponseEntity.badRequest().build();
    }

    AdminAddUserToGroupRequest request = new AdminAddUserToGroupRequest();
    request.setUserPoolId(userPoolId);
    request.setGroupName(groupName);
    request.setUsername(username);

    cognitoIdp.adminAddUserToGroup(request);
    log.info("User '{}' is enroled in user group name '{}'.", username, groupName);
    return ResponseEntity.noContent().build();
  }

  /**
   * Remove the given user from Cognito user group.
   *
   * @param usergroup The name of the Cognito user group.
   * @param username The username of the user.
   * @return 204 No Content, if successful.
   */
  @PostMapping("/remove-usergroup")
  ResponseEntity<Void> removeFromUserGroup(
      @RequestParam(value = "usergroup", required = true) String usergroup,
      @RequestParam(value = "username", required = true) String username
  ) {
    String groupName;
    log.info("User '{}' withdraw from user group '{}' requested.", username, usergroup);
    if (usergroup.equals(DSP_CONSULTATION)) {
      groupName = consultationGroupName;
    } else {
      log.warn("Invalid user group: '{}'", usergroup);
      return ResponseEntity.badRequest().build();
    }

    AdminRemoveUserFromGroupRequest request = new AdminRemoveUserFromGroupRequest();
    request.setUserPoolId(userPoolId);
    request.setGroupName(groupName);
    request.setUsername(username);

    cognitoIdp.adminRemoveUserFromGroup(request);
    log.info("User '{}' is withdrawn from user group name '{}'.", username, groupName);
    return ResponseEntity.noContent().build();
  }
}
