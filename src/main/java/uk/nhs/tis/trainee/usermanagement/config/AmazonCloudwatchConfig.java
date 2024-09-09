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

package uk.nhs.tis.trainee.usermanagement.config;

import io.micrometer.cloudwatch2.CloudWatchConfig;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;

@Configuration
public class AmazonCloudwatchConfig {

  private final String awsRegion;
  private final String metricsNamespace;
  private final String awsAccessKey;
  private final String awsSecret;

  public AmazonCloudwatchConfig(@Value("${cloud.aws.region.static}") String awsRegion,
                                @Value("${cloud.aws.cloudwatch.namespace}") String metricsNamespace,
                                @Value("${cloud.aws.cloudwatch.awsAccessKey}") String awsAccessKey,
                                @Value("${cloud.aws.cloudwatch.awsSecret}") String awsSecret) {
    this.awsRegion = awsRegion;
    this.metricsNamespace = metricsNamespace;
    this.awsAccessKey = awsAccessKey;
    this.awsSecret = awsSecret;
  }

  @Bean
  public CloudWatchAsyncClient cloudWatchAsyncClient() {
    return CloudWatchAsyncClient.builder()
        .region(Region.of(awsRegion))
        .credentialsProvider(
            StaticCredentialsProvider.create(new AwsCredentials() {
              @Override
              public String accessKeyId() {
                return awsAccessKey;
              }

              @Override
              public String secretAccessKey() {
                return awsSecret;
              }
            })
        )
        .build();
  }

  @Bean
  public MeterRegistry getMeterRegistry() {
    CloudWatchConfig cloudWatchConfig = setupCloudWatchConfig();

    return new CloudWatchMeterRegistry(cloudWatchConfig, Clock.SYSTEM, cloudWatchAsyncClient());
  }

  private CloudWatchConfig setupCloudWatchConfig() {
    return new CloudWatchConfig() {

      private final Map<String, String> configuration
          = Map.of("cloudwatch.namespace", metricsNamespace,
          "cloudwatch.step", Duration.ofMinutes(1).toString());

      @Override
      public String get(String key) {
        return configuration.get(key);
      }
    };
  }
}
