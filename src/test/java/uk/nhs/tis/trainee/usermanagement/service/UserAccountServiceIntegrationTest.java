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

package uk.nhs.tis.trainee.usermanagement.service;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;

@SpringBootTest(properties = {"embedded.containers.enabled=true", "embedded.redis.enabled=true"})
@ActiveProfiles({"redis", "test"})
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(MockitoExtension.class)
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
class UserAccountServiceIntegrationTest {

  private static final String TRAINEE_ID = "40";
  private static final String USER_ID = UUID.randomUUID().toString();

  private static final String ATTRIBUTE_TRAINEE_ID = "custom:tisId";
  private static final String ATTRIBUTE_USER_ID = "sub";

  @MockBean
  private CognitoIdentityProviderClient cognitoClient;

  @MockBean
  private EventPublishService eventPublishService;

  @MockBean
  private SqsTemplate sqsTemplate;

  @Autowired
  UserAccountService service;

  @Autowired
  CacheManager cacheManager;

  private Cache userIdCache;

  @BeforeEach
  void setUp() {
    userIdCache = cacheManager.getCache("UserId");
    assert userIdCache != null;
    userIdCache.clear();
  }

  @Test
  void shouldBuildUserAccountIdCacheWhenPersonNotInCache() {
    UserType user = UserType.builder()
        .attributes(
            at1 -> at1.name(ATTRIBUTE_TRAINEE_ID).value(TRAINEE_ID),
            at2 -> at2.name(ATTRIBUTE_USER_ID).value(USER_ID)
        )
        .build();

    ListUsersResponse response = ListUsersResponse.builder()
        .users(List.of(user))
        .build();

    when(cognitoClient.listUsers((ListUsersRequest) any())).thenReturn(response);

    Set<String> returnedUserIds = service.getUserAccountIds(TRAINEE_ID);

    assertThat("Unexpected user IDs count.", returnedUserIds.size(), is(1));
    assertThat("Unexpected user IDs.", returnedUserIds, hasItem(USER_ID));

    Set<String> cachedUserIds = userIdCache.get(TRAINEE_ID, Set.class);
    assertThat("Unexpected user IDs.", cachedUserIds, notNullValue());
    assertThat("Unexpected user IDs count.", cachedUserIds.size(), is(1));
    assertThat("Unexpected user IDs.", cachedUserIds, hasItem(USER_ID));
  }

  @Test
  void shouldReturnEmptyWhenPersonNotFoundAfterBuildingCache() {
    UserType user = UserType.builder()
        .attributes(
            at1 -> at1.name(ATTRIBUTE_TRAINEE_ID).value(TRAINEE_ID),
            at2 -> at2.name(ATTRIBUTE_USER_ID).value(USER_ID)
        )
        .build();

    ListUsersResponse response = ListUsersResponse.builder()
        .users(List.of(user))
        .build();

    when(cognitoClient.listUsers((ListUsersRequest) any())).thenReturn(response);

    Set<String> returnedUserIds = service.getUserAccountIds("notFound");

    assertThat("Unexpected user IDs count.", returnedUserIds.size(), is(0));
  }

  @Test
  void shouldReturnCachedUserAccountIdWhenPersonInCache() {
    userIdCache.put(TRAINEE_ID, Set.of(USER_ID));

    Set<String> returnedUserIds = service.getUserAccountIds(TRAINEE_ID);

    assertThat("Unexpected user IDs count.", returnedUserIds.size(), is(1));
    assertThat("Unexpected user IDs.", returnedUserIds, hasItem(USER_ID));

    verifyNoInteractions(cognitoClient);
  }
}
