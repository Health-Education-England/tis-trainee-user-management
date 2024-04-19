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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.model.AdminAddUserToGroupRequest;
import com.amazonaws.services.cognitoidp.model.AdminAddUserToGroupResult;
import com.amazonaws.services.cognitoidp.model.AdminDeleteUserRequest;
import com.amazonaws.services.cognitoidp.model.AdminDeleteUserResult;
import com.amazonaws.services.cognitoidp.model.AdminGetUserResult;
import com.amazonaws.services.cognitoidp.model.AdminListGroupsForUserResult;
import com.amazonaws.services.cognitoidp.model.AdminListUserAuthEventsResult;
import com.amazonaws.services.cognitoidp.model.AdminRemoveUserFromGroupRequest;
import com.amazonaws.services.cognitoidp.model.AdminRemoveUserFromGroupResult;
import com.amazonaws.services.cognitoidp.model.AdminSetUserMFAPreferenceRequest;
import com.amazonaws.services.cognitoidp.model.AdminSetUserMFAPreferenceResult;
import com.amazonaws.services.cognitoidp.model.AdminUpdateUserAttributesRequest;
import com.amazonaws.services.cognitoidp.model.AttributeType;
import com.amazonaws.services.cognitoidp.model.AuthEventType;
import com.amazonaws.services.cognitoidp.model.ChallengeResponseType;
import com.amazonaws.services.cognitoidp.model.EventContextDataType;
import com.amazonaws.services.cognitoidp.model.GroupType;
import com.amazonaws.services.cognitoidp.model.ListUsersRequest;
import com.amazonaws.services.cognitoidp.model.ListUsersResult;
import com.amazonaws.services.cognitoidp.model.UserNotFoundException;
import com.amazonaws.services.cognitoidp.model.UserStatusType;
import com.amazonaws.services.cognitoidp.model.UserType;
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

  private UserAccountService service;
  private AWSCognitoIdentityProvider cognitoIdp;
  private Cache cache;

  @BeforeEach
  void setUp() {
    cognitoIdp = mock(AWSCognitoIdentityProvider.class);
    cache = mock(Cache.class);

    CacheManager cacheManager = mock(CacheManager.class);
    when(cacheManager.getCache("UserId")).thenReturn(cache);

    service = new UserAccountService(cognitoIdp, USER_POOL_ID, cacheManager);

    // Initialize groups as an empty list instead of null, which reflects default AWS API behaviour.
    AdminListGroupsForUserResult mockGroupResult = new AdminListGroupsForUserResult();
    mockGroupResult.setGroups(List.of());
    when(cognitoIdp.adminListGroupsForUser(any())).thenReturn(mockGroupResult);
  }

  @Test
  void shouldReturnNoAccountDetailsWhenUserNotFoundGettingUser() {
    when(cognitoIdp.adminGetUser(any())).thenThrow(UserNotFoundException.class);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(USERNAME);
    assertThat("Unexpected MFA status.", userAccountDetails.getMfaStatus(), is("NO_ACCOUNT"));
    assertThat("Unexpected user status.", userAccountDetails.getUserStatus(), is("NO_ACCOUNT"));
    assertThat("Unexpected user groups size.", userAccountDetails.getGroups().size(), is(0));
    assertThat("Unexpected account created", userAccountDetails.getAccountCreated(), nullValue());
  }

  @Test
  void shouldReturnPartialAccountDetailsWhenUserNotFoundGettingGroups() {
    when(cognitoIdp.adminGetUser(any())).thenReturn(new AdminGetUserResult());
    when(cognitoIdp.adminListGroupsForUser(any())).thenThrow(UserNotFoundException.class);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(USERNAME);
    assertThat("Unexpected MFA status.", userAccountDetails.getMfaStatus(), not("NO_ACCOUNT"));
    assertThat("Unexpected user status.", userAccountDetails.getUserStatus(), not("NO_ACCOUNT"));
    assertThat("Unexpected user groups size.", userAccountDetails.getGroups().size(), is(0));
    assertThat("Unexpected account created.", userAccountDetails.getAccountCreated(), nullValue());
  }

  @Test
  void shouldSetNullCreatedAtWhenUserCreatedDateIsNull() {
    AdminGetUserResult result = new AdminGetUserResult();
    result.setUserCreateDate(null);

    when(cognitoIdp.adminGetUser(any())).thenReturn(result);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(USERNAME);
    assertThat("Unexpected account created.", userAccountDetails.getAccountCreated(),
        nullValue());
  }

  @Test
  void shouldSetCreatedAtWhenUserCreatedDateNotNull() {
    AdminGetUserResult result = new AdminGetUserResult();
    Instant createdAt = Instant.now();
    Instant createdAtWithDatePrecision = Date.from(createdAt).toInstant();
    result.setUserCreateDate(Date.from(createdAt));

    when(cognitoIdp.adminGetUser(any())).thenReturn(result);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(USERNAME);
    assertThat("Unexpected account created.", userAccountDetails.getAccountCreated(),
        is(createdAtWithDatePrecision));
  }

  @Test
  void shouldGetUserStatusWhenUserStatusConfirmed() {
    AdminGetUserResult result = new AdminGetUserResult();
    result.setUserStatus(UserStatusType.CONFIRMED);

    when(cognitoIdp.adminGetUser(any())).thenReturn(result);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(USERNAME);
    assertThat("Unexpected user status.", userAccountDetails.getUserStatus(),
        is(UserStatusType.CONFIRMED.toString()));
  }

  @Test
  void shouldGetUserStatusWhenUserStatusUnconfirmed() {
    AdminGetUserResult result = new AdminGetUserResult();
    result.setUserStatus(UserStatusType.UNCONFIRMED);

    when(cognitoIdp.adminGetUser(any())).thenReturn(result);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(USERNAME);
    assertThat("Unexpected user status.", userAccountDetails.getUserStatus(),
        is(UserStatusType.UNCONFIRMED.toString()));
  }


  @Test
  void shouldGetUserStatusWhenUserStatusForcedPasswordChange() {
    AdminGetUserResult result = new AdminGetUserResult();
    result.setUserStatus(UserStatusType.FORCE_CHANGE_PASSWORD);

    when(cognitoIdp.adminGetUser(any())).thenReturn(result);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(USERNAME);
    assertThat("Unexpected user status.", userAccountDetails.getUserStatus(),
        is(UserStatusType.FORCE_CHANGE_PASSWORD.toString()));
  }

  @Test
  void shouldReturnNoMfaWhenNoPreferredMfa() {
    AdminGetUserResult result = new AdminGetUserResult();

    when(cognitoIdp.adminGetUser(any())).thenReturn(result);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(USERNAME);
    assertThat("Unexpected MFA status.", userAccountDetails.getMfaStatus(), is("NO_MFA"));
  }

  @Test
  void shouldReturnPreferredMfaWhenPreferredMfa() {
    AdminGetUserResult result = new AdminGetUserResult();
    result.setPreferredMfaSetting("PREFERRED_MFA");

    when(cognitoIdp.adminGetUser(any())).thenReturn(result);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(USERNAME);
    assertThat("Unexpected MFA status.", userAccountDetails.getMfaStatus(), is("PREFERRED_MFA"));
  }

  @Test
  void shouldReturnNoGroupsWhenUserNotFoundRetrievingGroups() {
    when(cognitoIdp.adminGetUser(any())).thenReturn(new AdminGetUserResult());
    when(cognitoIdp.adminListGroupsForUser(any())).thenThrow(UserNotFoundException.class);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(USERNAME);
    List<String> groups = userAccountDetails.getGroups();
    assertThat("Unexpected user groups.", groups, notNullValue());
    assertThat("Unexpected user groups.", groups.size(), is(0));
  }

  @Test
  void shouldReturnNoGroupsWhenUserHasNoGroups() {
    AdminListGroupsForUserResult result = new AdminListGroupsForUserResult();
    result.setGroups(List.of());

    when(cognitoIdp.adminGetUser(any())).thenReturn(new AdminGetUserResult());
    when(cognitoIdp.adminListGroupsForUser(any())).thenReturn(result);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(USERNAME);
    List<String> groups = userAccountDetails.getGroups();
    assertThat("Unexpected user groups.", groups, notNullValue());
    assertThat("Unexpected user groups.", groups.size(), is(0));
  }

  @Test
  void shouldReturnGroupsWhenUserHasGroups() {
    AdminListGroupsForUserResult result = new AdminListGroupsForUserResult();
    result.withGroups(
        new GroupType().withGroupName(GROUP_1),
        new GroupType().withGroupName(GROUP_2));

    when(cognitoIdp.adminGetUser(any())).thenReturn(new AdminGetUserResult());
    when(cognitoIdp.adminListGroupsForUser(any())).thenReturn(result);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(USERNAME);
    List<String> groups = userAccountDetails.getGroups();
    assertThat("Unexpected user groups.", groups, notNullValue());
    assertThat("Unexpected user groups.", groups.size(), is(2));
    assertThat("Unexpected user groups.", groups, hasItems(GROUP_1, GROUP_2));
  }

  @Test
  void shouldThrowExceptionUpdatingEmailWhenEmailBelongsToAnotherAccount() {
    String newEmail = "new.email@example.com";

    AdminGetUserResult getUserResult = new AdminGetUserResult();
    getUserResult.setUsername(USER_ID_2);

    when(cognitoIdp.adminGetUser(any())).thenReturn(getUserResult);

    assertThrows(IllegalArgumentException.class, () -> service.updateEmail(USER_ID_1, newEmail));

    verify(cognitoIdp, never()).adminUpdateUserAttributes(any());
  }

  @Test
  void shouldNotUpdateEmailWhenTheEmailHasNotChanged() {
    String newEmail = "new.email@example.com";

    AdminGetUserResult getUserResult = new AdminGetUserResult();
    getUserResult.setUsername(USER_ID_1);

    when(cognitoIdp.adminGetUser(any())).thenReturn(getUserResult);

    service.updateEmail(USER_ID_1, newEmail);

    verify(cognitoIdp, never()).adminUpdateUserAttributes(any());
  }

  @Test
  void shouldUpdateEmailWhenNewEmailNotExistsInUserPool() {
    String newEmail = "new.email@example.com";

    when(cognitoIdp.adminGetUser(any())).thenThrow(UserNotFoundException.class);

    service.updateEmail(USER_ID_1, newEmail);

    ArgumentCaptor<AdminUpdateUserAttributesRequest> requestCaptor = ArgumentCaptor.forClass(
        AdminUpdateUserAttributesRequest.class);
    verify(cognitoIdp).adminUpdateUserAttributes(requestCaptor.capture());

    AdminUpdateUserAttributesRequest request = requestCaptor.getValue();

    assertThat("Unexpected user pool.", request.getUserPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected user ID.", request.getUsername(), is(USER_ID_1));

    List<AttributeType> userAttributes = request.getUserAttributes();
    assertThat("Unexpected attribute count.", userAttributes.size(), is(2));

    AttributeType userAttribute = userAttributes.get(0);
    assertThat("Unexpected attribute name.", userAttribute.getName(), is("email"));
    assertThat("Unexpected attribute value.", userAttribute.getValue(), is(newEmail));

    userAttribute = userAttributes.get(1);
    assertThat("Unexpected attribute name.", userAttribute.getName(), is("email_verified"));
    assertThat("Unexpected attribute value.", userAttribute.getValue(), is("true"));
  }

  @Test
  void shouldResetSmsMfa() {
    ArgumentCaptor<AdminSetUserMFAPreferenceRequest> requestCaptor = ArgumentCaptor.forClass(
        AdminSetUserMFAPreferenceRequest.class);

    when(cognitoIdp.adminSetUserMFAPreference(requestCaptor.capture())).thenReturn(
        new AdminSetUserMFAPreferenceResult());

    service.resetUserAccountMfa(USERNAME);

    AdminSetUserMFAPreferenceRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool.", request.getUserPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected username.", request.getUsername(), is(USERNAME));
    assertThat("Unexpected SMS enabled flag.", request.getSMSMfaSettings().getEnabled(), is(false));
  }

  @Test
  void shouldResetTotpMfa() {
    ArgumentCaptor<AdminSetUserMFAPreferenceRequest> requestCaptor = ArgumentCaptor.forClass(
        AdminSetUserMFAPreferenceRequest.class);

    when(cognitoIdp.adminSetUserMFAPreference(requestCaptor.capture())).thenReturn(
        new AdminSetUserMFAPreferenceResult());

    service.resetUserAccountMfa(USERNAME);

    AdminSetUserMFAPreferenceRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool.", request.getUserPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected username.", request.getUsername(), is(USERNAME));
    assertThat("Unexpected TOTP enabled flag.", request.getSoftwareTokenMfaSettings().getEnabled(),
        is(false));
  }

  @Test
  void shouldDeleteCognitoAccount() {
    ArgumentCaptor<AdminDeleteUserRequest> requestCaptor = ArgumentCaptor.forClass(
        AdminDeleteUserRequest.class);

    when(cognitoIdp.adminDeleteUser(requestCaptor.capture())).thenReturn(
        new AdminDeleteUserResult());

    service.deleteCognitoAccount(USERNAME);

    AdminDeleteUserRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool.", request.getUserPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected delete account username.", request.getUsername(), is(USERNAME));
  }

  @Test
  void shouldEnrollToUserGroup() {
    ArgumentCaptor<AdminAddUserToGroupRequest> requestCaptor = ArgumentCaptor.forClass(
        AdminAddUserToGroupRequest.class);

    when(cognitoIdp.adminAddUserToGroup(requestCaptor.capture())).thenReturn(
        new AdminAddUserToGroupResult());

    service.enrollToUserGroup(USERNAME, GROUP_1);

    AdminAddUserToGroupRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool.", request.getUserPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected username.", request.getUsername(), is(USERNAME));
    assertThat("Unexpected user group.", request.getGroupName(), is(GROUP_1));
  }

  @Test
  void shouldWithdrawFromUserGroup() {
    ArgumentCaptor<AdminRemoveUserFromGroupRequest> requestCaptor = ArgumentCaptor.forClass(
        AdminRemoveUserFromGroupRequest.class);

    when(cognitoIdp.adminRemoveUserFromGroup(requestCaptor.capture())).thenReturn(
        new AdminRemoveUserFromGroupResult());

    service.withdrawFromUserGroup(USERNAME, GROUP_1);

    AdminRemoveUserFromGroupRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool.", request.getUserPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected username.", request.getUsername(), is(USERNAME));
    assertThat("Unexpected user group.", request.getGroupName(), is(GROUP_1));
  }

  @Test
  void shouldReturnNoLoginsWhenUserNotFoundGettingUserLogins() {
    when(cognitoIdp.adminListUserAuthEvents(any())).thenThrow(UserNotFoundException.class);

    List<UserLoginDetailsDto> userLoginDetails = service.getUserLoginDetails(USERNAME);
    assertThat("Unexpected logins.", userLoginDetails.size(), is(0));
  }

  @Test
  void shouldGetUserLoginsWhenUserFoundGettingUserLogins() {
    Date eventDate = new Date();

    AuthEventType authEventTypeOne = new AuthEventType();
    authEventTypeOne.setCreationDate(eventDate);
    authEventTypeOne.setEventId("EVENT_ID");
    authEventTypeOne.setEventType("EVENT_TYPE");
    authEventTypeOne.setEventResponse("RESPONSE");

    ChallengeResponseType challenge1 = new ChallengeResponseType();
    challenge1.setChallengeName("Password");
    challenge1.setChallengeResponse("Success");
    ChallengeResponseType challenge2 = new ChallengeResponseType();
    challenge2.setChallengeName("Mfa");
    challenge2.setChallengeResponse("Failure");
    authEventTypeOne.setChallengeResponses(List.of(challenge1, challenge2));

    EventContextDataType eventContextDataType = new EventContextDataType();
    eventContextDataType.setDeviceName("DEVICE");
    authEventTypeOne.setEventContextData(eventContextDataType);

    Date eventDateTwo = new Date();

    AuthEventType authEventTypeTwo = new AuthEventType();
    authEventTypeTwo.setCreationDate(eventDateTwo);
    authEventTypeTwo.setEventId("EVENT_ID_2");
    authEventTypeTwo.setEventType("EVENT_TYPE_2");
    authEventTypeTwo.setEventResponse("RESPONSE_2");

    ChallengeResponseType challenge3 = new ChallengeResponseType();
    challenge3.setChallengeName("Password");
    challenge3.setChallengeResponse("Failure");
    authEventTypeTwo.setChallengeResponses(List.of(challenge3));

    EventContextDataType eventContextDataTypeTwo = new EventContextDataType();
    eventContextDataTypeTwo.setDeviceName("DEVICE_2");
    authEventTypeTwo.setEventContextData(eventContextDataTypeTwo);

    AdminListUserAuthEventsResult result = new AdminListUserAuthEventsResult();
    result.setAuthEvents(List.of(authEventTypeOne, authEventTypeTwo));

    when(cognitoIdp.adminListUserAuthEvents(any())).thenReturn(result);

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
    ListUsersResult result = new ListUsersResult();
    result.setUsers(List.of());

    ArgumentCaptor<ListUsersRequest> requestCaptor = ArgumentCaptor.forClass(
        ListUsersRequest.class);
    when(cognitoIdp.listUsers(requestCaptor.capture())).thenReturn(result);

    service.getUserAccountIds(TRAINEE_ID_1);

    ListUsersRequest request = requestCaptor.getValue();
    assertThat("Unexpected request user pool.", request.getUserPoolId(), is(USER_POOL_ID));
  }

  @Test
  void shouldCacheAllUserAccountIdsWhenGettingUserAccountIds() {
    UserType user1 = new UserType().withAttributes(
        new AttributeType().withName(ATTRIBUTE_TRAINEE_ID).withValue(TRAINEE_ID_1),
        new AttributeType().withName(ATTRIBUTE_USER_ID).withValue(USER_ID_1)
    );
    UserType user2 = new UserType().withAttributes(
        new AttributeType().withName(ATTRIBUTE_TRAINEE_ID).withValue(TRAINEE_ID_2),
        new AttributeType().withName(ATTRIBUTE_USER_ID).withValue(USER_ID_2)
    );

    ListUsersResult result = new ListUsersResult();
    result.setUsers(List.of(user1, user2));

    when(cognitoIdp.listUsers(any(ListUsersRequest.class))).thenReturn(result);

    when(cache.get(any())).thenReturn(null);

    service.getUserAccountIds(TRAINEE_ID_1);

    verify(cache).put(TRAINEE_ID_1, Set.of(USER_ID_1));
    verify(cache).put(TRAINEE_ID_2, Set.of(USER_ID_2));
  }

  @Test
  void shouldPaginateThroughAllUserAccountIdsWhenGettingUserAccountIds() {
    ListUsersResult result1 = new ListUsersResult();
    result1.setUsers(List.of(new UserType().withAttributes(
        new AttributeType().withName(ATTRIBUTE_TRAINEE_ID).withValue(TRAINEE_ID_1),
        new AttributeType().withName(ATTRIBUTE_USER_ID).withValue(USER_ID_1)
    )));
    result1.setPaginationToken("tokenforpage2");

    ListUsersResult result2 = new ListUsersResult();
    result2.setUsers(List.of(new UserType().withAttributes(
        new AttributeType().withName(ATTRIBUTE_TRAINEE_ID).withValue(TRAINEE_ID_2),
        new AttributeType().withName(ATTRIBUTE_USER_ID).withValue(USER_ID_2)
    )));

    ArgumentCaptor<ListUsersRequest> requestCaptor = ArgumentCaptor.forClass(
        ListUsersRequest.class);
    when(cognitoIdp.listUsers(requestCaptor.capture())).thenReturn(result1, result2);

    when(cache.get(any())).thenReturn(null);

    service.getUserAccountIds(TRAINEE_ID_1);

    verify(cache).put(TRAINEE_ID_1, Set.of(USER_ID_1));
    verify(cache).put(TRAINEE_ID_2, Set.of(USER_ID_2));

    List<ListUsersRequest> requests = requestCaptor.getAllValues();
    assertThat("Unexpected request count.", requests.size(), is(2));
    ListUsersRequest request1 = requests.get(0);
    assertThat("Unexpected pagination token.", request1.getPaginationToken(), nullValue());
    ListUsersRequest request2 = requests.get(1);
    assertThat("Unexpected pagination token.", request2.getPaginationToken(), is("tokenforpage2"));
  }

  @Test
  void shouldCacheDuplicateUserAccountIdsWhenGettingUserAccountIds() {
    UserType user = new UserType().withAttributes(
        new AttributeType().withName(ATTRIBUTE_TRAINEE_ID).withValue(TRAINEE_ID_1),
        new AttributeType().withName(ATTRIBUTE_USER_ID).withValue(USER_ID_2)
    );

    ListUsersResult result = new ListUsersResult();
    result.setUsers(List.of(user));

    when(cognitoIdp.listUsers(any(ListUsersRequest.class))).thenReturn(result);

    when(cache.get(TRAINEE_ID_1, Set.class)).thenReturn(new HashSet<>(Set.of(USER_ID_1)));

    service.getUserAccountIds(TRAINEE_ID_1);

    verify(cache).put(TRAINEE_ID_1, Set.of(USER_ID_1, USER_ID_2));
  }

  @Test
  void shouldGetUserAccountIdsFromCache() {
    ListUsersResult result = new ListUsersResult();
    result.setUsers(List.of());

    when(cognitoIdp.listUsers(any(ListUsersRequest.class))).thenReturn(result);

    when(cache.get(TRAINEE_ID_1, Set.class)).thenReturn(Set.of(USER_ID_1, USER_ID_2));

    Set<String> userAccountIds = service.getUserAccountIds(TRAINEE_ID_1);

    assertThat("Unexpected user IDs count.", userAccountIds.size(), is(2));
    assertThat("Unexpected user IDs.", userAccountIds, hasItems(USER_ID_1, USER_ID_2));
  }

  @Test
  void shouldGetEmptyUserAccountIdsWhenAccountNotFoundAfterBuildingCache() {
    ListUsersResult result = new ListUsersResult();
    result.setUsers(List.of());

    when(cognitoIdp.listUsers(any(ListUsersRequest.class))).thenReturn(result);

    when(cache.get(TRAINEE_ID_1, Set.class)).thenReturn(null);

    Set<String> userAccountIds = service.getUserAccountIds(TRAINEE_ID_1);

    assertThat("Unexpected user IDs count.", userAccountIds.size(), is(0));
  }
}
