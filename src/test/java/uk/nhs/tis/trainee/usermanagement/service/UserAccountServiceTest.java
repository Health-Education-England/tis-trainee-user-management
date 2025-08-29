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

package uk.nhs.tis.trainee.usermanagement.service;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static software.amazon.awssdk.services.cognitoidentityprovider.model.UserStatusType.CONFIRMED;
import static uk.nhs.tis.trainee.usermanagement.enumeration.MfaType.NO_MFA;
import static uk.nhs.tis.trainee.usermanagement.enumeration.MfaType.SOFTWARE_TOKEN_MFA;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListUserAuthEventsRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListUserAuthEventsResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRemoveUserFromGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRemoveUserFromGroupResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserMfaPreferenceRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserMfaPreferenceResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthEventType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ChallengeResponseType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.EventContextDataType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.EventResponseType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.EventType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.TooManyRequestsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;
import uk.nhs.tis.trainee.usermanagement.dto.UserAccountDetailsDto;
import uk.nhs.tis.trainee.usermanagement.dto.UserLoginDetailsDto;
import uk.nhs.tis.trainee.usermanagement.enumeration.MfaType;

class UserAccountServiceTest {

  private static final String USER_POOL_ID = "region_abc213";
  private static final String EMAIL = "joe.bloggs@fake.email";
  private static final String GROUP_1 = "user-group-one";

  private static final String TRAINEE_ID_1 = UUID.randomUUID().toString();
  private static final String TRAINEE_ID_2 = UUID.randomUUID().toString();
  private static final String USER_ID_1 = UUID.randomUUID().toString();
  private static final String USER_ID_2 = UUID.randomUUID().toString();
  private static final String USER_ID_3 = UUID.randomUUID().toString();

  private static final String FORENAMES_1 = "forenames-one";
  private static final String FORENAMES_2 = "forenames-two";
  private static final String SURNAME_1 = "surname-one";
  private static final String SURNAME_2 = "surname-two";

  private static final String ATTRIBUTE_TRAINEE_ID = "custom:tisId";
  private static final String ATTRIBUTE_USER_ID = "sub";
  private static final String ATTRIBUTE_EMAIL = "email";
  private static final String ATTRIBUTE_FAMILY_NAME = "family_name";
  private static final String ATTRIBUTE_GIVEN_NAME = "given_name";
  private static final String ATTRIBUTE_EMAIL_VERIFIED = "email_verified";

  private UserAccountService service;
  private CognitoService cognitoService;
  private Cache cache;
  private EventPublishService eventPublishService;
  private MetricsService metricsService;

  @BeforeEach
  void setUp() {
    cognitoService = mock(CognitoService.class);
    cache = mock(Cache.class);

    CacheManager cacheManager = mock(CacheManager.class);
    when(cacheManager.getCache("UserId")).thenReturn(cache);

    eventPublishService = mock(EventPublishService.class);
    metricsService = mock(MetricsService.class);

    service = spy(new UserAccountService(cognitoService, USER_POOL_ID, cacheManager,
        eventPublishService, metricsService));
  }

  @Test
  void shouldReturnNoAccountDetailsWhenUserNotFoundGettingUser() {
    when(cognitoService.getUserDetails(any())).thenThrow(UserNotFoundException.class);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(EMAIL);
    assertThat("Unexpected MFA status.", userAccountDetails.getMfaStatus(), is("NO_ACCOUNT"));
    assertThat("Unexpected user status.", userAccountDetails.getUserStatus(), is("NO_ACCOUNT"));
    assertThat("Unexpected user groups size.", userAccountDetails.getGroups().size(), is(0));
    assertThat("Unexpected account created", userAccountDetails.getAccountCreated(), nullValue());
  }

  @Test
  void shouldReturnAccountDetailsWhenUserFoundGettingUser() {
    UserAccountDetailsDto userDetails = UserAccountDetailsDto.builder().id(USER_ID_1).build();

    when(cognitoService.getUserDetails(any())).thenReturn(userDetails);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(EMAIL);
    assertThat("Unexpected user details.", userAccountDetails, sameInstance(userDetails));
  }

  @Test
  void shouldThrowExceptionUpdatingEmailWhenEmailBelongsToAnotherAccount() {
    String newEmail = "new.email@example.com";

    UserAccountDetailsDto userDetails = UserAccountDetailsDto.builder().id(USER_ID_2).build();
    when(cognitoService.getUserDetails(any())).thenReturn(userDetails);

    assertThrows(IllegalArgumentException.class,
        () -> service.updateContactDetails(USER_ID_1, newEmail, FORENAMES_1, SURNAME_1));

    verify(cognitoService, never()).updateAttributes(any(), any());
  }

  @Test
  void shouldOnlyUpdateNamesWhenTheEmailHasNotChanged() {
    String newEmail = "new.email@example.com";

    UserAccountDetailsDto userDetails = UserAccountDetailsDto.builder()
        .id(USER_ID_1)
        .build();

    when(cognitoService.getUserDetails(any())).thenReturn(userDetails);

    service.updateContactDetails(USER_ID_1, newEmail, FORENAMES_2, SURNAME_2);

    ArgumentCaptor<List<AttributeType>> attributesCaptor = ArgumentCaptor.captor();
    verify(cognitoService).updateAttributes(eq(USER_ID_1), attributesCaptor.capture());

    List<AttributeType> userAttributes = attributesCaptor.getValue();
    assertThat("Unexpected attribute count.", userAttributes.size(), is(2));

    AttributeType userAttribute = userAttributes.get(0);
    assertThat("Unexpected attribute name.", userAttribute.name(), is(ATTRIBUTE_FAMILY_NAME));
    assertThat("Unexpected attribute value.", userAttribute.value(), is(SURNAME_2));

    userAttribute = userAttributes.get(1);
    assertThat("Unexpected attribute name.", userAttribute.name(), is(ATTRIBUTE_GIVEN_NAME));
    assertThat("Unexpected attribute value.", userAttribute.value(), is(FORENAMES_2));
  }

  @Test
  void shouldUpdateEmailAndNamesWhenNewEmailNotExistsInUserPool() {
    String newEmail = "new.email@example.com";

    ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.captor();
    when(cognitoService.getUserDetails(usernameCaptor.capture()))
        .thenThrow(UserNotFoundException.class)
        .thenReturn(UserAccountDetailsDto.builder().id(USER_ID_1).build());

    service.updateContactDetails(USER_ID_1, newEmail, FORENAMES_2, SURNAME_2);

    List<String> usernames = usernameCaptor.getAllValues();
    assertThat("Unexpected list users request count.", usernames.size(), is(2));

    String username = usernames.get(0);
    assertThat("Unexpected filter.", username, is(newEmail));

    ArgumentCaptor<List<AttributeType>> attributesCaptor = ArgumentCaptor.captor();
    verify(cognitoService).updateAttributes(eq(USER_ID_1), attributesCaptor.capture());

    List<AttributeType> userAttributes = attributesCaptor.getValue();
    assertThat("Unexpected attribute count.", userAttributes.size(), is(4));

    AttributeType userAttribute = userAttributes.get(0);
    assertThat("Unexpected attribute name.", userAttribute.name(), is(ATTRIBUTE_FAMILY_NAME));
    assertThat("Unexpected attribute value.", userAttribute.value(), is(SURNAME_2));

    userAttribute = userAttributes.get(1);
    assertThat("Unexpected attribute name.", userAttribute.name(), is(ATTRIBUTE_GIVEN_NAME));
    assertThat("Unexpected attribute value.", userAttribute.value(), is(FORENAMES_2));

    userAttribute = userAttributes.get(2);
    assertThat("Unexpected attribute name.", userAttribute.name(), is(ATTRIBUTE_EMAIL));
    assertThat("Unexpected attribute value.", userAttribute.value(), is(newEmail));

    userAttribute = userAttributes.get(3);
    assertThat("Unexpected attribute name.", userAttribute.name(), is(ATTRIBUTE_EMAIL_VERIFIED));
    assertThat("Unexpected attribute value.", userAttribute.value(), is("true"));
  }

  @Test
  void shouldPublishEventAfterUpdatingEmail() {
    String previousEmail = "previous.email@example.com";
    String newEmail = "new.email@example.com";

    UserAccountDetailsDto userDetails = UserAccountDetailsDto.builder()
        .id(USER_ID_1)
        .email(previousEmail)
        .traineeId(TRAINEE_ID_1)
        .build();

    ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.captor();
    when(cognitoService.getUserDetails(usernameCaptor.capture()))
        .thenThrow(UserNotFoundException.class)
        .thenReturn(userDetails);

    service.updateContactDetails(USER_ID_1, newEmail, FORENAMES_1, SURNAME_1);

    List<String> usernames = usernameCaptor.getAllValues();
    assertThat("Unexpected get user details request count.", usernames.size(), is(2));

    String username = usernames.get(1);
    assertThat("Unexpected username.", username, is(USER_ID_1));

    verify(eventPublishService).publishEmailUpdateEvent(USER_ID_1, TRAINEE_ID_1, previousEmail,
        newEmail);
  }

  @ParameterizedTest
  @EnumSource(MfaType.class)
  void shouldResetMfa(MfaType mfaType) {
    ArgumentCaptor<AdminSetUserMfaPreferenceRequest> requestCaptor = ArgumentCaptor.captor();

    when(cognitoService.adminSetUserMfaPreference(requestCaptor.capture())).thenReturn(
        AdminSetUserMfaPreferenceResponse.builder().build());

    UserAccountDetailsDto userDetails = UserAccountDetailsDto.builder()
        .mfaStatus(mfaType.toString())
        .build();
    when(cognitoService.getUserDetails(EMAIL)).thenReturn(userDetails);

    service.resetUserAccountMfa(EMAIL);

    AdminSetUserMfaPreferenceRequest request = requestCaptor.getValue();
    assertThat("Unexpected userDetails pool.", request.userPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected username.", request.username(), is(EMAIL));
    assertThat("Unexpected SMS enabled flag.", request.smsMfaSettings().enabled(), is(false));
    assertThat("Unexpected TOTP enabled flag.", request.softwareTokenMfaSettings().enabled(),
        is(false));
  }

  @ParameterizedTest
  @EnumSource(MfaType.class)
  void shouldUpdateUserAttributeWhenMfaReset(MfaType mfaType) {
    when(cognitoService.adminSetUserMfaPreference(any())).thenReturn(
        AdminSetUserMfaPreferenceResponse.builder().build());

    UserAccountDetailsDto userDetails = UserAccountDetailsDto.builder()
        .mfaStatus(mfaType.toString())
        .build();
    when(cognitoService.getUserDetails(EMAIL)).thenReturn(userDetails);

    service.resetUserAccountMfa(EMAIL);

    ArgumentCaptor<List<AttributeType>> attributesCaptor = ArgumentCaptor.captor();
    verify(cognitoService).updateAttributes(eq(EMAIL), attributesCaptor.capture());

    List<AttributeType> userAttributes = attributesCaptor.getValue();
    assertThat("Unexpected attribute count.", userAttributes, hasSize(1));

    AttributeType userAttribute = userAttributes.get(0);
    assertThat("Unexpected attribute name.", userAttribute.name(), is("custom:mfaType"));
    assertThat("Unexpected attribute value.", userAttribute.value(), is("NO_MFA"));
  }

  @ParameterizedTest
  @EnumSource(MfaType.class)
  void shouldPublishMetricWhenMfaReset(MfaType mfaType) {
    when(cognitoService.adminSetUserMfaPreference(any())).thenReturn(
        AdminSetUserMfaPreferenceResponse.builder().build());

    UserAccountDetailsDto userDetails = UserAccountDetailsDto.builder()
        .mfaStatus(mfaType.toString())
        .build();
    when(cognitoService.getUserDetails(EMAIL)).thenReturn(userDetails);

    service.resetUserAccountMfa(EMAIL);

    verify(metricsService).incrementMfaResetCounter(mfaType);
  }

  @Test
  void shouldDeleteCognitoAccount() {
    ArgumentCaptor<AdminDeleteUserRequest> requestCaptor = ArgumentCaptor.captor();

    when(cognitoService.adminDeleteUser(requestCaptor.capture())).thenReturn(
        AdminDeleteUserResponse.builder().build());

    UserAccountDetailsDto userDetails = UserAccountDetailsDto.builder()
        .userStatus(CONFIRMED.toString())
        .mfaStatus(SOFTWARE_TOKEN_MFA.toString())
        .build();
    when(cognitoService.getUserDetails(any())).thenReturn(userDetails);

    service.deleteCognitoAccount(EMAIL);

    AdminDeleteUserRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool.", request.userPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected delete account username.", request.username(), is(EMAIL));
    verify(metricsService).incrementDeleteAccountCounter(SOFTWARE_TOKEN_MFA, CONFIRMED);
  }

  @Test
  void shouldGetAccountByCurrentEmailWhenDeletingDuplicates() {
    ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.captor();
    when(cognitoService.getUserDetails(usernameCaptor.capture())).thenAnswer(inv -> {
      String username = inv.getArgument(0);
      boolean isGetByEmail = username.equals(EMAIL);

      return UserAccountDetailsDto.builder()
          .id(isGetByEmail ? USER_ID_1 : username)
          .email(isGetByEmail ? EMAIL : "other.email@example.com")
          .traineeId(TRAINEE_ID_1)
          .mfaStatus(NO_MFA.toString())
          .userStatus(CONFIRMED.toString())
          .build();
    });

    service.deleteDuplicateAccounts(TRAINEE_ID_1, Set.of(USER_ID_1, USER_ID_2, USER_ID_3), EMAIL);

    String username = usernameCaptor.getAllValues().get(0);
    assertThat("Unexpected username.", username, is(EMAIL));
  }

  @Test
  void shouldDeleteDuplicatesWhenTisEmailMatches() {
    when(cognitoService.getUserDetails(any())).thenAnswer(inv -> {
      String username = inv.getArgument(0);
      boolean isGetByEmail = username.equals(EMAIL);

      return UserAccountDetailsDto.builder()
          .id(isGetByEmail ? USER_ID_1 : username)
          .email(isGetByEmail ? EMAIL : "other.email@example.com")
          .traineeId(TRAINEE_ID_1)
          .mfaStatus(NO_MFA.toString())
          .userStatus(CONFIRMED.toString())
          .build();
    });

    Optional<String> accountId = service.deleteDuplicateAccounts(TRAINEE_ID_1,
        Set.of(USER_ID_1, USER_ID_2, USER_ID_3), EMAIL);

    assertThat("Unexpected remaining account.", accountId, is(Optional.of(USER_ID_1)));

    ArgumentCaptor<AdminDeleteUserRequest> deleteUserCaptor = ArgumentCaptor.captor();
    verify(cognitoService, times(2)).adminDeleteUser(deleteUserCaptor.capture());

    List<AdminDeleteUserRequest> deleteUserRequests = deleteUserCaptor.getAllValues();
    assertThat("Unexpected request count.", deleteUserRequests, hasSize(2));

    Set<String> deletedIds = deleteUserRequests.stream()
        .peek(request -> assertThat("Unexpected user pool.", request.userPoolId(),
            is(USER_POOL_ID)))
        .map(AdminDeleteUserRequest::username)
        .collect(Collectors.toSet());
    assertThat("Unexpected deleted account count.", deletedIds, hasSize(2));
    assertThat("Unexpected deleted account.", deletedIds, hasItems(USER_ID_2, USER_ID_3));

    verify(metricsService, times(2)).incrementDeleteAccountCounter(any(), any());
  }

  @Test
  void shouldNotDeleteDuplicatesWhenTisEmailNotMatchesAndNoSuccessfulAuth() {
    ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.captor();
    when(cognitoService.getUserDetails(usernameCaptor.capture())).thenAnswer(inv -> {
      String username = inv.getArgument(0);

      return UserAccountDetailsDto.builder()
          .id(username)
          .email("no-match@example.com")
          .traineeId(TRAINEE_ID_1)
          .userStatus(CONFIRMED.toString())
          .mfaStatus(NO_MFA.toString())
          .build();
    });

    when(cognitoService.adminListUserAuthEvents(any())).thenReturn(
        AdminListUserAuthEventsResponse.builder()
            .authEvents(List.of())
            .build()
    );

    Optional<String> accountId = service.deleteDuplicateAccounts(TRAINEE_ID_1,
        Set.of(USER_ID_1, USER_ID_2, USER_ID_3), EMAIL);

    assertThat("Unexpected remaining account.", accountId, is(Optional.empty()));

    String username = usernameCaptor.getAllValues().get(0);
    assertThat("Unexpected username.", username, is(EMAIL));

    verify(cognitoService, times(0)).adminDeleteUser(any());
    verify(metricsService, times(0)).incrementDeleteAccountCounter(any(), any());
  }

  @Test
  void shouldNotDeleteDuplicatesWhenTisEmailNotMatchesAndSuccessfulAuth() {
    ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.captor();
    when(cognitoService.getUserDetails(usernameCaptor.capture())).thenAnswer(inv -> {
      String username = inv.getArgument(0);
      boolean isGetByEmail = username.equals(EMAIL);

      return UserAccountDetailsDto.builder()
          .id(isGetByEmail ? UUID.randomUUID().toString() : username)
          .email(isGetByEmail ? username : "other.email@example.com")
          .traineeId(TRAINEE_ID_1)
          .userStatus(CONFIRMED.toString())
          .build();
    });

    when(cognitoService.adminListUserAuthEvents(any())).thenReturn(
        AdminListUserAuthEventsResponse.builder()
            .authEvents(
                AuthEventType.builder()
                    .creationDate(Instant.now())
                    .eventType(EventType.SIGN_IN)
                    .eventResponse(EventResponseType.PASS)
                    .build())
            .build()
    );

    Optional<String> accountId = service.deleteDuplicateAccounts(TRAINEE_ID_1,
        Set.of(USER_ID_1, USER_ID_2, USER_ID_3), EMAIL);

    assertThat("Unexpected remaining account.", accountId, is(Optional.empty()));

    String username = usernameCaptor.getAllValues().get(0);
    assertThat("Unexpected username.", username, is(EMAIL));

    verify(cognitoService, times(0)).adminDeleteUser(any());
    verify(metricsService, times(0)).incrementDeleteAccountCounter(any(), any());
  }

  @Test
  void shouldNotDeleteDuplicatesWhenTisEmailNotFoundAndNoSuccessfulAuth() {
    ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.captor();
    when(cognitoService.getUserDetails(usernameCaptor.capture())).thenAnswer(inv -> {
      String username = inv.getArgument(0);

      return UserAccountDetailsDto.builder()
          .id(username)
          .email("no-match@example.com")
          .traineeId(TRAINEE_ID_1)
          .userStatus(CONFIRMED.toString())
          .mfaStatus(NO_MFA.toString())
          .build();
    });
    when(cognitoService.getUserDetails(EMAIL)).thenThrow(UserNotFoundException.class);

    when(cognitoService.adminListUserAuthEvents(any())).thenReturn(
        AdminListUserAuthEventsResponse.builder()
            .authEvents(List.of())
            .build()
    );

    Optional<String> accountId = service.deleteDuplicateAccounts(TRAINEE_ID_1,
        Set.of(USER_ID_1, USER_ID_2, USER_ID_3), EMAIL);

    assertThat("Unexpected remaining account.", accountId, is(Optional.empty()));

    String username = usernameCaptor.getAllValues().get(0);
    assertThat("Unexpected username.", username, is(EMAIL));

    verify(cognitoService, times(0)).adminDeleteUser(any());
    verify(metricsService, times(0)).incrementDeleteAccountCounter(any(), any());
  }

  @Test
  void shouldNotDeleteDuplicatesWhenTisEmailNotFoundAndSuccessfulAuth() {
    ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.captor();
    when(cognitoService.getUserDetails(usernameCaptor.capture())).thenAnswer(inv -> {
      String username = inv.getArgument(0);

      return UserAccountDetailsDto.builder()
          .id(username)
          .email("other.email@example.com")
          .traineeId(TRAINEE_ID_1)
          .userStatus(CONFIRMED.toString())
          .build();
    });
    when(cognitoService.getUserDetails(EMAIL)).thenThrow(UserNotFoundException.class);

    when(cognitoService.adminListUserAuthEvents(any())).thenReturn(
        AdminListUserAuthEventsResponse.builder()
            .authEvents(
                AuthEventType.builder()
                    .creationDate(Instant.now())
                    .eventType(EventType.SIGN_IN)
                    .eventResponse(EventResponseType.PASS)
                    .build())
            .build()
    );

    Optional<String> accountId = service.deleteDuplicateAccounts(TRAINEE_ID_1,
        Set.of(USER_ID_1, USER_ID_2, USER_ID_3), EMAIL);

    assertThat("Unexpected remaining account.", accountId, is(Optional.empty()));

    String username = usernameCaptor.getAllValues().get(0);
    assertThat("Unexpected username.", username, is(EMAIL));

    verify(cognitoService, times(0)).adminDeleteUser(any());
    verify(metricsService, times(0)).incrementDeleteAccountCounter(any(), any());
  }

  @Test
  void shouldRetryPaginatingThroughAuthEventsWhenRateLimitedGettingAuthEvents() {
    when(cognitoService.getUserDetails(any())).thenAnswer(inv -> {
      String username = inv.getArgument(0);

      return UserAccountDetailsDto.builder()
          .id(username)
          .email("no-match@example.com")
          .traineeId(TRAINEE_ID_1)
          .userStatus(CONFIRMED.toString())
          .build();
    });

    ArgumentCaptor<AdminListUserAuthEventsRequest> authEventCaptor = ArgumentCaptor.captor();
    when(cognitoService.adminListUserAuthEvents(authEventCaptor.capture()))
        .thenReturn(AdminListUserAuthEventsResponse.builder()
            .authEvents(List.of())
            .nextToken("tokenforpage2")
            .build())
        .thenThrow(TooManyRequestsException.class)
        .thenReturn(AdminListUserAuthEventsResponse.builder().authEvents(List.of()).build());

    service.deleteDuplicateAccounts(TRAINEE_ID_1, Set.of(USER_ID_1), EMAIL);

    List<AdminListUserAuthEventsRequest> requests = authEventCaptor.getAllValues();
    assertThat("Unexpected request count.", requests, hasSize(3));
    AdminListUserAuthEventsRequest request1 = requests.get(0);
    assertThat("Unexpected pagination token.", request1.nextToken(), nullValue());
    AdminListUserAuthEventsRequest request2 = requests.get(1);
    assertThat("Unexpected pagination token.", request2.nextToken(), is("tokenforpage2"));
    AdminListUserAuthEventsRequest request3 = requests.get(2);
    assertThat("Unexpected pagination token.", request3.nextToken(), is("tokenforpage2"));
  }

  @Test
  void shouldEnrollToUserGroup() {
    ArgumentCaptor<AdminAddUserToGroupRequest> requestCaptor = ArgumentCaptor.captor();

    when(cognitoService.adminAddUserToGroup(requestCaptor.capture())).thenReturn(
        AdminAddUserToGroupResponse.builder().build());

    service.enrollToUserGroup(EMAIL, GROUP_1);

    AdminAddUserToGroupRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool.", request.userPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected username.", request.username(), is(EMAIL));
    assertThat("Unexpected user group.", request.groupName(), is(GROUP_1));
  }

  @Test
  void shouldWithdrawFromUserGroup() {
    ArgumentCaptor<AdminRemoveUserFromGroupRequest> requestCaptor = ArgumentCaptor.captor();

    when(cognitoService.adminRemoveUserFromGroup(requestCaptor.capture())).thenReturn(
        AdminRemoveUserFromGroupResponse.builder().build());

    service.withdrawFromUserGroup(EMAIL, GROUP_1);

    AdminRemoveUserFromGroupRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool.", request.userPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected username.", request.username(), is(EMAIL));
    assertThat("Unexpected user group.", request.groupName(), is(GROUP_1));
  }

  @Test
  void shouldReturnNoLoginsWhenUserNotFoundGettingUserLogins() {
    when(cognitoService.adminListUserAuthEvents(any())).thenThrow(UserNotFoundException.class);

    List<UserLoginDetailsDto> userLoginDetails = service.getUserLoginDetails(EMAIL);
    assertThat("Unexpected logins.", userLoginDetails.size(), is(0));
  }

  @Test
  void shouldGetUserLoginsWhenUserFoundGettingUserLogins() {
    Instant eventInstant = Instant.EPOCH;

    ChallengeResponseType challenge1 = ChallengeResponseType.builder()
        .challengeName("Password")
        .challengeResponse("Success")
        .build();
    ChallengeResponseType challenge2 = ChallengeResponseType.builder()
        .challengeName("Mfa")
        .challengeResponse("Failure")
        .build();

    EventContextDataType eventContextDataType = EventContextDataType.builder()
        .deviceName("DEVICE")
        .build();

    AuthEventType authEventTypeOne = AuthEventType.builder()
        .creationDate(eventInstant)
        .eventId("EVENT_ID")
        .eventType("EVENT_TYPE")
        .eventResponse("RESPONSE")
        .challengeResponses(List.of(challenge1, challenge2))
        .eventContextData(eventContextDataType)
        .build();

    Instant eventInstantTwo = Instant.now();

    ChallengeResponseType challenge3 = ChallengeResponseType.builder()
        .challengeName("Password")
        .challengeResponse("Failure")
        .build();

    EventContextDataType eventContextDataTypeTwo = EventContextDataType.builder()
        .deviceName("DEVICE_2")
        .build();

    AuthEventType authEventTypeTwo = AuthEventType.builder()
        .creationDate(eventInstantTwo)
        .eventId("EVENT_ID_2")
        .eventType("EVENT_TYPE_2")
        .eventResponse("RESPONSE_2")
        .challengeResponses(List.of(challenge3))
        .eventContextData(eventContextDataTypeTwo)
        .build();

    AdminListUserAuthEventsResponse result = AdminListUserAuthEventsResponse.builder()
        .authEvents(List.of(authEventTypeOne, authEventTypeTwo))
        .build();

    when(cognitoService.adminListUserAuthEvents(any())).thenReturn(result);

    List<UserLoginDetailsDto> userLogins = service.getUserLoginDetails(EMAIL);

    assertThat("Unexpected logins count.", userLogins.size(), is(2));

    UserLoginDetailsDto loginOne = userLogins.get(0);
    assertThat("Unexpected login id.", loginOne.getEventId(), is("EVENT_ID"));
    assertThat("Unexpected login event.", loginOne.getEvent(), is("EVENT_TYPE"));
    assertThat("Unexpected login date.", loginOne.getEventDate(), is(eventInstant));
    assertThat("Unexpected login result.", loginOne.getResult(), is("RESPONSE"));
    assertThat("Unexpected login device.", loginOne.getDevice(), is("DEVICE"));
    assertThat("Unexpected login challenge.", loginOne.getChallenges(),
        is("Password:Success, Mfa:Failure"));

    UserLoginDetailsDto loginTwo = userLogins.get(1);
    assertThat("Unexpected login id.", loginTwo.getEventId(), is("EVENT_ID_2"));
    assertThat("Unexpected login event.", loginTwo.getEvent(), is("EVENT_TYPE_2"));
    assertThat("Unexpected login date.", loginTwo.getEventDate(), is(eventInstantTwo));
    assertThat("Unexpected login result.", loginTwo.getResult(), is("RESPONSE_2"));
    assertThat("Unexpected login device.", loginTwo.getDevice(), is("DEVICE_2"));
    assertThat("Unexpected login challenge.", loginTwo.getChallenges(),
        is("Password:Failure"));
  }

  @Test
  void shouldRequestUserAccountIdsFromGivenUserPoolWhenGettingUserAccountIds() {
    ListUsersResponse result = ListUsersResponse.builder()
        .users(List.of())
        .build();

    ArgumentCaptor<ListUsersRequest> requestCaptor = ArgumentCaptor.captor();
    when(cognitoService.listUsers(requestCaptor.capture())).thenReturn(result);

    service.getUserAccountIds(TRAINEE_ID_1);

    ListUsersRequest request = requestCaptor.getValue();
    assertThat("Unexpected request user pool.", request.userPoolId(), is(USER_POOL_ID));
  }

  @Test
  void shouldCacheAllUserAccountIdsWhenGettingUserAccountIds() {
    UserType user1 = UserType.builder()
        .attributes(
            AttributeType.builder().name(ATTRIBUTE_TRAINEE_ID).value(TRAINEE_ID_1).build(),
            AttributeType.builder().name(ATTRIBUTE_USER_ID).value(USER_ID_1).build()
        )
        .build();
    UserType user2 = UserType.builder()
        .attributes(
            AttributeType.builder().name(ATTRIBUTE_TRAINEE_ID).value(TRAINEE_ID_2).build(),
            AttributeType.builder().name(ATTRIBUTE_USER_ID).value(USER_ID_2).build()
        )
        .build();

    ListUsersResponse result = ListUsersResponse.builder()
        .users(List.of(user1, user2))
        .build();

    when(cognitoService.listUsers(any())).thenReturn(result);

    when(cache.get(any())).thenReturn(null);

    service.getUserAccountIds(TRAINEE_ID_1);

    verify(cache).put(TRAINEE_ID_1, Set.of(USER_ID_1));
    verify(cache).put(TRAINEE_ID_2, Set.of(USER_ID_2));
  }

  @Test
  void shouldPaginateThroughAllUserAccountIdsWhenGettingUserAccountIds() {
    ListUsersResponse result1 = ListUsersResponse.builder()
        .users(UserType.builder()
            .attributes(
                AttributeType.builder().name(ATTRIBUTE_TRAINEE_ID).value(TRAINEE_ID_1).build(),
                AttributeType.builder().name(ATTRIBUTE_USER_ID).value(USER_ID_1).build())
            .build())
        .paginationToken("tokenforpage2")
        .build();

    ListUsersResponse result2 = ListUsersResponse.builder()
        .users(UserType.builder()
            .attributes(
                AttributeType.builder().name(ATTRIBUTE_TRAINEE_ID).value(TRAINEE_ID_2).build(),
                AttributeType.builder().name(ATTRIBUTE_USER_ID).value(USER_ID_2).build())
            .build())
        .build();

    ArgumentCaptor<ListUsersRequest> requestCaptor = ArgumentCaptor.captor();
    when(cognitoService.listUsers(requestCaptor.capture())).thenReturn(result1, result2);

    when(cache.get(any())).thenReturn(null);

    service.getUserAccountIds(TRAINEE_ID_1);

    verify(cache).put(TRAINEE_ID_1, Set.of(USER_ID_1));
    verify(cache).put(TRAINEE_ID_2, Set.of(USER_ID_2));

    List<ListUsersRequest> requests = requestCaptor.getAllValues();
    assertThat("Unexpected request count.", requests.size(), is(2));
    ListUsersRequest request1 = requests.get(0);
    assertThat("Unexpected pagination token.", request1.paginationToken(), nullValue());
    ListUsersRequest request2 = requests.get(1);
    assertThat("Unexpected pagination token.", request2.paginationToken(), is("tokenforpage2"));
  }

  @Test
  void shouldRetryPaginatingThroughAllUserAccountIdsWhenRateLimitedGettingUserAccountIds() {
    ListUsersResponse result1 = ListUsersResponse.builder()
        .users(UserType.builder()
            .attributes(
                AttributeType.builder().name(ATTRIBUTE_TRAINEE_ID).value(TRAINEE_ID_1).build(),
                AttributeType.builder().name(ATTRIBUTE_USER_ID).value(USER_ID_1).build())
            .build())
        .paginationToken("tokenforpage2")
        .build();

    ListUsersResponse result2 = ListUsersResponse.builder()
        .users(UserType.builder()
            .attributes(
                AttributeType.builder().name(ATTRIBUTE_TRAINEE_ID).value(TRAINEE_ID_2).build(),
                AttributeType.builder().name(ATTRIBUTE_USER_ID).value(USER_ID_2).build())
            .build())
        .build();

    ArgumentCaptor<ListUsersRequest> requestCaptor = ArgumentCaptor.captor();
    when(cognitoService.listUsers(requestCaptor.capture()))
        .thenReturn(result1)
        .thenThrow(TooManyRequestsException.class)
        .thenReturn(result2);

    when(cache.get(any())).thenReturn(null);

    service.getUserAccountIds(TRAINEE_ID_1);

    verify(cache).put(TRAINEE_ID_1, Set.of(USER_ID_1));
    verify(cache).put(TRAINEE_ID_2, Set.of(USER_ID_2));

    List<ListUsersRequest> requests = requestCaptor.getAllValues();
    assertThat("Unexpected request count.", requests.size(), is(3));
    ListUsersRequest request1 = requests.get(0);
    assertThat("Unexpected pagination token.", request1.paginationToken(), nullValue());
    ListUsersRequest request2 = requests.get(1);
    assertThat("Unexpected pagination token.", request2.paginationToken(), is("tokenforpage2"));
    ListUsersRequest request3 = requests.get(2);
    assertThat("Unexpected pagination token.", request3.paginationToken(), is("tokenforpage2"));
  }

  @Test
  void shouldCacheDuplicateUserAccountIdsWhenGettingUserAccountIds() {
    UserType user = UserType.builder()
        .attributes(
            AttributeType.builder().name(ATTRIBUTE_TRAINEE_ID).value(TRAINEE_ID_1).build(),
            AttributeType.builder().name(ATTRIBUTE_USER_ID).value(USER_ID_2).build())
        .build();

    ListUsersResponse result = ListUsersResponse.builder()
        .users(List.of(user))
        .build();

    when(cognitoService.listUsers(any())).thenReturn(result);

    when(cache.get(TRAINEE_ID_1, Set.class)).thenReturn(new HashSet<>(Set.of(USER_ID_1)));

    service.getUserAccountIds(TRAINEE_ID_1);

    verify(cache).put(TRAINEE_ID_1, Set.of(USER_ID_1, USER_ID_2));
  }

  @Test
  void shouldGetUserAccountIdsFromCache() {
    ListUsersResponse result = ListUsersResponse.builder()
        .users(List.of())
        .build();

    when(cognitoService.listUsers(any())).thenReturn(result);

    when(cache.get(TRAINEE_ID_1, Set.class)).thenReturn(Set.of(USER_ID_1, USER_ID_2));

    Set<String> userAccountIds = service.getUserAccountIds(TRAINEE_ID_1);

    assertThat("Unexpected user IDs count.", userAccountIds.size(), is(2));
    assertThat("Unexpected user IDs.", userAccountIds, hasItems(USER_ID_1, USER_ID_2));
  }

  @Test
  void shouldGetEmptyUserAccountIdsWhenAccountNotFoundAfterBuildingCache() {
    ListUsersResponse result = ListUsersResponse.builder()
        .users(List.of())
        .build();

    when(cognitoService.listUsers(any())).thenReturn(result);

    when(cache.get(TRAINEE_ID_1, Set.class)).thenReturn(null);

    Set<String> userAccountIds = service.getUserAccountIds(TRAINEE_ID_1);

    assertThat("Unexpected user IDs count.", userAccountIds.size(), is(0));
  }

  @Test
  void shouldNotImmediatelyRepeatBuildingUserIdCache() {
    ListUsersResponse result = ListUsersResponse.builder()
        .users(List.of())
        .build();

    when(cognitoService.listUsers(any())).thenReturn(result);

    service.getUserAccountIds(TRAINEE_ID_1);
    service.getUserAccountIds(TRAINEE_ID_2);

    verify(cognitoService, times(1)).listUsers(any());
  }
}
