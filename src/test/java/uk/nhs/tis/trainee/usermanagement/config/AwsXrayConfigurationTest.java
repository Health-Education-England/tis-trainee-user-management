/*
 * The MIT License (MIT)
 *
 * Copyright 2023 Crown Copyright (Health Education England)
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

package uk.nhs.tis.trainee.usermanagement.config;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.amazonaws.xray.jakarta.servlet.AWSXRayServletFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AwsXrayConfigurationTest {

  private static final String DAEMON_PROPERTY = "com.amazonaws.xray.emitters.daemon-address";

  private ApplicationContextRunner runner;

  @BeforeEach
  void setUp() {
    runner = new ApplicationContextRunner()
        .withUserConfiguration(AwsXrayConfiguration.class);
  }

  @Test
  void shouldDisableConfigIfDaemonAddressNotSet() {
    runner
        .withPropertyValues(DAEMON_PROPERTY + "=")
        .run(context -> assertThat("Unexpected bean presence.",
            context.containsBean("tracingFilter"), is(false)));
  }

  @Test
  void shouldEnableConfigIfDaemonAddressSet() {
    runner
        .withPropertyValues(DAEMON_PROPERTY + "=https://localhost:1234")
        .run(context -> assertAll(
            () -> assertThat("Unexpected bean presence.",
                context.containsBean("awsXrayConfiguration"), is(true)),
            () -> assertThat("Unexpected bean type.", context.getBean("awsXrayConfiguration"),
                instanceOf(AwsXrayConfiguration.class))
        ));
  }

  @Test
  void shouldRegisterAwsXrayTracingFilterWhenConfigEnabled() {
    runner
        .withPropertyValues(DAEMON_PROPERTY + "=https://localhost:1234")
        .run(context -> assertAll(
            () -> assertThat("Unexpected bean presence.", context.containsBean("tracingFilter"),
                is(true)),
            () -> assertThat("Unexpected bean type.", context.getBean("tracingFilter"),
                instanceOf(AWSXRayServletFilter.class))
        ));
  }
}
