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

package uk.nhs.tis.trainee.usermanagement.api.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;
import uk.nhs.tis.trainee.usermanagement.DockerImageNames;
import uk.nhs.tis.trainee.usermanagement.model.AccountDetails;
import uk.nhs.tis.trainee.usermanagement.repository.AccountDetailsRepository;
import uk.nhs.tis.trainee.usermanagement.service.EventPublishService;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureMockMvc(addFilters = false)
class AccountDetailsResourceIntegrationTest {

  private static final String EXISTING_SUB = UUID.randomUUID().toString();
  private static final String NEW_SUB = UUID.randomUUID().toString();
  private static final String ORPHAN_SUB = UUID.randomUUID().toString();
  private static final String EXISTING_EMAIL = "existing.updated@example.com";
  private static final String NEW_EMAIL = "new.inserted@example.com";
  private static final String ORPHAN_EMAIL = "orphan.deleted@example.com";
  private static final String EXISTING_TRAINEE_ID = "trainee-existing";
  private static final String NEW_TRAINEE_ID = "trainee-new";
  private static final String ORPHAN_TRAINEE_ID = "trainee-orphan";

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongoDBContainer = new MongoDBContainer(
      DockerImageNames.MONGO);

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private MongoTemplate mongoTemplate;

  @Autowired
  private AccountDetailsRepository accountDetailsRepository;

  @MockitoBean
  private CognitoIdentityProviderClient cognitoClient;

  @MockitoBean
  private EventPublishService eventPublishService;

  @BeforeEach
  void setUp() {
    mongoTemplate.remove(new Query(), AccountDetails.class);
  }

  @Test
  void shouldInsertUpdateAndDeleteWhenReconcilingAccountDetails() throws Exception {
    ListUsersResponse cognitoResponse = ListUsersResponse.builder()
        .users(
            UserType.builder()
                .attributes(
                    AttributeType.builder().name("sub").value(EXISTING_SUB).build(),
                    AttributeType.builder().name("email").value(EXISTING_EMAIL).build(),
                    AttributeType.builder().name("custom:tisId").value(EXISTING_TRAINEE_ID).build())
                .build(),
            UserType.builder()
                .attributes(
                    AttributeType.builder().name("sub").value(NEW_SUB).build(),
                    AttributeType.builder().name("email").value(NEW_EMAIL).build(),
                    AttributeType.builder().name("custom:tisId").value(NEW_TRAINEE_ID).build())
                .build())
        .build();
    when(cognitoClient.listUsers(any(ListUsersRequest.class))).thenReturn(cognitoResponse);

    Instant oldLastModified = Instant.now().minus(Duration.ofMinutes(10));
    final AccountDetails existingAccount = mongoTemplate.insert(AccountDetails.builder()
        .sub(EXISTING_SUB)
        .email("stale@example.com")
        .traineeId("stale-trainee")
        .build());
    mongoTemplate.updateFirst(Query.query(Criteria.where("sub").is(EXISTING_SUB)),
        new Update().set("lastModified", oldLastModified), AccountDetails.class);

    mongoTemplate.insert(AccountDetails.builder()
        .sub(ORPHAN_SUB)
        .email(ORPHAN_EMAIL)
        .traineeId(ORPHAN_TRAINEE_ID)
        .build());
    mongoTemplate.updateFirst(Query.query(Criteria.where("sub").is(ORPHAN_SUB)),
        new Update().set("lastModified", oldLastModified), AccountDetails.class);

    mockMvc.perform(post("/api/internal/account-details/jobs/reconcile"))
        .andExpect(status().isNoContent());

    assertThat("Unexpected account details count after reconciliation.",
        accountDetailsRepository.count(), is(2L));

    Optional<AccountDetails> updatedAccount = accountDetailsRepository.findBySub(EXISTING_SUB);
    assertThat("Expected existing account to remain present.", updatedAccount.isPresent(),
        is(true));
    assertThat("Unexpected user id.", updatedAccount.get().id(), is(existingAccount.id()));
    assertThat("Unexpected email.", updatedAccount.get().email(), is(EXISTING_EMAIL));
    assertThat("Unexpected trainee id.", updatedAccount.get().traineeId(), is(EXISTING_TRAINEE_ID));
    assertThat("Unexpected lastModified.",
        updatedAccount.get().lastModified().isAfter(oldLastModified), is(true));

    Optional<AccountDetails> insertedAccount = accountDetailsRepository.findBySub(NEW_SUB);
    assertThat("Expected new account to be inserted.", insertedAccount.isPresent(), is(true));
    assertThat("Expected user id.", insertedAccount.get().id(), notNullValue());
    assertThat("Unexpected email.", insertedAccount.get().email(), is(NEW_EMAIL));
    assertThat("Unexpected trainee id.", insertedAccount.get().traineeId(), is(NEW_TRAINEE_ID));
    assertThat("Unexpected lastModified.", insertedAccount.get().lastModified(), notNullValue());

    assertThat("Expected orphaned account to be deleted.",
        accountDetailsRepository.findBySub(ORPHAN_SUB).isPresent(), is(false));
  }
}
