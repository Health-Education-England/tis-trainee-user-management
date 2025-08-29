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

package uk.nhs.tis.trainee.usermanagement.service;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static software.amazon.awssdk.services.cognitoidentityprovider.model.UserStatusType.CONFIRMED;
import static uk.nhs.tis.trainee.usermanagement.enumeration.MfaType.NO_MFA;
import static uk.nhs.tis.trainee.usermanagement.enumeration.MfaType.SMS_MFA;
import static uk.nhs.tis.trainee.usermanagement.enumeration.MfaType.SOFTWARE_TOKEN_MFA;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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
import software.amazon.awssdk.services.cognitoidentityprovider.model.GroupType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserStatusType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;
import uk.nhs.tis.trainee.usermanagement.dto.UserAccountDetailsDto;
import uk.nhs.tis.trainee.usermanagement.enumeration.MfaType;
import uk.nhs.tis.trainee.usermanagement.mapper.UserAccountDetailsMapper;
import uk.nhs.tis.trainee.usermanagement.mapper.UserAccountDetailsMapperImpl;

class CognitoServiceTest {

  private static final String USER_POOL_ID = "region_abc213";
  private static final String USER_ID = UUID.randomUUID().toString();
  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final String EMAIL = "joe.bloggs@fake.email";
  private static final String GROUP_1 = "user-group-one";
  private static final String GROUP_2 = "user-group-two";
  private static final Instant CREATED = Instant.now();

  private static final String ATTRIBUTE_SUB = "sub";
  private static final String ATTRIBUTE_EMAIL = "email";
  private static final String ATTRIBUTE_MFA_TYPE = "custom:mfaType";
  private static final String ATTRIBUTE_TRAINEE_ID = "custom:tisId";

  private CognitoService service;

  private CognitoIdentityProviderClient cognitoClient;

  @BeforeEach
  void setUp() {
    cognitoClient = mock(CognitoIdentityProviderClient.class);
    UserAccountDetailsMapper mapper = new UserAccountDetailsMapperImpl();

    service = new CognitoService(cognitoClient, USER_POOL_ID, mapper);

    // Cognito endpoints return empty results by default, rather than mocks returning null.
    AdminListGroupsForUserResponse groupResponse = AdminListGroupsForUserResponse.builder()
        .groups(List.of())
        .build();
    when(cognitoClient.adminListGroupsForUser((AdminListGroupsForUserRequest) any())).thenReturn(
        groupResponse);
  }

  @Test
  void shouldThrowExceptionGettingUserDetailsWhenUserNotFound() {
    when(cognitoClient.listUsers((ListUsersRequest) any())).thenReturn(
        ListUsersResponse.builder().users(List.of()).build()
    );

    assertThrows(UserNotFoundException.class, () -> service.getUserDetails(USER_ID));

    verify(cognitoClient, never()).adminGetUser((AdminGetUserRequest) any());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      sub | 369536f5-13ff-4b97-936f-85f112880ebf
      email | email@example.com
      """)
  void shouldGetUserDetailsFromListUsersByUsername(String type, String username) {
    ArgumentCaptor<ListUsersRequest> requestCaptor = ArgumentCaptor.captor();
    when(cognitoClient.listUsers(requestCaptor.capture())).thenReturn(ListUsersResponse.builder()
        .users(UserType.builder()
            .attributes(
                AttributeType.builder().name(ATTRIBUTE_MFA_TYPE).value(SMS_MFA.toString()).build()
            )
            .build())
        .build()
    );

    service.getUserDetails(username);

    ListUsersRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool id.", request.userPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected username.", request.filter(),
        is(String.format("%s=\"%s\"", type, username)));

    verify(cognitoClient, never()).adminGetUser((AdminGetUserRequest) any());
  }

  @ParameterizedTest
  @EnumSource(value = MfaType.class, mode = EXCLUDE, names = "NO_MFA")
  void shouldGetUserDetailsFromListUsersWhenCustomMfaSet(MfaType mfaType) {
    when(cognitoClient.listUsers((ListUsersRequest) any())).thenReturn(ListUsersResponse.builder()
        .users(UserType.builder()
            .attributes(
                AttributeType.builder().name(ATTRIBUTE_SUB).value(USER_ID).build(),
                AttributeType.builder().name(ATTRIBUTE_EMAIL).value(EMAIL).build(),
                AttributeType.builder().name(ATTRIBUTE_MFA_TYPE).value(mfaType.toString()).build(),
                AttributeType.builder().name(ATTRIBUTE_TRAINEE_ID).value(TRAINEE_ID).build()
            )
            .userCreateDate(CREATED)
            .userStatus(CONFIRMED.toString())
            .build())
        .build()
    );

    UserAccountDetailsDto userDetails = service.getUserDetails(USER_ID);

    assertThat("Unexpected ID.", userDetails.getId(), is(USER_ID));
    assertThat("Unexpected email.", userDetails.getEmail(), is(EMAIL));
    assertThat("Unexpected MFA status.", userDetails.getMfaStatus(), is(mfaType.toString()));
    assertThat("Unexpected user status.", userDetails.getUserStatus(), is(CONFIRMED.toString()));
    assertThat("Unexpected user status.", userDetails.getGroups(), hasSize(0));
    assertThat("Unexpected creation timestamp.", userDetails.getAccountCreated(), is(CREATED));
    assertThat("Unexpected trainee ID.", userDetails.getTraineeId(), is(TRAINEE_ID));

    verify(cognitoClient, never()).adminGetUser((AdminGetUserRequest) any());
  }

  @ParameterizedTest
  @EnumSource(value = MfaType.class, mode = EXCLUDE, names = "NO_MFA")
  void shouldNotPopulateCustomMfaTypeFromListUsersWhenCustomMfaSet(MfaType mfaType) {
    when(cognitoClient.listUsers((ListUsersRequest) any())).thenReturn(ListUsersResponse.builder()
        .users(UserType.builder()
            .attributes(
                AttributeType.builder().name(ATTRIBUTE_SUB).value(USER_ID).build(),
                AttributeType.builder().name(ATTRIBUTE_EMAIL).value(EMAIL).build(),
                AttributeType.builder().name(ATTRIBUTE_MFA_TYPE).value(mfaType.toString()).build(),
                AttributeType.builder().name(ATTRIBUTE_TRAINEE_ID).value(TRAINEE_ID).build()
            )
            .userCreateDate(CREATED)
            .userStatus(CONFIRMED.toString())
            .build())
        .build()
    );

    service.getUserDetails(USER_ID);

    verify(cognitoClient, never()).adminUpdateUserAttributes(
        (AdminUpdateUserAttributesRequest) any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"369536f5-13ff-4b97-936f-85f112880ebf", "email@example.com"})
  void shouldGetUserDetailsFromAdminGetUserByUsername(String username) {
    when(cognitoClient.listUsers((ListUsersRequest) any())).thenReturn(ListUsersResponse.builder()
        .users(UserType.builder().attributes(List.of()).build())
        .build());

    ArgumentCaptor<AdminGetUserRequest> requestCaptor = ArgumentCaptor.captor();
    when(cognitoClient.adminGetUser(requestCaptor.capture())).thenReturn(
        AdminGetUserResponse.builder().build());

    service.getUserDetails(username);

    AdminGetUserRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool id.", request.userPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected username.", request.username(), is(username));
  }

  @Test
  void shouldGetUserDetailsFromAdminGetUserWhenCustomMfaNotSet() {
    when(cognitoClient.listUsers((ListUsersRequest) any())).thenReturn(ListUsersResponse.builder()
        .users(UserType.builder().attributes(List.of()).build())
        .build());

    when(cognitoClient.adminGetUser((AdminGetUserRequest) any())).thenReturn(
        AdminGetUserResponse.builder()
            .userAttributes(
                AttributeType.builder().name(ATTRIBUTE_SUB).value(USER_ID).build(),
                AttributeType.builder().name(ATTRIBUTE_EMAIL).value(EMAIL).build(),
                AttributeType.builder().name(ATTRIBUTE_TRAINEE_ID).value(TRAINEE_ID).build()
            )
            .preferredMfaSetting(SOFTWARE_TOKEN_MFA.toString())
            .userCreateDate(CREATED)
            .userStatus(CONFIRMED)
            .build()
    );

    UserAccountDetailsDto userDetails = service.getUserDetails(USER_ID);

    assertThat("Unexpected ID.", userDetails.getId(), is(USER_ID));
    assertThat("Unexpected email.", userDetails.getEmail(), is(EMAIL));
    assertThat("Unexpected MFA status.", userDetails.getMfaStatus(),
        is(SOFTWARE_TOKEN_MFA.toString()));
    assertThat("Unexpected user status.", userDetails.getUserStatus(), is(CONFIRMED.toString()));
    assertThat("Unexpected user status.", userDetails.getGroups(), hasSize(0));
    assertThat("Unexpected creation timestamp.", userDetails.getAccountCreated(), is(CREATED));
    assertThat("Unexpected trainee ID.", userDetails.getTraineeId(), is(TRAINEE_ID));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', nullValues = "null", textBlock = """
      EMAIL_OTP          | EMAIL_OTP
      SMS_MFA            | SMS_MFA
      SOFTWARE_TOKEN_MFA | SOFTWARE_TOKEN_MFA
      null               | NO_MFA
      """)
  void shouldPopulateCustomMfaTypeFromAdminGetUserWhenCustomMfaNotSet(
      String preferredMfa, MfaType mfaType) {
    when(cognitoClient.listUsers((ListUsersRequest) any())).thenReturn(ListUsersResponse.builder()
        .users(UserType.builder().attributes(List.of()).build())
        .build());

    when(cognitoClient.adminGetUser((AdminGetUserRequest) any())).thenReturn(
        AdminGetUserResponse.builder().preferredMfaSetting(preferredMfa).build()
    );

    service.getUserDetails(USER_ID);

    ArgumentCaptor<AdminUpdateUserAttributesRequest> requestCaptor = ArgumentCaptor.captor();
    verify(cognitoClient).adminUpdateUserAttributes(requestCaptor.capture());

    AdminUpdateUserAttributesRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool id.", request.userPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected username.", request.username(), is(USER_ID));

    List<AttributeType> requestAttributes = request.userAttributes();
    assertThat("Unexpected attribute count.", requestAttributes, hasSize(1));

    AttributeType requestAttribute = requestAttributes.get(0);
    assertThat("Unexpected attribute name.", requestAttribute.name(), is(ATTRIBUTE_MFA_TYPE));
    assertThat("Unexpected attribute value.", requestAttribute.value(), is(mfaType.toString()));
  }

  @Test
  void shouldGetUserDetailsFromAdminGetUserWhenCustomMfaNoMfa() {
    when(cognitoClient.listUsers((ListUsersRequest) any())).thenReturn(ListUsersResponse.builder()
        .users(UserType.builder()
            .attributes(
                AttributeType.builder().name(ATTRIBUTE_MFA_TYPE).value(NO_MFA.toString()).build())
            .build())
        .build()
    );

    when(cognitoClient.adminGetUser((AdminGetUserRequest) any())).thenReturn(
        AdminGetUserResponse.builder()
            .userAttributes(
                AttributeType.builder().name(ATTRIBUTE_SUB).value(USER_ID).build(),
                AttributeType.builder().name(ATTRIBUTE_EMAIL).value(EMAIL).build(),
                AttributeType.builder().name(ATTRIBUTE_TRAINEE_ID).value(TRAINEE_ID).build()
            )
            .preferredMfaSetting(SOFTWARE_TOKEN_MFA.toString())
            .userCreateDate(CREATED)
            .userStatus(CONFIRMED)
            .build()
    );

    UserAccountDetailsDto userDetails = service.getUserDetails(USER_ID);

    assertThat("Unexpected ID.", userDetails.getId(), is(USER_ID));
    assertThat("Unexpected email.", userDetails.getEmail(), is(EMAIL));
    assertThat("Unexpected MFA status.", userDetails.getMfaStatus(),
        is(SOFTWARE_TOKEN_MFA.toString()));
    assertThat("Unexpected user status.", userDetails.getUserStatus(), is(CONFIRMED.toString()));
    assertThat("Unexpected user status.", userDetails.getGroups(), hasSize(0));
    assertThat("Unexpected creation timestamp.", userDetails.getAccountCreated(), is(CREATED));
    assertThat("Unexpected trainee ID.", userDetails.getTraineeId(), is(TRAINEE_ID));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', nullValues = "null", textBlock = """
      EMAIL_OTP          | EMAIL_OTP
      SMS_MFA            | SMS_MFA
      SOFTWARE_TOKEN_MFA | SOFTWARE_TOKEN_MFA
      null               | NO_MFA
      """)
  void shouldPopulateCustomMfaTypeFromAdminGetUserWhenCustomMfaNoMfa(String preferredMfa,
      MfaType mfaType) {
    when(cognitoClient.listUsers((ListUsersRequest) any())).thenReturn(ListUsersResponse.builder()
        .users(UserType.builder()
            .attributes(
                AttributeType.builder().name(ATTRIBUTE_MFA_TYPE).value(NO_MFA.toString()).build()
            )
            .build())
        .build()
    );

    when(cognitoClient.adminGetUser((AdminGetUserRequest) any())).thenReturn(
        AdminGetUserResponse.builder().preferredMfaSetting(preferredMfa).build()
    );

    service.getUserDetails(USER_ID);

    ArgumentCaptor<AdminUpdateUserAttributesRequest> requestCaptor = ArgumentCaptor.captor();
    verify(cognitoClient).adminUpdateUserAttributes(requestCaptor.capture());

    AdminUpdateUserAttributesRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool id.", request.userPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected username.", request.username(), is(USER_ID));

    List<AttributeType> requestAttributes = request.userAttributes();
    assertThat("Unexpected attribute count.", requestAttributes, hasSize(1));

    AttributeType requestAttribute = requestAttributes.get(0);
    assertThat("Unexpected attribute name.", requestAttribute.name(), is(ATTRIBUTE_MFA_TYPE));
    assertThat("Unexpected attribute value.", requestAttribute.value(), is(mfaType.toString()));
  }

  @ParameterizedTest
  @EnumSource(UserStatusType.class)
  void shouldConvertUserStatusWhenGettingUserDetails(UserStatusType userStatus) {
    when(cognitoClient.listUsers((ListUsersRequest) any())).thenReturn(ListUsersResponse.builder()
        .users(UserType.builder().attributes(List.of()).build())
        .build());

    AdminGetUserResponse response = AdminGetUserResponse.builder()
        .userStatus(userStatus)
        .build();

    when(cognitoClient.adminGetUser((AdminGetUserRequest) any())).thenReturn(response);

    UserAccountDetailsDto userAccountDetails = service.getUserDetails(EMAIL);
    assertThat("Unexpected user status.", userAccountDetails.getUserStatus(),
        is(userStatus.toString()));
  }

  @ParameterizedTest
  @EnumSource(value = MfaType.class, mode = EXCLUDE, names = "NO_MFA")
  void shouldConvertCustomMfaTypeWhenGettingUserDetails(MfaType mfaType) {
    ListUsersResponse response = ListUsersResponse.builder()
        .users(UserType.builder()
            .attributes(
                AttributeType.builder().name(ATTRIBUTE_MFA_TYPE).value(mfaType.toString()).build()
            )
            .build())
        .build();

    when(cognitoClient.listUsers((ListUsersRequest) any())).thenReturn(response);

    UserAccountDetailsDto userAccountDetails = service.getUserDetails(EMAIL);
    assertThat("Unexpected MFA type.", userAccountDetails.getMfaStatus(),
        is(mfaType.toString()));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', nullValues = "null", textBlock = """
      EMAIL_OTP          | EMAIL_OTP
      SMS_MFA            | SMS_MFA
      SOFTWARE_TOKEN_MFA | SOFTWARE_TOKEN_MFA
      null               | NO_MFA
      """)
  void shouldConvertAwsMfaPreferenceWhenGettingUserDetails(String preferredMfa, MfaType mfaType) {
    when(cognitoClient.listUsers((ListUsersRequest) any())).thenReturn(ListUsersResponse.builder()
        .users(UserType.builder().attributes(List.of()).build())
        .build()
    );

    AdminGetUserResponse response = AdminGetUserResponse.builder().preferredMfaSetting(preferredMfa)
        .build();

    when(cognitoClient.adminGetUser((AdminGetUserRequest) any())).thenReturn(response);

    UserAccountDetailsDto userAccountDetails = service.getUserDetails(EMAIL);
    assertThat("Unexpected MFA type.", userAccountDetails.getMfaStatus(), is(mfaType.toString()));
  }

  @Test
  void shouldReturnNoGroupsWhenUserNotFoundRetrievingGroups() {
    when(cognitoClient.listUsers((ListUsersRequest) any())).thenReturn(ListUsersResponse.builder()
        .users(UserType.builder()
            .attributes(
                AttributeType.builder().name(ATTRIBUTE_MFA_TYPE).value(SMS_MFA.toString()).build()
            )
            .build())
        .build()
    );
    when(cognitoClient.adminListGroupsForUser((AdminListGroupsForUserRequest) any())).thenThrow(
        UserNotFoundException.class);

    UserAccountDetailsDto userAccountDetails = service.getUserDetails(EMAIL);
    List<String> groups = userAccountDetails.getGroups();
    assertThat("Unexpected user groups.", groups, notNullValue());
    assertThat("Unexpected user groups.", groups.size(), is(0));
  }

  @Test
  void shouldReturnNoGroupsWhenUserHasNoGroups() {
    AdminListGroupsForUserResponse response = AdminListGroupsForUserResponse.builder()
        .groups(List.of())
        .build();

    when(cognitoClient.listUsers((ListUsersRequest) any())).thenReturn(ListUsersResponse.builder()
        .users(UserType.builder()
            .attributes(
                AttributeType.builder().name(ATTRIBUTE_MFA_TYPE).value(SMS_MFA.toString()).build()
            )
            .build())
        .build()
    );
    when(cognitoClient.adminListGroupsForUser((AdminListGroupsForUserRequest) any())).thenReturn(
        response);

    UserAccountDetailsDto userAccountDetails = service.getUserDetails(EMAIL);
    List<String> groups = userAccountDetails.getGroups();
    assertThat("Unexpected user groups.", groups, notNullValue());
    assertThat("Unexpected user groups.", groups.size(), is(0));
  }

  @Test
  void shouldReturnGroupsWhenUserHasGroups() {
    AdminListGroupsForUserResponse response = AdminListGroupsForUserResponse.builder()
        .groups(
            GroupType.builder().groupName(GROUP_1).build(),
            GroupType.builder().groupName(GROUP_2).build())
        .build();

    when(cognitoClient.listUsers((ListUsersRequest) any())).thenReturn(ListUsersResponse.builder()
        .users(UserType.builder()
            .attributes(
                AttributeType.builder().name(ATTRIBUTE_MFA_TYPE).value(SMS_MFA.toString()).build()
            )
            .build())
        .build()
    );
    when(cognitoClient.adminListGroupsForUser((AdminListGroupsForUserRequest) any())).thenReturn(
        response);

    UserAccountDetailsDto userAccountDetails = service.getUserDetails(EMAIL);
    List<String> groups = userAccountDetails.getGroups();
    assertThat("Unexpected user groups.", groups, notNullValue());
    assertThat("Unexpected user groups.", groups.size(), is(2));
    assertThat("Unexpected user groups.", groups, hasItems(GROUP_1, GROUP_2));
  }

  @Test
  void shouldUpdateUserAttributes() {
    List<AttributeType> attributes = List.of(
        AttributeType.builder().name("attribute1").value("value1").build(),
        AttributeType.builder().name("attribute2").value("value2").build()
    );

    service.updateAttributes(USER_ID, attributes);

    ArgumentCaptor<AdminUpdateUserAttributesRequest> requestCaptor = ArgumentCaptor.captor();
    verify(cognitoClient).adminUpdateUserAttributes(requestCaptor.capture());

    AdminUpdateUserAttributesRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool id.", request.userPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected username.", request.username(), is(USER_ID));

    List<AttributeType> requestAttributes = request.userAttributes();
    assertThat("Unexpected attributes.", requestAttributes, is(attributes));
  }

  @Test
  void shouldWrapAdminAddUserToGroup() {
    var request = AdminAddUserToGroupRequest.builder().build();
    var expectedResponse = AdminAddUserToGroupResponse.builder().build();
    when(cognitoClient.adminAddUserToGroup(request)).thenReturn(expectedResponse);

    var actualResponse = service.adminAddUserToGroup(request);
    assertThat("Unexpected result.", actualResponse, sameInstance(expectedResponse));
  }

  @Test
  void shouldWrapAdminDeleteUser() {
    var request = AdminDeleteUserRequest.builder().build();
    var expectedResponse = AdminDeleteUserResponse.builder().build();
    when(cognitoClient.adminDeleteUser(request)).thenReturn(expectedResponse);

    var actualResponse = service.adminDeleteUser(request);
    assertThat("Unexpected result.", actualResponse, sameInstance(expectedResponse));
  }

  @Test
  void shouldWrapAdminListUserAuthEvents() {
    var request = AdminListUserAuthEventsRequest.builder().build();
    var expectedResponse = AdminListUserAuthEventsResponse.builder().build();
    when(cognitoClient.adminListUserAuthEvents(request)).thenReturn(expectedResponse);

    var actualResponse = service.adminListUserAuthEvents(request);
    assertThat("Unexpected result.", actualResponse, sameInstance(expectedResponse));
  }

  @Test
  void shouldWrapAdminRemoveUserFromGroup() {
    var request = AdminRemoveUserFromGroupRequest.builder().build();
    var expectedResponse = AdminRemoveUserFromGroupResponse.builder().build();
    when(cognitoClient.adminRemoveUserFromGroup(request)).thenReturn(expectedResponse);

    var actualResponse = service.adminRemoveUserFromGroup(request);
    assertThat("Unexpected result.", actualResponse, sameInstance(expectedResponse));
  }

  @Test
  void shouldWrapAdminSetUserMfaPreference() {
    var request = AdminSetUserMfaPreferenceRequest.builder().build();
    var expectedResponse = AdminSetUserMfaPreferenceResponse.builder().build();
    when(cognitoClient.adminSetUserMFAPreference(request)).thenReturn(expectedResponse);

    var actualResponse = service.adminSetUserMfaPreference(request);
    assertThat("Unexpected result.", actualResponse, sameInstance(expectedResponse));
  }

  @Test
  void shouldWrapListUsers() {
    var request = ListUsersRequest.builder().build();
    var expectedResponse = ListUsersResponse.builder().build();
    when(cognitoClient.listUsers(request)).thenReturn(expectedResponse);

    var actualResponse = service.listUsers(request);
    assertThat("Unexpected result.", actualResponse, sameInstance(expectedResponse));
  }
}
