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

package uk.nhs.tis.trainee.usermanagement.event;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.nhs.tis.trainee.usermanagement.dto.CognitoEventDto;
import uk.nhs.tis.trainee.usermanagement.dto.CognitoEventDto.AdditionalEventData;
import uk.nhs.tis.trainee.usermanagement.dto.EventBridgeEventDto;
import uk.nhs.tis.trainee.usermanagement.service.UserAccountService;

class CognitoEventListenerTest {

  private static final String USER_ID = UUID.randomUUID().toString();
  private static final String EVENT_NAME = "AdminUpdateUserAttributes";

  private CognitoEventListener listener;
  private UserAccountService userAccountService;

  @BeforeEach
  void setUp() {
    userAccountService = mock(UserAccountService.class);
    listener = new CognitoEventListener(userAccountService);
  }

  @Test
  void shouldHandleCognitoEventFromEventBridge() {
    AdditionalEventData additionalEventData = new AdditionalEventData(USER_ID);
    CognitoEventDto cognitoEvent = new CognitoEventDto(EVENT_NAME, Instant.now(),
        additionalEventData);

    EventBridgeEventDto<CognitoEventDto> eventBridgeEvent = new EventBridgeEventDto<>(
        UUID.randomUUID(), cognitoEvent);

    listener.handleCognitoEvent(eventBridgeEvent);

    ArgumentCaptor<CognitoEventDto> eventCaptor = ArgumentCaptor.captor();
    verify(userAccountService).updateAccountDetails(eventCaptor.capture());

    CognitoEventDto capturedEvent = eventCaptor.getValue();
    assertThat("Unexpected event name.", capturedEvent, is(cognitoEvent));
  }
}

