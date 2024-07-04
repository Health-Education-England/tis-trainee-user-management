/*
 * The MIT License (MIT)
 *
 * Copyright 2025 Crown Copyright (Health Education England)
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transformuk.hee.tis.profile.client.service.impl.JwtProfileServiceImpl;
import com.transformuk.hee.tis.security.service.JwtProfileService;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

class ProfileClientConfigurationTest {

  private static final int TIMEOUT = 12345;

  private ProfileClientConfiguration configuration;

  @BeforeEach
  void setUp() {
    configuration = new ProfileClientConfiguration(TIMEOUT);
  }

  @Test
  void shouldCreateProfileService() {
    RestTemplateBuilder builder = new RestTemplateBuilder();

    JwtProfileService service = configuration.jwtProfileService(builder);

    assertThat("Unexpected service type.", service, instanceOf(JwtProfileServiceImpl.class));
  }

  @Test
  void shouldSetClientParameters() {
    RestTemplateBuilder builder = mock(RestTemplateBuilder.class);
    when(builder.requestFactory((Class<? extends ClientHttpRequestFactory>) any()))
        .thenAnswer(InvocationOnMock::getMock);
    when(builder.setReadTimeout(any())).thenAnswer(InvocationOnMock::getMock);
    when(builder.setConnectTimeout(any())).thenAnswer(InvocationOnMock::getMock);

    configuration.jwtProfileService(builder);

    verify(builder).requestFactory(HttpComponentsClientHttpRequestFactory.class);
    verify(builder).setReadTimeout(Duration.ofSeconds(TIMEOUT));
    verify(builder).setConnectTimeout(Duration.ofSeconds(TIMEOUT));
  }
}
