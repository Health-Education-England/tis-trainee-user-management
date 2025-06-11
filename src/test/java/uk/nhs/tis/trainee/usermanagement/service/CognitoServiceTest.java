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

import static com.amazonaws.services.cognitoidp.model.ChallengeNameType.SOFTWARE_TOKEN_MFA;
import static com.amazonaws.services.cognitoidp.model.UserStatusType.CONFIRMED;
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
import static uk.nhs.tis.trainee.usermanagement.enumeration.MfaType.NO_MFA;
import static uk.nhs.tis.trainee.usermanagement.enumeration.MfaType.SMS;
import static uk.nhs.tis.trainee.usermanagement.enumeration.MfaType.TOTP;

import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.model.AdminAddUserToGroupRequest;
import com.amazonaws.services.cognitoidp.model.AdminAddUserToGroupResult;
import com.amazonaws.services.cognitoidp.model.AdminDeleteUserRequest;
import com.amazonaws.services.cognitoidp.model.AdminDeleteUserResult;
import com.amazonaws.services.cognitoidp.model.AdminGetUserRequest;
import com.amazonaws.services.cognitoidp.model.AdminGetUserResult;
import com.amazonaws.services.cognitoidp.model.AdminListGroupsForUserResult;
import com.amazonaws.services.cognitoidp.model.AdminListUserAuthEventsRequest;
import com.amazonaws.services.cognitoidp.model.AdminListUserAuthEventsResult;
import com.amazonaws.services.cognitoidp.model.AdminRemoveUserFromGroupRequest;
import com.amazonaws.services.cognitoidp.model.AdminRemoveUserFromGroupResult;
import com.amazonaws.services.cognitoidp.model.AdminSetUserMFAPreferenceRequest;
import com.amazonaws.services.cognitoidp.model.AdminSetUserMFAPreferenceResult;
import com.amazonaws.services.cognitoidp.model.AdminUpdateUserAttributesRequest;
import com.amazonaws.services.cognitoidp.model.AttributeType;
import com.amazonaws.services.cognitoidp.model.GroupType;
import com.amazonaws.services.cognitoidp.model.ListUsersRequest;
import com.amazonaws.services.cognitoidp.model.ListUsersResult;
import com.amazonaws.services.cognitoidp.model.UserNotFoundException;
import com.amazonaws.services.cognitoidp.model.UserStatusType;
import com.amazonaws.services.cognitoidp.model.UserType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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

  private AWSCognitoIdentityProvider cognitoIdp;

  @BeforeEach
  void setUp() {
    cognitoIdp = mock(AWSCognitoIdentityProvider.class);
    UserAccountDetailsMapper mapper = new UserAccountDetailsMapperImpl();

    service = new CognitoService(cognitoIdp, USER_POOL_ID, mapper);

    // Cognito endpoints return empty results by default, rather than mocks returning null.
    AdminListGroupsForUserResult groupResult = new AdminListGroupsForUserResult().withGroups(
        List.of());
    when(cognitoIdp.adminListGroupsForUser(any())).thenReturn(groupResult);
  }

  @Test
  void shouldThrowExceptionGettingUserDetailsWhenUserNotFound() {
    when(cognitoIdp.listUsers(any())).thenReturn(new ListUsersResult().withUsers(List.of()));

    assertThrows(UserNotFoundException.class, () -> service.getUserDetails(USER_ID));

    verify(cognitoIdp, never()).adminGetUser(any());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      sub | 369536f5-13ff-4b97-936f-85f112880ebf
      email | email@example.com
      """)
  void shouldGetUserDetailsFromListUsersByUsername(String type, String username) {
    ArgumentCaptor<ListUsersRequest> requestCaptor = ArgumentCaptor.forClass(
        ListUsersRequest.class);
    when(cognitoIdp.listUsers(requestCaptor.capture())).thenReturn(new ListUsersResult().withUsers(
        new UserType()
            .withAttributes(
                new AttributeType().withName(ATTRIBUTE_MFA_TYPE).withValue(SMS.toString())
            )
    ));

    service.getUserDetails(username);

    ListUsersRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool id.", request.getUserPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected username.", request.getFilter(),
        is(String.format("%s=\"%s\"", type, username)));

    verify(cognitoIdp, never()).adminGetUser(any());
  }

  @ParameterizedTest
  @EnumSource(value = MfaType.class, mode = EXCLUDE, names = "NO_MFA")
  void shouldGetUserDetailsFromListUsersWhenCustomMfaSet(MfaType mfaType) {
    when(cognitoIdp.listUsers(any())).thenReturn(new ListUsersResult().withUsers(
        new UserType()
            .withAttributes(
                new AttributeType().withName(ATTRIBUTE_SUB).withValue(USER_ID),
                new AttributeType().withName(ATTRIBUTE_EMAIL).withValue(EMAIL),
                new AttributeType().withName(ATTRIBUTE_MFA_TYPE).withValue(mfaType.toString()),
                new AttributeType().withName(ATTRIBUTE_TRAINEE_ID).withValue(TRAINEE_ID)
            )
            .withUserCreateDate(Date.from(CREATED))
            .withUserStatus(CONFIRMED.toString())
    ));

    UserAccountDetailsDto userDetails = service.getUserDetails(USER_ID);

    assertThat("Unexpected ID.", userDetails.getId(), is(USER_ID));
    assertThat("Unexpected email.", userDetails.getEmail(), is(EMAIL));
    assertThat("Unexpected MFA status.", userDetails.getMfaStatus(), is(mfaType.toString()));
    assertThat("Unexpected user status.", userDetails.getUserStatus(), is(CONFIRMED.toString()));
    assertThat("Unexpected user status.", userDetails.getGroups(), hasSize(0));
    assertThat("Unexpected creation timestamp.", userDetails.getAccountCreated(),
        is(CREATED.truncatedTo(ChronoUnit.MILLIS)));
    assertThat("Unexpected trainee ID.", userDetails.getTraineeId(), is(TRAINEE_ID));

    verify(cognitoIdp, never()).adminGetUser(any());
  }

  @ParameterizedTest
  @EnumSource(value = MfaType.class, mode = EXCLUDE, names = "NO_MFA")
  void shouldNotPopulateCustomMfaTypeFromListUsersWhenCustomMfaSet(MfaType mfaType) {
    when(cognitoIdp.listUsers(any())).thenReturn(new ListUsersResult().withUsers(
        new UserType()
            .withAttributes(
                new AttributeType().withName(ATTRIBUTE_SUB).withValue(USER_ID),
                new AttributeType().withName(ATTRIBUTE_EMAIL).withValue(EMAIL),
                new AttributeType().withName(ATTRIBUTE_MFA_TYPE).withValue(mfaType.toString()),
                new AttributeType().withName(ATTRIBUTE_TRAINEE_ID).withValue(TRAINEE_ID)
            )
            .withUserCreateDate(Date.from(CREATED))
            .withUserStatus(CONFIRMED.toString())
    ));

    service.getUserDetails(USER_ID);

    verify(cognitoIdp, never()).adminUpdateUserAttributes(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"369536f5-13ff-4b97-936f-85f112880ebf", "email@example.com"})
  void shouldGetUserDetailsFromAdminGetUserByUsername(String username) {
    when(cognitoIdp.listUsers(any())).thenReturn(new ListUsersResult().withUsers(
        new UserType().withAttributes(List.of())
    ));

    ArgumentCaptor<AdminGetUserRequest> requestCaptor = ArgumentCaptor.forClass(
        AdminGetUserRequest.class);
    when(cognitoIdp.adminGetUser(requestCaptor.capture())).thenReturn(new AdminGetUserResult());

    service.getUserDetails(username);

    AdminGetUserRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool id.", request.getUserPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected username.", request.getUsername(), is(username));
  }

  @Test
  void shouldGetUserDetailsFromAdminGetUserWhenCustomMfaNotSet() {
    when(cognitoIdp.listUsers(any())).thenReturn(new ListUsersResult().withUsers(
        new UserType().withAttributes(List.of())
    ));

    when(cognitoIdp.adminGetUser(any())).thenReturn(
        new AdminGetUserResult()
            .withUserAttributes(List.of(
                new AttributeType().withName(ATTRIBUTE_SUB).withValue(USER_ID),
                new AttributeType().withName(ATTRIBUTE_EMAIL).withValue(EMAIL),
                new AttributeType().withName(ATTRIBUTE_TRAINEE_ID).withValue(TRAINEE_ID)
            ))
            .withPreferredMfaSetting(SOFTWARE_TOKEN_MFA.toString())
            .withUserCreateDate(Date.from(CREATED))
            .withUserStatus(CONFIRMED)
    );

    UserAccountDetailsDto userDetails = service.getUserDetails(USER_ID);

    assertThat("Unexpected ID.", userDetails.getId(), is(USER_ID));
    assertThat("Unexpected email.", userDetails.getEmail(), is(EMAIL));
    assertThat("Unexpected MFA status.", userDetails.getMfaStatus(), is(TOTP.toString()));
    assertThat("Unexpected user status.", userDetails.getUserStatus(), is(CONFIRMED.toString()));
    assertThat("Unexpected user status.", userDetails.getGroups(), hasSize(0));
    assertThat("Unexpected creation timestamp.", userDetails.getAccountCreated(),
        is(CREATED.truncatedTo(ChronoUnit.MILLIS)));
    assertThat("Unexpected trainee ID.", userDetails.getTraineeId(), is(TRAINEE_ID));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', nullValues = "null", textBlock = """
      SMS_MFA            | SMS
      SOFTWARE_TOKEN_MFA | TOTP
      null               | NO_MFA
      """)
  void shouldPopulateCustomMfaTypeFromAdminGetUserWhenCustomMfaNotSet(String preferredMfa,
      String mfaType) {
    when(cognitoIdp.listUsers(any())).thenReturn(new ListUsersResult().withUsers(
        new UserType().withAttributes(List.of())
    ));

    when(cognitoIdp.adminGetUser(any())).thenReturn(
        new AdminGetUserResult().withPreferredMfaSetting(preferredMfa)
    );

    service.getUserDetails(USER_ID);

    ArgumentCaptor<AdminUpdateUserAttributesRequest> requestCaptor = ArgumentCaptor.forClass(
        AdminUpdateUserAttributesRequest.class);
    verify(cognitoIdp).adminUpdateUserAttributes(requestCaptor.capture());

    AdminUpdateUserAttributesRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool id.", request.getUserPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected username.", request.getUsername(), is(USER_ID));

    List<AttributeType> requestAttributes = request.getUserAttributes();
    assertThat("Unexpected attribute count.", requestAttributes, hasSize(1));

    AttributeType requestAttribute = requestAttributes.get(0);
    assertThat("Unexpected attribute name.", requestAttribute.getName(), is(ATTRIBUTE_MFA_TYPE));
    assertThat("Unexpected attribute value.", requestAttribute.getValue(), is(mfaType));
  }

  @Test
  void shouldGetUserDetailsFromAdminGetUserWhenCustomMfaNoMfa() {
    when(cognitoIdp.listUsers(any())).thenReturn(new ListUsersResult().withUsers(
        new UserType()
            .withAttributes(
                new AttributeType().withName(ATTRIBUTE_MFA_TYPE).withValue(NO_MFA.toString())
            )
    ));

    when(cognitoIdp.adminGetUser(any())).thenReturn(
        new AdminGetUserResult()
            .withUserAttributes(List.of(
                new AttributeType().withName(ATTRIBUTE_SUB).withValue(USER_ID),
                new AttributeType().withName(ATTRIBUTE_EMAIL).withValue(EMAIL),
                new AttributeType().withName(ATTRIBUTE_TRAINEE_ID).withValue(TRAINEE_ID)
            ))
            .withPreferredMfaSetting(SOFTWARE_TOKEN_MFA.toString())
            .withUserCreateDate(Date.from(CREATED))
            .withUserStatus(CONFIRMED)
    );

    UserAccountDetailsDto userDetails = service.getUserDetails(USER_ID);

    assertThat("Unexpected ID.", userDetails.getId(), is(USER_ID));
    assertThat("Unexpected email.", userDetails.getEmail(), is(EMAIL));
    assertThat("Unexpected MFA status.", userDetails.getMfaStatus(), is(TOTP.toString()));
    assertThat("Unexpected user status.", userDetails.getUserStatus(), is(CONFIRMED.toString()));
    assertThat("Unexpected user status.", userDetails.getGroups(), hasSize(0));
    assertThat("Unexpected creation timestamp.", userDetails.getAccountCreated(),
        is(CREATED.truncatedTo(ChronoUnit.MILLIS)));
    assertThat("Unexpected trainee ID.", userDetails.getTraineeId(), is(TRAINEE_ID));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', nullValues = "null", textBlock = """
      SMS_MFA            | SMS
      SOFTWARE_TOKEN_MFA | TOTP
      null               | NO_MFA
      """)
  void shouldPopulateCustomMfaTypeFromAdminGetUserWhenCustomMfaNoMfa(String preferredMfa,
      String mfaType) {
    when(cognitoIdp.listUsers(any())).thenReturn(new ListUsersResult().withUsers(
        new UserType()
            .withAttributes(
                new AttributeType().withName(ATTRIBUTE_MFA_TYPE).withValue(NO_MFA.toString())
            )
    ));

    when(cognitoIdp.adminGetUser(any())).thenReturn(
        new AdminGetUserResult().withPreferredMfaSetting(preferredMfa)
    );

    service.getUserDetails(USER_ID);

    ArgumentCaptor<AdminUpdateUserAttributesRequest> requestCaptor = ArgumentCaptor.forClass(
        AdminUpdateUserAttributesRequest.class);
    verify(cognitoIdp).adminUpdateUserAttributes(requestCaptor.capture());

    AdminUpdateUserAttributesRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool id.", request.getUserPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected username.", request.getUsername(), is(USER_ID));

    List<AttributeType> requestAttributes = request.getUserAttributes();
    assertThat("Unexpected attribute count.", requestAttributes, hasSize(1));

    AttributeType requestAttribute = requestAttributes.get(0);
    assertThat("Unexpected attribute name.", requestAttribute.getName(), is(ATTRIBUTE_MFA_TYPE));
    assertThat("Unexpected attribute value.", requestAttribute.getValue(), is(mfaType));
  }

  @ParameterizedTest
  @EnumSource(UserStatusType.class)
  void shouldConvertUserStatusWhenGettingUserDetails(UserStatusType userStatus) {
    when(cognitoIdp.listUsers(any())).thenReturn(new ListUsersResult().withUsers(
        new UserType().withAttributes(List.of())
    ));

    AdminGetUserResult result = new AdminGetUserResult();
    result.setUserStatus(userStatus);

    when(cognitoIdp.adminGetUser(any())).thenReturn(result);

    UserAccountDetailsDto userAccountDetails = service.getUserDetails(EMAIL);
    assertThat("Unexpected user status.", userAccountDetails.getUserStatus(),
        is(userStatus.toString()));
  }

  @ParameterizedTest
  @EnumSource(value = MfaType.class, mode = EXCLUDE, names = "NO_MFA")
  void shouldConvertCustomMfaTypeWhenGettingUserDetails(MfaType mfaType) {
    ListUsersResult result = new ListUsersResult().withUsers(
        new UserType()
            .withAttributes(
                new AttributeType().withName(ATTRIBUTE_MFA_TYPE).withValue(mfaType.toString())
            )
    );

    when(cognitoIdp.listUsers(any())).thenReturn(result);

    UserAccountDetailsDto userAccountDetails = service.getUserDetails(EMAIL);
    assertThat("Unexpected MFA type.", userAccountDetails.getMfaStatus(),
        is(mfaType.toString()));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', nullValues = "null", textBlock = """
      SMS_MFA            | SMS
      SOFTWARE_TOKEN_MFA | TOTP
      null               | NO_MFA
      """)
  void shouldConvertAwsMfaPreferenceWhenGettingUserDetails(String preferredMfa, MfaType mfaType) {
    when(cognitoIdp.listUsers(any())).thenReturn(new ListUsersResult().withUsers(
        new UserType().withAttributes(List.of())
    ));

    AdminGetUserResult result = new AdminGetUserResult()
        .withPreferredMfaSetting(preferredMfa);

    when(cognitoIdp.adminGetUser(any())).thenReturn(result);

    UserAccountDetailsDto userAccountDetails = service.getUserDetails(EMAIL);
    assertThat("Unexpected MFA type.", userAccountDetails.getMfaStatus(), is(mfaType.toString()));
  }

  @Test
  void shouldReturnNoGroupsWhenUserNotFoundRetrievingGroups() {
    when(cognitoIdp.listUsers(any())).thenReturn(new ListUsersResult().withUsers(
        new UserType().withAttributes(
            new AttributeType().withName(ATTRIBUTE_MFA_TYPE).withValue(SMS.toString())
        )
    ));
    when(cognitoIdp.adminListGroupsForUser(any())).thenThrow(UserNotFoundException.class);

    UserAccountDetailsDto userAccountDetails = service.getUserDetails(EMAIL);
    List<String> groups = userAccountDetails.getGroups();
    assertThat("Unexpected user groups.", groups, notNullValue());
    assertThat("Unexpected user groups.", groups.size(), is(0));
  }

  @Test
  void shouldReturnNoGroupsWhenUserHasNoGroups() {
    AdminListGroupsForUserResult result = new AdminListGroupsForUserResult();
    result.setGroups(List.of());

    when(cognitoIdp.listUsers(any())).thenReturn(new ListUsersResult().withUsers(
        new UserType().withAttributes(
            new AttributeType().withName(ATTRIBUTE_MFA_TYPE).withValue(SMS.toString())
        )
    ));
    when(cognitoIdp.adminListGroupsForUser(any())).thenReturn(result);

    UserAccountDetailsDto userAccountDetails = service.getUserDetails(EMAIL);
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

    when(cognitoIdp.listUsers(any())).thenReturn(new ListUsersResult().withUsers(
        new UserType().withAttributes(
            new AttributeType().withName(ATTRIBUTE_MFA_TYPE).withValue(SMS.toString())
        )
    ));
    when(cognitoIdp.adminListGroupsForUser(any())).thenReturn(result);

    UserAccountDetailsDto userAccountDetails = service.getUserDetails(EMAIL);
    List<String> groups = userAccountDetails.getGroups();
    assertThat("Unexpected user groups.", groups, notNullValue());
    assertThat("Unexpected user groups.", groups.size(), is(2));
    assertThat("Unexpected user groups.", groups, hasItems(GROUP_1, GROUP_2));
  }

  @Test
  void shouldUpdateUserAttributes() {
    List<AttributeType> attributes = List.of(
        new AttributeType().withName("attribute1").withValue("value1"),
        new AttributeType().withName("attribute2").withValue("value2")
    );

    service.updateAttributes(USER_ID, attributes);

    ArgumentCaptor<AdminUpdateUserAttributesRequest> requestCaptor = ArgumentCaptor.forClass(
        AdminUpdateUserAttributesRequest.class);
    verify(cognitoIdp).adminUpdateUserAttributes(requestCaptor.capture());

    AdminUpdateUserAttributesRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool id.", request.getUserPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected username.", request.getUsername(), is(USER_ID));

    List<AttributeType> requestAttributes = request.getUserAttributes();
    assertThat("Unexpected attributes.", requestAttributes, is(attributes));
  }

  @Test
  void shouldWrapAdminAddUserToGroup() {
    var request = new AdminAddUserToGroupRequest();
    var expectedResult = new AdminAddUserToGroupResult();
    when(cognitoIdp.adminAddUserToGroup(request)).thenReturn(expectedResult);

    var actualResult = service.adminAddUserToGroup(request);
    assertThat("Unexpected result.", actualResult, sameInstance(expectedResult));
  }

  @Test
  void shouldWrapAdminDeleteUser() {
    var request = new AdminDeleteUserRequest();
    var expectedResult = new AdminDeleteUserResult();
    when(cognitoIdp.adminDeleteUser(request)).thenReturn(expectedResult);

    var actualResult = service.adminDeleteUser(request);
    assertThat("Unexpected result.", actualResult, sameInstance(expectedResult));
  }

  @Test
  void shouldWrapAdminListUserAuthEvents() {
    var request = new AdminListUserAuthEventsRequest();
    var expectedResult = new AdminListUserAuthEventsResult();
    when(cognitoIdp.adminListUserAuthEvents(request)).thenReturn(expectedResult);

    var actualResult = service.adminListUserAuthEvents(request);
    assertThat("Unexpected result.", actualResult, sameInstance(expectedResult));
  }

  @Test
  void shouldWrapAdminRemoveUserFromGroup() {
    var request = new AdminRemoveUserFromGroupRequest();
    var expectedResult = new AdminRemoveUserFromGroupResult();
    when(cognitoIdp.adminRemoveUserFromGroup(request)).thenReturn(expectedResult);

    var actualResult = service.adminRemoveUserFromGroup(request);
    assertThat("Unexpected result.", actualResult, sameInstance(expectedResult));
  }

  @Test
  void shouldWrapAdminSetUserMfaPreference() {
    var request = new AdminSetUserMFAPreferenceRequest();
    var expectedResult = new AdminSetUserMFAPreferenceResult();
    when(cognitoIdp.adminSetUserMFAPreference(request)).thenReturn(expectedResult);

    var actualResult = service.adminSetUserMfaPreference(request);
    assertThat("Unexpected result.", actualResult, sameInstance(expectedResult));
  }

  @Test
  void shouldWrapListUsers() {
    var request = new ListUsersRequest();
    var expectedResult = new ListUsersResult();
    when(cognitoIdp.listUsers(request)).thenReturn(expectedResult);

    var actualResult = service.listUsers(request);
    assertThat("Unexpected result.", actualResult, sameInstance(expectedResult));
  }
}
