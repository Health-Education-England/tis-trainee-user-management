/*
 * The MIT License (MIT)
 *
 * Copyright 2024 Crown Copyright (Health Education England)
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

package uk.nhs.tis.trainee.usermanagement.event;

import static io.awspring.cloud.messaging.listener.SqsMessageDeletionPolicy.ON_SUCCESS;

import io.awspring.cloud.messaging.listener.annotation.SqsListener;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.nhs.tis.trainee.usermanagement.dto.ContactDetailsDto;
import uk.nhs.tis.trainee.usermanagement.service.UserAccountService;

@Slf4j
@Component
public class ContactDetailsListener {

  private final UserAccountService service;

  public ContactDetailsListener(UserAccountService service) {
    this.service = service;
  }

  /**
   * Handle contact details being updated.
   *
   * @param event The contact details update event.
   */
  @SqsListener(
      value = "${application.aws.sqs.contact-details.updated}", deletionPolicy = ON_SUCCESS)
  public void handleContactDetailsUpdate(ContactDetailsEvent event) {
    log.info("Received contact details update event '{}'.", event.getContactDetails());

    ContactDetailsDto dto = event.getContactDetails();
    String traineeId = dto.traineeId();
    Set<String> userAccountIds = service.getUserAccountIds(traineeId);

    switch (userAccountIds.size()) {
      case 0 -> log.info("No account exists for trainee {}, skipping username update.", traineeId);
      case 1 -> {
        String accountId = userAccountIds.iterator().next();
        service.updateEmail(accountId, dto.email());
      }
      default -> {
        String message = String.format("%s accounts found for trainee %s, unable to update email.",
            userAccountIds.size(), traineeId);
        throw new IllegalArgumentException(message);
      }
    }
  }
}
