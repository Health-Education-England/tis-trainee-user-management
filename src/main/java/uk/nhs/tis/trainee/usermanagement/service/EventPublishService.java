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

import com.amazonaws.xray.spring.aop.XRayEnabled;
import io.awspring.cloud.messaging.core.QueueMessagingTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.nhs.tis.trainee.usermanagement.event.DataRequestEvent;

/**
 * A service to publish events to SQS.
 */
@Slf4j
@Service
@XRayEnabled
public class EventPublishService {

  private final QueueMessagingTemplate messagingTemplate;
  private final String queueUrl;

  EventPublishService(QueueMessagingTemplate messagingTemplate,
      @Value("${application.aws.sqs.request}") String queueUrl) {
    this.messagingTemplate = messagingTemplate;
    this.queueUrl = queueUrl;
  }

  /**
   * Publish a single profile sync event.
   *
   * @param traineeTisId The TisId of the trainee to sync.
   */
  public void publishSingleProfileSyncEvent(String traineeTisId) {
    log.info("Sending single profile sync event for trainee id '{}'", traineeTisId);

    DataRequestEvent dataRequestEvent = new DataRequestEvent("Person", traineeTisId);
    messagingTemplate.convertAndSend(queueUrl, dataRequestEvent);
  }
}
