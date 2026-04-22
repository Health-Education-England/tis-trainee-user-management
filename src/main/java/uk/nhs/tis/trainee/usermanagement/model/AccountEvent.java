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

package uk.nhs.tis.trainee.usermanagement.model;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.With;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Represents an event that occurred to a user account, such as email update or password reset.
 *
 * @param id        The unique identifier of the event.
 * @param userId    The ID of the user whose account was affected.
 * @param traineeId The ID of the trainee associated with the user account.
 * @param type      The type of the event, e.g. EMAIL_UPDATED.
 * @param detail    Additional details about the event, such as the old and new email addresses for
 *                  an email update event.
 * @param created   The time the event occurred.
 */
@Document("AccountEvent")
@Builder
public record AccountEvent(
    @Id
    @With
    UUID id,

    @Indexed
    String userId,

    @Indexed
    String traineeId,

    AccountEventType type,
    AccountEventDetail detail,

    @CreatedDate
    Instant created) {

  /**
   * Marker interface for additional details about an account event.
   */
  public interface AccountEventDetail {

  }

  /**
   * Details for an email update event, containing the old and new email addresses.
   *
   * @param before The email address before the update.
   * @param after  The email address after the update.
   */
  @Builder
  public record EmailUpdatedDetail(String before, String after) implements AccountEventDetail {

  }
}
