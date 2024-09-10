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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.nhs.tis.trainee.usermanagement.service.MetricsService.METRIC_NAME_ACCOUNT_DELETE;
import static uk.nhs.tis.trainee.usermanagement.service.MetricsService.METRIC_NAME_MFA_RESET;
import static uk.nhs.tis.trainee.usermanagement.service.MetricsService.METRIC_RESYNC;
import static uk.nhs.tis.trainee.usermanagement.service.MetricsService.TAG_MFA;
import static uk.nhs.tis.trainee.usermanagement.service.MetricsService.TAG_USER_STATUS;

import com.amazonaws.services.cognitoidp.model.UserStatusType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.cumulative.CumulativeCounter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import uk.nhs.tis.trainee.usermanagement.enumeration.MfaType;

class MetricsServiceTest {

  private MetricsService service;

  @BeforeEach
  void setup() {
    MeterRegistry meterRegistry = mock(MeterRegistry.class);
    Meter.Id idAccountDelete = new Meter.Id("delete", Tags.empty(), null, null, Meter.Type.COUNTER);
    Counter counterAccountDelete = new CumulativeCounter(idAccountDelete);

    when(meterRegistry.counter(eq(METRIC_NAME_ACCOUNT_DELETE),
        any(), any(),
        eq(TAG_USER_STATUS), any(),
        eq(TAG_MFA), any())).thenReturn(counterAccountDelete);

    Meter.Id idResetMfa = new Meter.Id("resetMfa", Tags.empty(), null, null, Meter.Type.COUNTER);
    Counter counterResetMfa = new CumulativeCounter(idResetMfa);
    when(meterRegistry.counter(eq(METRIC_NAME_MFA_RESET),
        any(), any(),
        eq(TAG_MFA), any())).thenReturn(counterResetMfa);

    Meter.Id idResync = new Meter.Id("resync", Tags.empty(), null, null, Meter.Type.COUNTER);
    Counter counterResync = new CumulativeCounter(idResync);
    when(meterRegistry.counter(eq(METRIC_RESYNC),
        any(), any())).thenReturn(counterResync);

    service = new MetricsService(meterRegistry, "test");
  }

  @ParameterizedTest
  @EnumSource(MfaType.class)
  void shouldIncrementMfaResetCounter(MfaType mfaType) {
    double before = service.resetMfaCounters.get(mfaType).count();

    service.incrementMfaResetCounter(mfaType);
    double after = service.resetMfaCounters.get(mfaType).count();
    double expected = before + 1;

    assertThat("Unexpected MFA reset counter.", after, is(expected));
  }

  @ParameterizedTest
  @MethodSource("mfaAndUserStatusMethodSource")
  void shouldIncrementAccountDeleteCounter(MfaType mfaType, UserStatusType userStatusType) {
    double before = service.deleteAccountCounters.get(mfaType).get(userStatusType).count();

    service.incrementDeleteAccountCounter(mfaType, userStatusType);
    double after = service.deleteAccountCounters.get(mfaType).get(userStatusType).count();
    double expected = before + 1;

    assertThat("Unexpected Account delete counter.", after, is(expected));
  }

  @Test
  void shouldIncrementResyncCounter() {
    double before = service.resyncCounter.count();

    service.incrementResyncCounter();
    double after = service.resyncCounter.count();
    double expected = before + 1;

    assertThat("Unexpected resync counter.", after, is(expected));
  }

  /**
   * Create a stream of paired MFA and UserStatusType arguments.
   *
   * @return The stream of arguments.
   */
  private static Stream<Arguments> mfaAndUserStatusMethodSource() {
    List<Arguments> args = new ArrayList<>();
    for (MfaType mfaType : MfaType.values()) {
      for (UserStatusType userStatusType : UserStatusType.values()) {
        args.add(Arguments.of(mfaType, userStatusType));
      }
    }
    return args.stream();
  }
}
