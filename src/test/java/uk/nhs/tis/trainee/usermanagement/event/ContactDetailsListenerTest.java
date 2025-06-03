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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.nhs.tis.trainee.usermanagement.service.UserAccountService;

class ContactDetailsListenerTest {

  private static final String ACCOUNT_ID = UUID.randomUUID().toString();
  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final String EMAIL = "unit.test@example.com";
  private static final String FORENAMES = "Fore Name";
  private static final String SURNAME = "Surname";

  private ContactDetailsListener listener;
  private UserAccountService service;

  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    service = mock(UserAccountService.class);
    listener = new ContactDetailsListener(service);
    mapper = JsonMapper.builder()
        .findAndAddModules()
        .build();
  }

  @Test
  void shouldNotUpdateUsernameWhenNoAccountFoundForUpdate() throws JsonProcessingException {
    String eventJson = """
        {
          "record": {
            "data": {
              "id": "%s",
              "email": "%s"
            }
          }
        }""".formatted(TRAINEE_ID, EMAIL);
    ContactDetailsEvent event = mapper.readValue(eventJson, ContactDetailsEvent.class);

    when(service.getUserAccountIds(TRAINEE_ID)).thenReturn(Set.of());

    listener.handleContactDetailsUpdate(event);

    verify(service, never()).updateContactDetails(any(), any(), any(), any());
  }

  @Test
  void shouldThrowExceptionWhenMultipleAccountFoundForUpdate() throws JsonProcessingException {
    String eventJson = """
        {
          "record": {
            "data": {
              "id": "%s",
              "email": "%s"
            }
          }
        }""".formatted(TRAINEE_ID, EMAIL);
    ContactDetailsEvent event = mapper.readValue(eventJson, ContactDetailsEvent.class);

    when(service.getUserAccountIds(TRAINEE_ID)).thenReturn(Set.of(ACCOUNT_ID, "123"));

    assertThrows(IllegalArgumentException.class, () -> listener.handleContactDetailsUpdate(event));

    verify(service, never()).updateContactDetails(any(), any(), any(), any());
  }

  @Test
  void shouldUpdateUsernameWhenSingleAccountFoundForUpdate() throws JsonProcessingException {
    String eventJson = """
        {
          "record": {
            "data": {
              "id": "%s",
              "email": "%s",
              "forenames": "%s",
              "surname": "%s"
            }
          }
        }""".formatted(TRAINEE_ID, EMAIL, FORENAMES, SURNAME);
    ContactDetailsEvent event = mapper.readValue(eventJson, ContactDetailsEvent.class);

    when(service.getUserAccountIds(TRAINEE_ID)).thenReturn(Set.of(ACCOUNT_ID));

    listener.handleContactDetailsUpdate(event);

    verify(service).updateContactDetails(ACCOUNT_ID, EMAIL, FORENAMES, SURNAME);
  }
}
