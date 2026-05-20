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

package uk.nhs.tis.trainee.usermanagement.mapper;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import uk.nhs.tis.trainee.usermanagement.dto.EmailUpdateEventDto;
import uk.nhs.tis.trainee.usermanagement.model.AccountEvent;
import uk.nhs.tis.trainee.usermanagement.model.AccountEvent.EmailUpdatedDetail;
import uk.nhs.tis.trainee.usermanagement.model.AccountEventType;

class AccountEventMapperTest {

  private final AccountEventMapper mapper = new AccountEventMapperImpl();

  @Test
  void shouldMapTraineeId() {
    AccountEvent event = AccountEvent.builder().traineeId("trainee-1").build();

    EmailUpdateEventDto dto = mapper.toEmailUpdateEventDto(event);

    assertThat("Unexpected traineeId.", dto.getTraineeId(), is("trainee-1"));
  }

  @Test
  void shouldMapCreated() {
    Instant created = Instant.parse("2026-01-15T10:00:00Z");
    AccountEvent event = AccountEvent.builder().created(created).build();

    EmailUpdateEventDto dto = mapper.toEmailUpdateEventDto(event);

    assertThat("Unexpected created.", dto.getCreated(), is(created));
  }

  @Test
  void shouldMapPreviousEmailFromDetail() {
    EmailUpdatedDetail detail = EmailUpdatedDetail.builder()
        .before("old@example.com")
        .after("new@example.com")
        .build();
    AccountEvent event = AccountEvent.builder()
        .type(AccountEventType.EMAIL_UPDATED)
        .detail(detail)
        .build();

    EmailUpdateEventDto dto = mapper.toEmailUpdateEventDto(event);

    assertThat("Unexpected previousEmail.", dto.getPreviousEmail(), is("old@example.com"));
  }

  @Test
  void shouldMapNewEmailFromDetail() {
    EmailUpdatedDetail detail = EmailUpdatedDetail.builder()
        .before("old@example.com")
        .after("new@example.com")
        .build();
    AccountEvent event = AccountEvent.builder()
        .type(AccountEventType.EMAIL_UPDATED)
        .detail(detail)
        .build();

    EmailUpdateEventDto dto = mapper.toEmailUpdateEventDto(event);

    assertThat("Unexpected newEmail.", dto.getNewEmail(), is("new@example.com"));
  }

  @Test
  void shouldMapNullEmailsWhenDetailIsNull() {
    AccountEvent event = AccountEvent.builder().detail(null).build();

    EmailUpdateEventDto dto = mapper.toEmailUpdateEventDto(event);

    assertThat("Unexpected previousEmail.", dto.getPreviousEmail(), nullValue());
    assertThat("Unexpected newEmail.", dto.getNewEmail(), nullValue());
  }
}

