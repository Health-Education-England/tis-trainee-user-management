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

import com.amazonaws.xray.spring.aop.XRayEnabled;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.tis.trainee.usermanagement.dto.UserAccountDetailsDto;
import uk.nhs.tis.trainee.usermanagement.dto.UserLoginDetailsDto;
import uk.nhs.tis.trainee.usermanagement.service.UserAccountService;

/**
 * An API for interacting with user accounts.
 */
@Slf4j
@RestController
@RequestMapping("/api/user-account")
@XRayEnabled
public class UserAccountResource {

  private final UserAccountService service;

  UserAccountResource(UserAccountService service) {
    this.service = service;
  }

  /**
   * Get whether a user account exists for the given username.
   *
   * @param username The username for the account.
   * @return Whether an account exists.
   */
  @GetMapping("/exists/{username}")
  ResponseEntity<Map<String, Boolean>> doesUserAccountExist(@PathVariable String username) {
    log.info("Account existence requested for user '{}'.", username);
    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(username);
    boolean exists = !userAccountDetails.getUserStatus().equals("NO_ACCOUNT");
    return ResponseEntity.ok(Map.of("exists", exists));
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
    return ResponseEntity.ok(service.getUserAccountDetails(username));
  }

  /**
   * Get the list of login events for the account associated with the given username.
   *
   * @param username The username for the account.
   * @return The list of login events.
   */
  @GetMapping("/logins/{username}")
  ResponseEntity<List<UserLoginDetailsDto>> getUserLoginDetails(@PathVariable String username) {
    log.info("Login details requested for user '{}'.", username);
    return ResponseEntity.ok(service.getUserLoginDetails(username));
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
    service.resetUserAccountMfa(username);
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
    service.deleteCognitoAccount(username);
    return ResponseEntity.noContent().build();
  }
}
