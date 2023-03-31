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

package uk.nhs.tis.trainee.usermanagement.api;

import com.amazonaws.xray.spring.aop.XRayEnabled;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.tis.trainee.usermanagement.service.EventPublishService;

/**
 * An API for interacting with trainee profile.
 */
@Slf4j
@RestController
@RequestMapping("/api/trainee-profile")
@XRayEnabled
public class TraineeProfileResource {

  private final EventPublishService eventPublishService;

  TraineeProfileResource(EventPublishService eventPublishService) {
    this.eventPublishService = eventPublishService;
  }

  /**
   * Trigger single profile sync for the trainee.
   *
   * @param traineeTisId The tisId for the trainee.
   * @return Ture if single profile sync event is published.
   */
  @PostMapping("/sync/{traineeTisId}")
  ResponseEntity<Void> syncTraineeProfile(@PathVariable String traineeTisId) {
    log.info("Single profile re-sync requested for trainee '{}'", traineeTisId);

    eventPublishService.publishSingleProfileSyncEvent(traineeTisId);

    return ResponseEntity.ok().build();
  }
}
