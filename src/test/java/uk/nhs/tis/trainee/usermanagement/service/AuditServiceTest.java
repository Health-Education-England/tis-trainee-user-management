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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static uk.nhs.tis.trainee.usermanagement.model.AccountEventType.EMAIL_UPDATED;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.nhs.tis.trainee.usermanagement.model.AccountEvent;
import uk.nhs.tis.trainee.usermanagement.model.AccountEvent.EmailUpdatedDetail;
import uk.nhs.tis.trainee.usermanagement.repository.AccountEventRepository;

class AuditServiceTest {

  private static final String USER_ID = UUID.randomUUID().toString();
  private static final String TRAINEE_ID = "TraineeId123";
  private static final String EMAIL = "trainee@example.com";


  private AuditService service;

  private AccountEventRepository repository;

  @BeforeEach
  void setUp() {
    repository = mock(AccountEventRepository.class);
    service = new AuditService(repository);
  }

  @Test
  void shouldCreateEmailUpdatedAuditEvent() {
    service.accountEmailUpdated(USER_ID, TRAINEE_ID, EMAIL, "new.trainee@example.com");

    ArgumentCaptor<AccountEvent> eventCaptor = ArgumentCaptor.captor();
    verify(repository).insert(eventCaptor.capture());

    AccountEvent event = eventCaptor.getValue();
    assertThat("Unexpected event ID.", event.id(), nullValue());
    assertThat("Unexpected user ID.", event.userId(), is(USER_ID));
    assertThat("Unexpected trainee ID.", event.traineeId(), is(TRAINEE_ID));
    assertThat("Unexpected event type.", event.type(), is(EMAIL_UPDATED));
    assertThat("Unexpected event detail type.", event.detail(),
        instanceOf(EmailUpdatedDetail.class));
    assertThat("Unexpected event timestamp.", event.created(), nullValue());

    EmailUpdatedDetail eventDetail = (EmailUpdatedDetail) event.detail();
    assertThat("Unexpected old email.", eventDetail.before(), is(EMAIL));
    assertThat("Unexpected new email.", eventDetail.after(), is("new.trainee@example.com"));
  }

}
