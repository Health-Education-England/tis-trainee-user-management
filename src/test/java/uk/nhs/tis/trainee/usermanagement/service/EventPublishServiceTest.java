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

package uk.nhs.tis.trainee.usermanagement.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.awspring.cloud.messaging.core.QueueMessagingTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.nhs.tis.trainee.usermanagement.event.DataRequestEvent;

class EventPublishServiceTest {

  private static final String QUEUE_URL = "queue.url";
  private static final String TRAINEE_ID = "11111";
  private EventPublishService eventPublishService;
  private QueueMessagingTemplate messagingTemplate;

  @BeforeEach
  void setUp() {
    messagingTemplate = mock(QueueMessagingTemplate.class);
    eventPublishService = new EventPublishService(messagingTemplate, QUEUE_URL);
  }

  @Test
  void shouldPublishProfileSyncEvent() {

    eventPublishService.publishSingleProfileSyncEvent(TRAINEE_ID);

    ArgumentCaptor<DataRequestEvent> eventCaptor = ArgumentCaptor.forClass(
        DataRequestEvent.class);
    verify(messagingTemplate).convertAndSend(eq(QUEUE_URL), eventCaptor.capture());

    assertThat("Unexpected table.", eventCaptor.getValue().getTable(), is("Person"));
    assertThat("Unexpected trainee ID.", eventCaptor.getValue().getId(), is(TRAINEE_ID));
  }
}
