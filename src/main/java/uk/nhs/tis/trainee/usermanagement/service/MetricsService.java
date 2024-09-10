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

package uk.nhs.tis.trainee.usermanagement.service;

import com.amazonaws.services.cognitoidp.model.UserStatusType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.nhs.tis.trainee.usermanagement.enumeration.MfaType;

/**
 * A service for defining and publishing metrics.
 */
@Service
@Slf4j
public class MetricsService {
  protected static final String METRIC_NAME_MFA_RESET = "account.mfa.reset";
  protected static final String METRIC_NAME_ACCOUNT_DELETE = "account.delete";
  protected static final String METRIC_RESYNC = "data.resync";

  protected static final String TAG_MFA = "MfaType";
  protected static final String TAG_USER_STATUS = "UserStatus";

  protected final Map<MfaType, Map<UserStatusType, Counter>> deleteAccountCounters;
  protected final Map<MfaType, Counter> resetMfaCounters;
  protected final Counter resyncCounter;

  /**
   * Initialise the metrics service.
   *
   * @param meterRegistry The Meter Registry to use.
   */
  public MetricsService(MeterRegistry meterRegistry) {

    this.deleteAccountCounters = new EnumMap<>(MfaType.class);
    this.resetMfaCounters = new EnumMap<>(MfaType.class);
    for (MfaType mfaType : MfaType.values()) {
      Map<UserStatusType, Counter> userStatusTypeMap = new EnumMap<>(UserStatusType.class);
      for (UserStatusType userStatusType : UserStatusType.values()) {
        userStatusTypeMap.put(userStatusType,
            meterRegistry.counter(METRIC_NAME_ACCOUNT_DELETE,
                TAG_USER_STATUS, userStatusType.toString(),
                TAG_MFA, mfaType.name()));
      }
      this.deleteAccountCounters.put(mfaType, userStatusTypeMap);
      this.resetMfaCounters.put(mfaType, meterRegistry.counter(METRIC_NAME_MFA_RESET,
          TAG_MFA, mfaType.name()));
    }

    resyncCounter = meterRegistry.counter(METRIC_RESYNC);
  }

  /**
   * Increment the MFA Reset counter.
   *
   * @param mfaType The MFA type whose counter should be incremented.
   */
  public void incrementMfaResetCounter(MfaType mfaType) {
    this.resetMfaCounters.get(mfaType).increment();
  }

  /**
   * Increment the Delete Account counter.
   *
   * @param mfaType The MFA type whose counter should be incremented.
   * @param userStatusType The User Status whose counter should be incremented.
   */
  public void incrementDeleteAccountCounter(MfaType mfaType, UserStatusType userStatusType) {
    deleteAccountCounters.get(mfaType).get(userStatusType).increment();
  }

  /**
   * Increment the Account Resync counter.
   */
  public void incrementResyncCounter() {
    this.resyncCounter.increment();
  }
}
