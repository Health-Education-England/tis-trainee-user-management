package uk.nhs.tis.trainee.usermanagement.service;

import com.amazonaws.services.cognitoidp.model.UserStatusType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

  protected static final String TAG_ENVIRONMENT = "Environment";
  protected static final String TAG_MFA = "MfaType";
  protected static final String TAG_USER_STATUS = "UserStatus";

  private final Map<MfaType, Map<UserStatusType, Counter>> deleteAccountCounters;
  private final Map<MfaType, Counter> resetMfaCounters;
  private final Counter resyncCounter;

  public MetricsService(MeterRegistry meterRegistry,
                        @Value("${application.environment}") String environment) {

    this.deleteAccountCounters = new EnumMap<>(MfaType.class);
    this.resetMfaCounters = new EnumMap<>(MfaType.class);
    for (MfaType mfaType : MfaType.values()) {
      Map<UserStatusType, Counter> userStatusTypeMap = new EnumMap<>(UserStatusType.class);
      for (UserStatusType userStatusType : UserStatusType.values()) {
        userStatusTypeMap.put(userStatusType,
            meterRegistry.counter(METRIC_NAME_ACCOUNT_DELETE,
                TAG_ENVIRONMENT, environment,
                TAG_USER_STATUS, userStatusType.toString(),
                TAG_MFA, mfaType.name()));
      }
      this.deleteAccountCounters.put(mfaType, userStatusTypeMap);
      this.resetMfaCounters.put(mfaType, meterRegistry.counter(METRIC_NAME_MFA_RESET,
          TAG_ENVIRONMENT, environment,
          TAG_MFA, mfaType.name()));
    }

    resyncCounter = meterRegistry.counter(METRIC_RESYNC, TAG_ENVIRONMENT, environment);

    this.resetMfaCounters.get(MfaType.TOTP).increment(5.0);
  }

  public void incrementMfaResetCounter(MfaType mfaType) {
    if (mfaType != null) {
      this.resetMfaCounters.get(mfaType).increment();
    } else {
      log.warn("Cannot increment null MFA counter");
    }
  }

  public void incrementDeleteAccountCounter(MfaType mfaType, UserStatusType userStatusType) {
    deleteAccountCounters.get(mfaType).get(userStatusType).increment();
  }

  public void incrementResyncCounter() {
    this.resyncCounter.increment();
  }
}
