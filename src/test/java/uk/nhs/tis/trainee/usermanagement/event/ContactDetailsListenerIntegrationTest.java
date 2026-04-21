/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
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

package uk.nhs.tis.trainee.usermanagement.event;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

import com.redis.testcontainers.RedisContainer;
import io.awspring.cloud.sns.core.SnsTemplate;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import uk.nhs.tis.trainee.usermanagement.DockerImageNames;
import uk.nhs.tis.trainee.usermanagement.dto.UserAccountDetailsDto;
import uk.nhs.tis.trainee.usermanagement.model.AccountEvent;
import uk.nhs.tis.trainee.usermanagement.model.AccountEvent.AccountEventDetail;
import uk.nhs.tis.trainee.usermanagement.model.AccountEvent.EmailUpdatedDetail;
import uk.nhs.tis.trainee.usermanagement.service.CognitoService;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ContactDetailsListenerIntegrationTest {

  private static final String CONTACT_DETAILS_QUEUE = UUID.randomUUID().toString();

  private static final String USER_ID = UUID.randomUUID().toString();
  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final String USER_EMAIL_OLD = "old@example.com";
  private static final String USER_EMAIL_NEW = "new@example.com";

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongoDBContainer = new MongoDBContainer(
      DockerImageNames.MONGO);

  @Container
  private static final LocalStackContainer localstack = new LocalStackContainer(
      DockerImageNames.LOCALSTACK)
      .withServices(SQS);

  @Container
  private static final RedisContainer redisContainer = new RedisContainer(DockerImageNames.REDIS);

  @DynamicPropertySource
  private static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("application.aws.sqs.contact-details.updated", () -> CONTACT_DETAILS_QUEUE);

    registry.add("spring.cloud.aws.region.static", localstack::getRegion);
    registry.add("spring.cloud.aws.credentials.access-key", localstack::getAccessKey);
    registry.add("spring.cloud.aws.credentials.secret-key", localstack::getSecretKey);
    registry.add("spring.cloud.aws.sqs.endpoint",
        () -> localstack.getEndpointOverride(SQS).toString());
    registry.add("spring.cloud.aws.sqs.enabled", () -> true);

    registry.add("spring.data.redis.host", redisContainer::getHost);
    registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
  }

  @BeforeAll
  static void setUpBeforeAll() throws IOException, InterruptedException {
    localstack.execInContainer("awslocal sqs create-queue --queue-name", CONTACT_DETAILS_QUEUE);
  }

  @Autowired
  private CacheManager cacheManager;

  @Autowired
  private MongoTemplate mongoTemplate;

  @Autowired
  private SqsTemplate sqsTemplate;

  @MockitoBean
  private CognitoService cognitoService;

  @MockitoBean
  private SnsTemplate snsTemplate;

  private Cache cache;

  @BeforeEach
  void setUp() {
    cache = cacheManager.getCache("UserId");
    Objects.requireNonNull(cache);
    cache.clear();
  }

  @AfterEach
  void cleanUp() {
    mongoTemplate.findAllAndRemove(new Query(), AccountEvent.class);
  }

  @Test
  void shouldStoreAccountEventWhenContactDetailsUpdated() {
    cache.put(TRAINEE_ID, Set.of(USER_ID));

    UserAccountDetailsDto oldDetails = UserAccountDetailsDto.builder()
        .id(USER_ID)
        .traineeId(TRAINEE_ID)
        .email(USER_EMAIL_OLD)
        .build();

    when(cognitoService.getUserDetails(USER_ID)).thenReturn(oldDetails);
    when(cognitoService.getUserDetails(USER_EMAIL_NEW)).thenThrow(UserNotFoundException.class);

    String message = """
        {
          "record": {
            "data": {
              "id": "%s",
              "email": "%s"
            }
          }
        }
        """.formatted(TRAINEE_ID, USER_EMAIL_NEW);

    sqsTemplate.send(CONTACT_DETAILS_QUEUE, message);

    Criteria criteria = Criteria.where("userId").is(USER_ID);
    Query query = Query.query(criteria);

    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> {
          AccountEvent found = mongoTemplate.findOne(query, AccountEvent.class);
          assertThat("No account event found.", found, notNullValue());

          AccountEventDetail eventDetail = found.detail();
          assertThat("Unexpected event detail type.", eventDetail,
              instanceOf(EmailUpdatedDetail.class));

          EmailUpdatedDetail emailUpdatedDetail = (EmailUpdatedDetail) eventDetail;
          assertThat("Unexpected old email.", emailUpdatedDetail.before(), is(USER_EMAIL_OLD));
          assertThat("Unexpected new email.", emailUpdatedDetail.after(), is(USER_EMAIL_NEW));
        });
  }
}
