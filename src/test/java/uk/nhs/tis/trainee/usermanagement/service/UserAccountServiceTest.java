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
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListGroupsForUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListGroupsForUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListUserAuthEventsRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListUserAuthEventsResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRemoveUserFromGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRemoveUserFromGroupResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserMfaPreferenceRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserMfaPreferenceResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUpdateUserAttributesRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthEventType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ChallengeResponseType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.EventContextDataType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.TooManyRequestsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserStatusType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;
import uk.nhs.tis.trainee.usermanagement.dto.UserAccountDetailsDto;
import uk.nhs.tis.trainee.usermanagement.dto.UserLoginDetailsDto;

class UserAccountServiceTest {

  private static final String USER_POOL_ID = "region_abc213";
  private static final String USERNAME = "joe.bloggs@fake.email";
  private static final String GROUP_1 = "user-group-one";
  private static final String GROUP_2 = "user-group-two";

  private static final String TRAINEE_ID_1 = UUID.randomUUID().toString();
  private static final String TRAINEE_ID_2 = UUID.randomUUID().toString();
  private static final String USER_ID_1 = UUID.randomUUID().toString();
  private static final String USER_ID_2 = UUID.randomUUID().toString();

  private static final String ATTRIBUTE_TRAINEE_ID = "custom:tisId";
  private static final String ATTRIBUTE_USER_ID = "sub";
  private static final String ATTRIBUTE_EMAIL = "email";
  private static final String ATTRIBUTE_EMAIL_VERIFIED = "email_verified";

  private UserAccountService service;
  private CognitoIdentityProviderClient cognitoClient;
  private Cache cache;
  private EventPublishService eventPublishService;

  @BeforeEach
  void setUp() {
    cognitoClient = mock(CognitoIdentityProviderClient.class);
    cache = mock(Cache.class);

    CacheManager cacheManager = mock(CacheManager.class);
    when(cacheManager.getCache("UserId")).thenReturn(cache);

    eventPublishService = mock(EventPublishService.class);

    service = new UserAccountService(cognitoClient, USER_POOL_ID, cacheManager, eventPublishService);

    // Initialize groups as an empty list instead of null, which reflects default AWS API behaviour.
    AdminListGroupsForUserResponse mockGroupResponse = AdminListGroupsForUserResponse.builder()
        .groups(List.of())
        .build();
    when(cognitoClient.adminListGroupsForUser((AdminListGroupsForUserRequest) any())).thenReturn(
        mockGroupResponse);
  }

  @Test
  void shouldReturnNoAccountDetailsWhenUserNotFoundGettingUser() {
    when(cognitoClient.adminGetUser((AdminGetUserRequest) any())).thenThrow(
        UserNotFoundException.class);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(USERNAME);
    assertThat("Unexpected MFA status.", userAccountDetails.getMfaStatus(), is("NO_ACCOUNT"));
    assertThat("Unexpected user status.", userAccountDetails.getUserStatus(), is("NO_ACCOUNT"));
    assertThat("Unexpected user groups size.", userAccountDetails.getGroups().size(), is(0));
    assertThat("Unexpected account created", userAccountDetails.getAccountCreated(), nullValue());
  }

  @Test
  void shouldReturnPartialAccountDetailsWhenUserNotFoundGettingGroups() {
    when(cognitoClient.adminGetUser((AdminGetUserRequest) any())).thenReturn(
        AdminGetUserResponse.builder().build());
    when(cognitoClient.adminListGroupsForUser((AdminListGroupsForUserRequest) any())).thenThrow(
        UserNotFoundException.class);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(USERNAME);
    assertThat("Unexpected MFA status.", userAccountDetails.getMfaStatus(), not("NO_ACCOUNT"));
    assertThat("Unexpected user status.", userAccountDetails.getUserStatus(), not("NO_ACCOUNT"));
    assertThat("Unexpected user groups size.", userAccountDetails.getGroups().size(), is(0));
    assertThat("Unexpected account created.", userAccountDetails.getAccountCreated(), nullValue());
  }

  @Test
  void shouldSetNullCreatedAtWhenUserCreatedDateIsNull() {
    AdminGetUserResponse response = AdminGetUserResponse.builder()
        .userCreateDate(null)
        .build();

    when(cognitoClient.adminGetUser((AdminGetUserRequest) any())).thenReturn(response);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(USERNAME);
    assertThat("Unexpected account created.", userAccountDetails.getAccountCreated(),
        nullValue());
  }

  @Test
  void shouldSetCreatedAtWhenUserCreatedDateNotNull() {
    Instant createdAt = Instant.now();
    AdminGetUserResponse response = AdminGetUserResponse.builder()
        .userCreateDate(createdAt)
        .build();

    when(cognitoClient.adminGetUser((AdminGetUserRequest) any())).thenReturn(response);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(USERNAME);
    assertThat("Unexpected account created.", userAccountDetails.getAccountCreated(),
        is(createdAt));
  }

  @Test
  void shouldGetUserStatusWhenUserStatusConfirmed() {
    AdminGetUserResponse response = AdminGetUserResponse.builder()
        .userStatus(UserStatusType.CONFIRMED)
        .build();

    when(cognitoClient.adminGetUser((AdminGetUserRequest) any())).thenReturn(response);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(USERNAME);
    assertThat("Unexpected user status.", userAccountDetails.getUserStatus(),
        is(UserStatusType.CONFIRMED.toString()));
  }

  @Test
  void shouldGetUserStatusWhenUserStatusUnconfirmed() {
    AdminGetUserResponse response = AdminGetUserResponse.builder()
        .userStatus(UserStatusType.UNCONFIRMED)
        .build();

    when(cognitoClient.adminGetUser((AdminGetUserRequest) any())).thenReturn(response);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(USERNAME);
    assertThat("Unexpected user status.", userAccountDetails.getUserStatus(),
        is(UserStatusType.UNCONFIRMED.toString()));
  }


  @Test
  void shouldGetUserStatusWhenUserStatusForcedPasswordChange() {
    AdminGetUserResponse response = AdminGetUserResponse.builder()
        .userStatus(UserStatusType.FORCE_CHANGE_PASSWORD)
        .build();

    when(cognitoClient.adminGetUser((AdminGetUserRequest) any())).thenReturn(response);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(USERNAME);
    assertThat("Unexpected user status.", userAccountDetails.getUserStatus(),
        is(UserStatusType.FORCE_CHANGE_PASSWORD.toString()));
  }

  @Test
  void shouldReturnNoMfaWhenNoPreferredMfa() {
    AdminGetUserResponse response = AdminGetUserResponse.builder().build();

    when(cognitoClient.adminGetUser((AdminGetUserRequest) any())).thenReturn(response);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(USERNAME);
    assertThat("Unexpected MFA status.", userAccountDetails.getMfaStatus(), is("NO_MFA"));
  }

  @Test
  void shouldReturnPreferredMfaWhenPreferredMfa() {
    AdminGetUserResponse response = AdminGetUserResponse.builder()
        .preferredMfaSetting("PREFERRED_MFA")
        .build();

    when(cognitoClient.adminGetUser((AdminGetUserRequest) any())).thenReturn(response);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(USERNAME);
    assertThat("Unexpected MFA status.", userAccountDetails.getMfaStatus(), is("PREFERRED_MFA"));
  }

  @Test
  void shouldReturnNoGroupsWhenUserNotFoundRetrievingGroups() {
    when(cognitoClient.adminGetUser((AdminGetUserRequest) any())).thenReturn(
        AdminGetUserResponse.builder().build());
    when(cognitoClient.adminListGroupsForUser((AdminListGroupsForUserRequest) any())).thenThrow(
        UserNotFoundException.class);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(USERNAME);
    List<String> groups = userAccountDetails.getGroups();
    assertThat("Unexpected user groups.", groups, notNullValue());
    assertThat("Unexpected user groups.", groups.size(), is(0));
  }

  @Test
  void shouldReturnNoGroupsWhenUserHasNoGroups() {
    AdminListGroupsForUserResponse response = AdminListGroupsForUserResponse.builder()
        .groups(List.of())
        .build();

    when(cognitoClient.adminGetUser((AdminGetUserRequest) any())).thenReturn(
        AdminGetUserResponse.builder().build());
    when(cognitoClient.adminListGroupsForUser((AdminListGroupsForUserRequest) any())).thenReturn(
        response);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(USERNAME);
    List<String> groups = userAccountDetails.getGroups();
    assertThat("Unexpected user groups.", groups, notNullValue());
    assertThat("Unexpected user groups.", groups.size(), is(0));
  }

  @Test
  void shouldReturnGroupsWhenUserHasGroups() {
    AdminListGroupsForUserResponse response = AdminListGroupsForUserResponse.builder()
        .groups(
            g1 -> g1.groupName(GROUP_1),
            g2 -> g2.groupName(GROUP_2))
        .build();

    when(cognitoClient.adminGetUser((AdminGetUserRequest) any())).thenReturn(
        AdminGetUserResponse.builder().build());
    when(cognitoClient.adminListGroupsForUser((AdminListGroupsForUserRequest) any())).thenReturn(
        response);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(USERNAME);
    List<String> groups = userAccountDetails.getGroups();
    assertThat("Unexpected user groups.", groups, notNullValue());
    assertThat("Unexpected user groups.", groups.size(), is(2));
    assertThat("Unexpected user groups.", groups, hasItems(GROUP_1, GROUP_2));
  }

  @Test
  void shouldThrowExceptionUpdatingEmailWhenEmailBelongsToAnotherAccount() {
    String newEmail = "new.email@example.com";

    AdminGetUserResponse getUserResponse = AdminGetUserResponse.builder()
        .username(USER_ID_2)
        .build();

    when(cognitoClient.adminGetUser((AdminGetUserRequest) any())).thenReturn(getUserResponse);

    assertThrows(IllegalArgumentException.class, () -> service.updateEmail(USER_ID_1, newEmail));

    verify(cognitoClient, never()).adminUpdateUserAttributes(
        (AdminUpdateUserAttributesRequest) any());
  }

  @Test
  void shouldNotUpdateEmailWhenTheEmailHasNotChanged() {
    String newEmail = "new.email@example.com";

    AdminGetUserResponse getUserResponse = AdminGetUserResponse.builder()
        .username(USER_ID_1)
        .build();

    when(cognitoClient.adminGetUser((AdminGetUserRequest) any())).thenReturn(getUserResponse);

    service.updateEmail(USER_ID_1, newEmail);

    verify(cognitoClient, never()).adminUpdateUserAttributes(
        (AdminUpdateUserAttributesRequest) any());
  }

  @Test
  void shouldUpdateEmailWhenNewEmailNotExistsInUserPool() {
    String newEmail = "new.email@example.com";

    ArgumentCaptor<AdminGetUserRequest> getRequestCaptor = ArgumentCaptor.captor();
    when(cognitoClient.adminGetUser(getRequestCaptor.capture()))
        .thenThrow(UserNotFoundException.class)
        .thenReturn(AdminGetUserResponse.builder()
            .userAttributes(at -> at.name("name").value("value"))
            .build()
        );

    service.updateEmail(USER_ID_1, newEmail);

    List<AdminGetUserRequest> getRequests = getRequestCaptor.getAllValues();
    assertThat("Unexpected get request count.", getRequests.size(), is(2));

    AdminGetUserRequest getRequest = getRequests.get(0);
    assertThat("Unexpected username.", getRequest.username(), is(newEmail));

    ArgumentCaptor<AdminUpdateUserAttributesRequest> updateRequestCaptor = ArgumentCaptor.captor();
    verify(cognitoClient).adminUpdateUserAttributes(updateRequestCaptor.capture());

    AdminUpdateUserAttributesRequest updateRequest = updateRequestCaptor.getValue();

    assertThat("Unexpected user pool.", updateRequest.userPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected user ID.", updateRequest.username(), is(USER_ID_1));

    List<AttributeType> userAttributes = updateRequest.userAttributes();
    assertThat("Unexpected attribute count.", userAttributes.size(), is(2));

    AttributeType userAttribute = userAttributes.get(0);
    assertThat("Unexpected attribute name.", userAttribute.name(), is(ATTRIBUTE_EMAIL));
    assertThat("Unexpected attribute value.", userAttribute.value(), is(newEmail));

    userAttribute = userAttributes.get(1);
    assertThat("Unexpected attribute name.", userAttribute.name(), is(ATTRIBUTE_EMAIL_VERIFIED));
    assertThat("Unexpected attribute value.", userAttribute.value(), is("true"));
  }

  @Test
  void shouldPublishEventAfterUpdatingEmail() {
    String previousEmail = "previous.email@example.com";
    String newEmail = "new.email@example.com";

    ArgumentCaptor<AdminGetUserRequest> getRequestCaptor = ArgumentCaptor.forClass(
        AdminGetUserRequest.class);
    when(cognitoClient.adminGetUser(getRequestCaptor.capture()))
        .thenThrow(UserNotFoundException.class)
        .thenReturn(AdminGetUserResponse.builder()
            .userAttributes(
                at1 -> at1.name(ATTRIBUTE_EMAIL).value(previousEmail),
                at2 -> at2.name(ATTRIBUTE_TRAINEE_ID).value(TRAINEE_ID_1)
            )
            .build()
        );

    service.updateEmail(USER_ID_1, newEmail);

    List<AdminGetUserRequest> getRequests = getRequestCaptor.getAllValues();
    assertThat("Unexpected get request count.", getRequests.size(), is(2));

    AdminGetUserRequest getRequest = getRequests.get(1);
    assertThat("Unexpected username.", getRequest.username(), is(USER_ID_1));

    verify(eventPublishService).publishEmailUpdateEvent(USER_ID_1, TRAINEE_ID_1, previousEmail,
        newEmail);
  }

  @Test
  void shouldResetSmsMfa() {
    ArgumentCaptor<AdminSetUserMfaPreferenceRequest> requestCaptor = ArgumentCaptor.captor();

    when(cognitoClient.adminSetUserMFAPreference(requestCaptor.capture())).thenReturn(
        AdminSetUserMfaPreferenceResponse.builder().build());

    service.resetUserAccountMfa(USERNAME);

    AdminSetUserMfaPreferenceRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool.", request.userPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected username.", request.username(), is(USERNAME));
    assertThat("Unexpected SMS enabled flag.", request.smsMfaSettings().enabled(), is(false));
  }

  @Test
  void shouldResetTotpMfa() {
    ArgumentCaptor<AdminSetUserMfaPreferenceRequest> requestCaptor = ArgumentCaptor.captor();

    when(cognitoClient.adminSetUserMFAPreference(requestCaptor.capture())).thenReturn(
        AdminSetUserMfaPreferenceResponse.builder().build());

    service.resetUserAccountMfa(USERNAME);

    AdminSetUserMfaPreferenceRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool.", request.userPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected username.", request.username(), is(USERNAME));
    assertThat("Unexpected TOTP enabled flag.", request.softwareTokenMfaSettings().enabled(),
        is(false));
  }

  @Test
  void shouldDeleteCognitoAccount() {
    ArgumentCaptor<AdminDeleteUserRequest> requestCaptor = ArgumentCaptor.forClass(
        AdminDeleteUserRequest.class);

    when(cognitoClient.adminDeleteUser(requestCaptor.capture())).thenReturn(
        AdminDeleteUserResponse.builder().build());

    service.deleteCognitoAccount(USERNAME);

    AdminDeleteUserRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool.", request.userPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected delete account username.", request.username(), is(USERNAME));
  }

  @Test
  void shouldEnrollToUserGroup() {
    ArgumentCaptor<AdminAddUserToGroupRequest> requestCaptor = ArgumentCaptor.forClass(
        AdminAddUserToGroupRequest.class);

    when(cognitoClient.adminAddUserToGroup(requestCaptor.capture())).thenReturn(
        AdminAddUserToGroupResponse.builder().build());

    service.enrollToUserGroup(USERNAME, GROUP_1);

    AdminAddUserToGroupRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool.", request.userPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected username.", request.username(), is(USERNAME));
    assertThat("Unexpected user group.", request.groupName(), is(GROUP_1));
  }

  @Test
  void shouldWithdrawFromUserGroup() {
    ArgumentCaptor<AdminRemoveUserFromGroupRequest> requestCaptor = ArgumentCaptor.forClass(
        AdminRemoveUserFromGroupRequest.class);

    when(cognitoClient.adminRemoveUserFromGroup(requestCaptor.capture())).thenReturn(
        AdminRemoveUserFromGroupResponse.builder().build());

    service.withdrawFromUserGroup(USERNAME, GROUP_1);

    AdminRemoveUserFromGroupRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool.", request.userPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected username.", request.username(), is(USERNAME));
    assertThat("Unexpected user group.", request.groupName(), is(GROUP_1));
  }

  @Test
  void shouldReturnNoLoginsWhenUserNotFoundGettingUserLogins() {
    when(cognitoClient.adminListUserAuthEvents((AdminListUserAuthEventsRequest) any())).thenThrow(
        UserNotFoundException.class);

    List<UserLoginDetailsDto> userLoginDetails = service.getUserLoginDetails(USERNAME);
    assertThat("Unexpected logins.", userLoginDetails.size(), is(0));
  }

  @Test
  void shouldGetUserLoginsWhenUserFoundGettingUserLogins() {
    Date eventDate = new Date();

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
        .creationDate(eventDate.toInstant())
        .eventId("EVENT_ID")
        .eventType("EVENT_TYPE")
        .eventResponse("RESPONSE")
        .challengeResponses(List.of(challenge1, challenge2))
        .eventContextData(eventContextDataType)
        .build();

    Date eventDateTwo = new Date();

    ChallengeResponseType challenge3 = ChallengeResponseType.builder()
        .challengeName("Password")
        .challengeResponse("Failure")
        .build();

    AuthEventType authEventTypeTwo = AuthEventType.builder()
        .creationDate(eventDateTwo.toInstant())
        .eventId("EVENT_ID_2")
        .eventType("EVENT_TYPE_2")
        .eventResponse("RESPONSE_2")
        .challengeResponses(List.of(challenge3))
        .eventContextData(ctx -> ctx.deviceName("DEVICE_2"))
        .build();

    AdminListUserAuthEventsResponse response = AdminListUserAuthEventsResponse.builder()
        .authEvents(List.of(authEventTypeOne, authEventTypeTwo))
        .build();

    when(cognitoClient.adminListUserAuthEvents((AdminListUserAuthEventsRequest) any())).thenReturn(
        response);

    List<UserLoginDetailsDto> userLogins = service.getUserLoginDetails(USERNAME);

    assertThat("Unexpected logins count.", userLogins.size(), is(2));

    UserLoginDetailsDto loginOne = userLogins.get(0);
    assertThat("Unexpected login id.", loginOne.getEventId(), is("EVENT_ID"));
    assertThat("Unexpected login event.", loginOne.getEvent(), is("EVENT_TYPE"));
    assertThat("Unexpected login date.", loginOne.getEventDate(), is(eventDate));
    assertThat("Unexpected login result.", loginOne.getResult(), is("RESPONSE"));
    assertThat("Unexpected login device.", loginOne.getDevice(), is("DEVICE"));
    assertThat("Unexpected login challenge.", loginOne.getChallenges(),
        is("Password:Success, Mfa:Failure"));

    UserLoginDetailsDto loginTwo = userLogins.get(1);
    assertThat("Unexpected login id.", loginTwo.getEventId(), is("EVENT_ID_2"));
    assertThat("Unexpected login event.", loginTwo.getEvent(), is("EVENT_TYPE_2"));
    assertThat("Unexpected login date.", loginTwo.getEventDate(), is(eventDateTwo));
    assertThat("Unexpected login result.", loginTwo.getResult(), is("RESPONSE_2"));
    assertThat("Unexpected login device.", loginTwo.getDevice(), is("DEVICE_2"));
    assertThat("Unexpected login challenge.", loginTwo.getChallenges(),
        is("Password:Failure"));
  }

  @Test
  void shouldRequestUserAccountIdsFromGivenUserPoolWhenGettingUserAccountIds() {
    ListUsersResponse response = ListUsersResponse.builder()
        .users(List.of())
        .build();

    ArgumentCaptor<ListUsersRequest> requestCaptor = ArgumentCaptor.forClass(
        ListUsersRequest.class);
    when(cognitoClient.listUsers(requestCaptor.capture())).thenReturn(response);

    service.getUserAccountIds(TRAINEE_ID_1);

    ListUsersRequest request = requestCaptor.getValue();
    assertThat("Unexpected request user pool.", request.userPoolId(), is(USER_POOL_ID));
  }

  @Test
  void shouldCacheAllUserAccountIdsWhenGettingUserAccountIds() {
    UserType user1 = UserType.builder()
        .attributes(
            at1 -> at1.name(ATTRIBUTE_TRAINEE_ID).value(TRAINEE_ID_1),
            at2 -> at2.name(ATTRIBUTE_USER_ID).value(USER_ID_1)
        )
        .build();
    UserType user2 = UserType.builder()
        .attributes(
            at1 -> at1.name(ATTRIBUTE_TRAINEE_ID).value(TRAINEE_ID_2),
            at2 -> at2.name(ATTRIBUTE_USER_ID).value(USER_ID_2)
        )
        .build();

    ListUsersResponse response = ListUsersResponse.builder()
        .users(List.of(user1, user2))
        .build();

    when(cognitoClient.listUsers((ListUsersRequest) any())).thenReturn(response);

    when(cache.get(any())).thenReturn(null);

    service.getUserAccountIds(TRAINEE_ID_1);

    verify(cache).put(TRAINEE_ID_1, Set.of(USER_ID_1));
    verify(cache).put(TRAINEE_ID_2, Set.of(USER_ID_2));
  }

  @Test
  void shouldPaginateThroughAllUserAccountIdsWhenGettingUserAccountIds() {
    ListUsersResponse response1 = ListUsersResponse.builder()
        .users(List.of(
            UserType.builder().attributes(
                at1 -> at1.name(ATTRIBUTE_TRAINEE_ID).value(TRAINEE_ID_1),
                at2 -> at2.name(ATTRIBUTE_USER_ID).value(USER_ID_1)
            ).build()
        ))
        .paginationToken("tokenforpage2")
        .build();

    ListUsersResponse response2 = ListUsersResponse.builder()
        .users(List.of(
            UserType.builder().attributes(
                at1 -> at1.name(ATTRIBUTE_TRAINEE_ID).value(TRAINEE_ID_2),
                at2 -> at2.name(ATTRIBUTE_USER_ID).value(USER_ID_2)
            ).build()
        ))
        .build();

    ArgumentCaptor<ListUsersRequest> requestCaptor = ArgumentCaptor.forClass(
        ListUsersRequest.class);
    when(cognitoClient.listUsers(requestCaptor.capture())).thenReturn(response1, response2);

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
    ListUsersResponse response1 = ListUsersResponse.builder()
        .users(List.of(
            UserType.builder().attributes(
                at1 -> at1.name(ATTRIBUTE_TRAINEE_ID).value(TRAINEE_ID_1),
                at2 -> at2.name(ATTRIBUTE_USER_ID).value(USER_ID_1)
            ).build()
        ))
        .paginationToken("tokenforpage2")
        .build();

    ListUsersResponse response2 = ListUsersResponse.builder()
        .users(List.of(
            UserType.builder().attributes(
                at1 -> at1.name(ATTRIBUTE_TRAINEE_ID).value(TRAINEE_ID_2),
                at2 -> at2.name(ATTRIBUTE_USER_ID).value(USER_ID_2)
            ).build()
        ))
        .build();

    ArgumentCaptor<ListUsersRequest> requestCaptor = ArgumentCaptor.forClass(
        ListUsersRequest.class);
    when(cognitoClient.listUsers(requestCaptor.capture()))
        .thenReturn(response1)
        .thenThrow(TooManyRequestsException.class)
        .thenReturn(response2);

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
            at1 -> at1.name(ATTRIBUTE_TRAINEE_ID).value(TRAINEE_ID_1),
            at2 -> at2.name(ATTRIBUTE_USER_ID).value(USER_ID_2)
        )
        .build();

    ListUsersResponse response = ListUsersResponse.builder()
        .users(List.of(user))
        .build();

    when(cognitoClient.listUsers((ListUsersRequest) any())).thenReturn(response);

    when(cache.get(TRAINEE_ID_1, Set.class)).thenReturn(new HashSet<>(Set.of(USER_ID_1)));

    service.getUserAccountIds(TRAINEE_ID_1);

    verify(cache).put(TRAINEE_ID_1, Set.of(USER_ID_1, USER_ID_2));
  }

  @Test
  void shouldGetUserAccountIdsFromCache() {
    ListUsersResponse response = ListUsersResponse.builder()
        .users(List.of())
        .build();

    when(cognitoClient.listUsers((ListUsersRequest) any())).thenReturn(response);

    when(cache.get(TRAINEE_ID_1, Set.class)).thenReturn(Set.of(USER_ID_1, USER_ID_2));

    Set<String> userAccountIds = service.getUserAccountIds(TRAINEE_ID_1);

    assertThat("Unexpected user IDs count.", userAccountIds.size(), is(2));
    assertThat("Unexpected user IDs.", userAccountIds, hasItems(USER_ID_1, USER_ID_2));
  }

  @Test
  void shouldGetEmptyUserAccountIdsWhenAccountNotFoundAfterBuildingCache() {
    ListUsersResponse response = ListUsersResponse.builder()
        .users(List.of())
        .build();

    when(cognitoClient.listUsers((ListUsersRequest) any())).thenReturn(response);

    when(cache.get(TRAINEE_ID_1, Set.class)).thenReturn(null);

    Set<String> userAccountIds = service.getUserAccountIds(TRAINEE_ID_1);

    assertThat("Unexpected user IDs count.", userAccountIds.size(), is(0));
  }

  @Test
  void shouldNotImmediatelyRepeatBuildingUserIdCache() {
    ListUsersResponse response = ListUsersResponse.builder()
        .users(List.of())
        .build();

    when(cognitoClient.listUsers((ListUsersRequest) any())).thenReturn(response);

    service.getUserAccountIds(TRAINEE_ID_1);
    service.getUserAccountIds(TRAINEE_ID_2);

    verify(cognitoClient, times(1)).listUsers((ListUsersRequest) any());
  }
}
