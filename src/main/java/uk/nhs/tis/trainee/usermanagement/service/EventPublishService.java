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

import static io.awspring.cloud.sns.core.SnsHeaders.MESSAGE_GROUP_ID_HEADER;
import static io.awspring.cloud.sns.core.SnsHeaders.NOTIFICATION_SUBJECT_HEADER;

import com.amazonaws.xray.spring.aop.XRayEnabled;
import io.awspring.cloud.sns.core.SnsTemplate;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Service;
import uk.nhs.tis.trainee.usermanagement.event.AccountDeleteEvent;
import uk.nhs.tis.trainee.usermanagement.event.AccountUpdateEvent;
import uk.nhs.tis.trainee.usermanagement.event.DataRequestEvent;
import uk.nhs.tis.trainee.usermanagement.event.EmailUpdateEvent;
import uk.nhs.tis.trainee.usermanagement.event.ProfileMoveEvent;

/**
 * A service to publish events to SQS.
 */
@Slf4j
@Service
@XRayEnabled
public class EventPublishService {

  protected static final String REQUEST_SCHEMA = "tcs";
  protected static final String REQUEST_TABLE = "Person";
  protected static final String EVENT_TYPE_HEADER = "event_type";
  protected static final String PRODUCER_HEADER = "producer";
  protected static final String PRODUCER_VALUE = "tis-trainee-user-management";

  private final SnsTemplate notificationMessagingTemplate;
  private final SqsTemplate queueMessagingTemplate;
  private final String accountEventTopicArn;
  private final String profileMoveTopicArn;
  private final String userAccountUpdateTopicArn;
  private final String queueUrl;
  private final MetricsService metricsService;

  EventPublishService(SnsTemplate notificationMessagingTemplate,
      @Value("${application.aws.sns.account}") String accountEventTopicArn,
      @Value("${application.aws.sns.user-account.update}") String userAccountUpdateTopicArn,
      @Value("${application.aws.sns.profile-move.request}") String profileMoveTopicArn,
      SqsTemplate queueMessagingTemplate,
      @Value("${application.aws.sqs.request}") String requestQueueUrl,
      MetricsService metricsService) {
    this.notificationMessagingTemplate = notificationMessagingTemplate;
    this.accountEventTopicArn = accountEventTopicArn;
    this.userAccountUpdateTopicArn = userAccountUpdateTopicArn;
    this.profileMoveTopicArn = profileMoveTopicArn;
    this.queueMessagingTemplate = queueMessagingTemplate;
    this.queueUrl = requestQueueUrl;
    this.metricsService = metricsService;
  }

  /**
   * Publish a single profile sync event.
   *
   * @param traineeTisId The TisId of the trainee to sync.
   */
  public void publishSingleProfileSyncEvent(String traineeTisId) {
    log.info("Sending single profile sync event for trainee id '{}'", traineeTisId);

    DataRequestEvent dataRequestEvent = new DataRequestEvent(REQUEST_TABLE, traineeTisId);

    Map<String, Object> headers = new HashMap<>();
    String messageGroupId = REQUEST_SCHEMA + "_" + REQUEST_TABLE + "_" + traineeTisId;
    headers.put("message-group-id", messageGroupId);

    GenericMessage<DataRequestEvent> message = new GenericMessage<>(dataRequestEvent, headers);
    queueMessagingTemplate.send(queueUrl, message);

    metricsService.incrementResyncCounter();
  }

  /**
   * Public an event containing details of a user account email changing.
   *
   * @param userId        The ID of the user account.
   * @param traineeId     The ID of the trainee.
   * @param previousEmail The original email associated with the account.
   * @param newEmail      The new email associated with the account.
   */
  public void publishEmailUpdateEvent(String userId, String traineeId, String previousEmail,
      String newEmail) {
    log.info("Publishing email update event for previous email '{}' and new email '{}'.",
        previousEmail, newEmail);
    EmailUpdateEvent event = new EmailUpdateEvent(userId, traineeId, previousEmail, newEmail);
    notificationMessagingTemplate.convertAndSend(userAccountUpdateTopicArn, event, Map.of(
        NOTIFICATION_SUBJECT_HEADER, "Account Email Updated",
        MESSAGE_GROUP_ID_HEADER, userId,
        PRODUCER_HEADER, PRODUCER_VALUE
    ));
  }

  /**
   * Publish a move profile data event.
   *
   * @param fromTisId The TIS ID of the trainee to move data from.
   * @param toTisId   The TIS ID of the trainee to move data to.
   */
  public void publishProfileMoveEvent(String fromTisId, String toTisId) {
    log.info("Sending move profile data event from trainee {} to trainee {}", fromTisId, toTisId);

    ProfileMoveEvent event = new ProfileMoveEvent(fromTisId, toTisId);

    String messageGroupId = fromTisId + "_" + toTisId;

    notificationMessagingTemplate.convertAndSend(profileMoveTopicArn, event, Map.of(
        NOTIFICATION_SUBJECT_HEADER, "Profile Data Move",
        MESSAGE_GROUP_ID_HEADER, messageGroupId,
        PRODUCER_HEADER, PRODUCER_VALUE
    ));
  }

  /**
   * Publish an account update event.
   *
   * @param id        The ID of the account updated.
   * @param sub       The Cognito subject identifier of the account.
   * @param email     The email associated with the account.
   * @param traineeId The TIS ID of the trainee.
   */
  public void publishAccountUpdateEvent(UUID id, String sub, String email, String traineeId) {
    log.info("Publishing account update event for account '{}'.", id);
    AccountUpdateEvent event = new AccountUpdateEvent(id, sub, email, traineeId);
    notificationMessagingTemplate.convertAndSend(accountEventTopicArn, event, Map.of(
        NOTIFICATION_SUBJECT_HEADER, "Account Updated",
        MESSAGE_GROUP_ID_HEADER, id.toString(),
        EVENT_TYPE_HEADER, "UPDATE",
        PRODUCER_HEADER, PRODUCER_VALUE
    ));
  }

  /**
   * Publish an account delete event.
   *
   * @param id The ID of the account deleted.
   */
  public void publishAccountDeleteEvent(UUID id) {
    log.info("Publishing account delete event for account '{}'.", id);
    AccountDeleteEvent event = new AccountDeleteEvent(id);
    notificationMessagingTemplate.convertAndSend(accountEventTopicArn, event, Map.of(
        NOTIFICATION_SUBJECT_HEADER, "Account Deleted",
        MESSAGE_GROUP_ID_HEADER, id.toString(),
        EVENT_TYPE_HEADER, "DELETE",
        PRODUCER_HEADER, PRODUCER_VALUE
    ));
  }
}
