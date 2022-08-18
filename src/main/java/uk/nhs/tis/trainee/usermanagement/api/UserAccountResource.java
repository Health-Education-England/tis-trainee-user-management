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
import com.amazonaws.services.cognitoidp.model.AdminGetUserRequest;
import com.amazonaws.services.cognitoidp.model.AdminGetUserResult;
import com.amazonaws.services.cognitoidp.model.UserNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.tis.trainee.usermanagement.dto.UserAccountDetailsDto;

/**
 * An API for interacting with user accounts.
 */
@Slf4j
@RestController
@RequestMapping("/api/user-account")
public class UserAccountResource {

  private static final String NOT_FOUND_USER_STATUS = "NO ACCOUNT";

  @Value("${application.aws.cognito.user-pool-id}")
  private String userPoolId;

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
    log.info("Account details requested for user '{}'", username);
    AdminGetUserRequest request = new AdminGetUserRequest();
    request.setUserPoolId(userPoolId);
    request.setUsername(username);

    String userStatus;

    try {
      AdminGetUserResult result = cognitoIdp.adminGetUser(request);
      userStatus = result.getUserStatus();
    } catch (UserNotFoundException e) {
      log.info("User '{}' not found.", username);
      userStatus = NOT_FOUND_USER_STATUS;
    }

    UserAccountDetailsDto response = new UserAccountDetailsDto(userStatus);
    return ResponseEntity.ok(response);
  }
}
