/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
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

import static uk.nhs.tis.trainee.usermanagement.model.AccountEventType.EMAIL_UPDATED;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.nhs.tis.trainee.usermanagement.model.AccountEvent;
import uk.nhs.tis.trainee.usermanagement.model.AccountEvent.EmailUpdatedDetail;
import uk.nhs.tis.trainee.usermanagement.repository.AccountEventRepository;

/**
 * A service for recording audit events related to user accounts.
 */
@Slf4j
@Service
public class AuditService {

  private final AccountEventRepository repository;

  public AuditService(AccountEventRepository repository) {
    this.repository = repository;
  }

  /**
   * Records an email update event for a user account.
   *
   * @param userId    The ID of the user whose email was updated.
   * @param traineeId The ID of the trainee associated with the user account.
   * @param oldEmail  The old email address.
   * @param newEmail  The new email address.
   */
  public void accountEmailUpdated(String userId, String traineeId, String oldEmail,
      String newEmail) {
    log.debug("Recording email update for user {} with trainee ID {}.", userId, traineeId);
    EmailUpdatedDetail emailUpdatedDetail = new EmailUpdatedDetail(oldEmail, newEmail);
    AccountEvent event = AccountEvent.builder()
        .userId(userId)
        .traineeId(traineeId)
        .type(EMAIL_UPDATED)
        .detail(emailUpdatedDetail)
        .build();
    repository.insert(event);
  }
}
