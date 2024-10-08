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

import static io.awspring.cloud.messaging.core.TopicMessageChannel.MESSAGE_GROUP_ID_HEADER;
import static io.awspring.cloud.messaging.core.TopicMessageChannel.NOTIFICATION_SUBJECT_HEADER;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.awspring.cloud.messaging.core.NotificationMessagingTemplate;
import io.awspring.cloud.messaging.core.QueueMessagingTemplate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.nhs.tis.trainee.usermanagement.event.DataRequestEvent;
import uk.nhs.tis.trainee.usermanagement.event.EmailUpdateEvent;

class EventPublishServiceTest {

  private static final String REQUEST_QUEUE_URL = "request.queue.url";
  private static final String USER_ACCOUNT_UPDATE_TOPIC = "user-account.update.topic.arn";
  private static final String TRAINEE_ID = "11111";
  private static final String USER_ID = UUID.randomUUID().toString();
  private EventPublishService eventPublishService;
  private MetricsService metricsService;
  private NotificationMessagingTemplate notificationMessagingTemplate;
  private QueueMessagingTemplate queueMessagingTemplate;

  @BeforeEach
  void setUp() {
    metricsService = mock(MetricsService.class);
    notificationMessagingTemplate = mock(NotificationMessagingTemplate.class);
    queueMessagingTemplate = mock(QueueMessagingTemplate.class);
    eventPublishService = new EventPublishService(notificationMessagingTemplate,
        USER_ACCOUNT_UPDATE_TOPIC, queueMessagingTemplate, REQUEST_QUEUE_URL, metricsService);
  }

  @Test
  void shouldPublishProfileSyncEvent() {

    eventPublishService.publishSingleProfileSyncEvent(TRAINEE_ID);

    ArgumentCaptor<DataRequestEvent> eventCaptor = ArgumentCaptor.forClass(
        DataRequestEvent.class);
    ArgumentCaptor<Map<String, Object>> headerCaptor = ArgumentCaptor.forClass(Map.class);
    verify(queueMessagingTemplate).convertAndSend(eq(REQUEST_QUEUE_URL), eventCaptor.capture(),
        headerCaptor.capture());

    assertThat("Unexpected table.", eventCaptor.getValue().getTable(), is("Person"));
    assertThat("Unexpected trainee ID.", eventCaptor.getValue().getId(), is(TRAINEE_ID));

    Map<String, Object> headers = headerCaptor.getValue();
    assertThat("Unexpected headers size.", headers.size(), is(1));
    String expectedMessageGroupId = String.format("%s_%s_%s", EventPublishService.REQUEST_SCHEMA,
        EventPublishService.REQUEST_TABLE, TRAINEE_ID);
    assertThat("Unexpected header.", headers.get("message-group-id"),
        is(expectedMessageGroupId));
    verify(metricsService).incrementResyncCounter();
  }

  @Test
  void shouldPublishEmailUpdateEvent() {
    String previousEmail = "previous.email@example.com";
    String newEmail = "new.email@example.com";

    eventPublishService.publishEmailUpdateEvent(USER_ID, TRAINEE_ID, previousEmail, newEmail);

    ArgumentCaptor<EmailUpdateEvent> eventCaptor = ArgumentCaptor.forClass(EmailUpdateEvent.class);
    ArgumentCaptor<Map<String, Object>> headersCaptor = ArgumentCaptor.forClass(Map.class);
    verify(notificationMessagingTemplate).convertAndSend(eq(USER_ACCOUNT_UPDATE_TOPIC),
        eventCaptor.capture(), headersCaptor.capture());

    EmailUpdateEvent event = eventCaptor.getValue();
    assertThat("Unexpected user ID.", event.userId(), is(USER_ID));
    assertThat("Unexpected trainee ID.", event.traineeId(), is(TRAINEE_ID));
    assertThat("Unexpected previous email.", event.previousEmail(), is(previousEmail));
    assertThat("Unexpected new email.", event.newEmail(), is(newEmail));

    Map<String, Object> headers = headersCaptor.getValue();
    assertThat("Unexpected header count.", headers.size(), is(3));
    assertThat("Unexpected subject.", headers.get(NOTIFICATION_SUBJECT_HEADER),
        is("Account Email Updated"));
    assertThat("Unexpected group ID.", headers.get(MESSAGE_GROUP_ID_HEADER),
        is(USER_ID));
    assertThat("Unexpected producer.", headers.get("producer"), is("tis-trainee-user-management"));
    verifyNoInteractions(metricsService);
  }
}
