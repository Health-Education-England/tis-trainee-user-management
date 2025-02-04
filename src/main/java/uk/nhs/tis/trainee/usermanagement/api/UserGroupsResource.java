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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.tis.trainee.usermanagement.service.UserAccountService;

/**
 * An API for interacting with user groups.
 */
@Slf4j
@RestController
@RequestMapping("/api/user-groups")
@XRayEnabled
public class UserGroupsResource {

  private final UserAccountService service;
  private final String betaParticipantGroupName;

  UserGroupsResource(UserAccountService service,
      @Value("${application.aws.cognito.beta-participant-group}") String betaParticipantGroupName) {
    this.service = service;
    this.betaParticipantGroupName = betaParticipantGroupName;
  }

  /**
   * Add the given user into Beta Participant group.
   *
   * @param username The username of the user.
   * @return 204 No Content, if successful.
   */
  @PostMapping("/beta-participants/enroll/{username}")
  ResponseEntity<Void> enrollBetaParticipantGroup(@PathVariable String username) {
    log.info("User '{}' enrollment to Beta Participant group requested.", username);
    service.enrollToUserGroup(username, betaParticipantGroupName);
    return ResponseEntity.noContent().build();
  }

  /**
   * Remove the given user from Beta Participant group.
   *
   * @param username The username of the user.
   * @return 204 No Content, if successful.
   */
  @PostMapping("/beta-participants/withdraw/{username}")
  ResponseEntity<Void> withdrawBetaParticipantGroup(@PathVariable String username) {
    log.info("User '{}' withdrawal from Beta Participant group requested.", username);
    service.withdrawFromUserGroup(username, betaParticipantGroupName);
    return ResponseEntity.noContent().build();
  }
}
