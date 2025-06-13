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
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
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
import com.amazonaws.services.cognitoidp.model.AuthEventType;
import com.amazonaws.services.cognitoidp.model.ChallengeResponseType;
import com.amazonaws.services.cognitoidp.model.EventContextDataType;
import com.amazonaws.services.cognitoidp.model.EventResponseType;
import com.amazonaws.services.cognitoidp.model.EventType;
import com.amazonaws.services.cognitoidp.model.GroupType;
import com.amazonaws.services.cognitoidp.model.ListUsersRequest;
import com.amazonaws.services.cognitoidp.model.ListUsersResult;
import com.amazonaws.services.cognitoidp.model.SMSMfaSettingsType;
import com.amazonaws.services.cognitoidp.model.SoftwareTokenMfaSettingsType;
import com.amazonaws.services.cognitoidp.model.TooManyRequestsException;
import com.amazonaws.services.cognitoidp.model.UserNotFoundException;
import com.amazonaws.services.cognitoidp.model.UserStatusType;
import com.amazonaws.services.cognitoidp.model.UserType;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import uk.nhs.tis.trainee.usermanagement.dto.UserAccountDetailsDto;
import uk.nhs.tis.trainee.usermanagement.dto.UserLoginDetailsDto;
import uk.nhs.tis.trainee.usermanagement.enumeration.MfaType;

class UserAccountServiceTest {

  private static final String USER_POOL_ID = "region_abc213";
  private static final String EMAIL = "joe.bloggs@fake.email";
  private static final String GROUP_1 = "user-group-one";
  private static final String GROUP_2 = "user-group-two";

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
  private AWSCognitoIdentityProvider cognitoIdp;
  private Cache cache;
  private EventPublishService eventPublishService;
  private MetricsService metricsService;

  @BeforeEach
  void setUp() {
    cognitoIdp = mock(AWSCognitoIdentityProvider.class);
    cache = mock(Cache.class);

    CacheManager cacheManager = mock(CacheManager.class);
    when(cacheManager.getCache("UserId")).thenReturn(cache);

    eventPublishService = mock(EventPublishService.class);
    metricsService = mock(MetricsService.class);

    service = spy(new UserAccountService(cognitoIdp, USER_POOL_ID, cacheManager,
        eventPublishService, metricsService));

    // Initialize groups as an empty list instead of null, which reflects default AWS API behaviour.
    AdminListGroupsForUserResult mockGroupResult = new AdminListGroupsForUserResult();
    mockGroupResult.setGroups(List.of());
    when(cognitoIdp.adminListGroupsForUser(any())).thenReturn(mockGroupResult);
  }

  @Test
  void shouldReturnNoAccountDetailsWhenUserNotFoundGettingUser() {
    when(cognitoIdp.adminGetUser(any())).thenThrow(UserNotFoundException.class);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(EMAIL);
    assertThat("Unexpected MFA status.", userAccountDetails.getMfaStatus(), is("NO_ACCOUNT"));
    assertThat("Unexpected user status.", userAccountDetails.getUserStatus(), is("NO_ACCOUNT"));
    assertThat("Unexpected user groups size.", userAccountDetails.getGroups().size(), is(0));
    assertThat("Unexpected account created", userAccountDetails.getAccountCreated(), nullValue());
  }

  @Test
  void shouldReturnPartialAccountDetailsWhenUserNotFoundGettingGroups() {
    when(cognitoIdp.adminGetUser(any())).thenReturn(new AdminGetUserResult());
    when(cognitoIdp.adminListGroupsForUser(any())).thenThrow(UserNotFoundException.class);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(EMAIL);
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

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(EMAIL);
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

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(EMAIL);
    assertThat("Unexpected account created.", userAccountDetails.getAccountCreated(),
        is(createdAtWithDatePrecision));
  }

  @Test
  void shouldGetUserStatusWhenUserStatusConfirmed() {
    AdminGetUserResult result = new AdminGetUserResult();
    result.setUserStatus(UserStatusType.CONFIRMED);

    when(cognitoIdp.adminGetUser(any())).thenReturn(result);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(EMAIL);
    assertThat("Unexpected user status.", userAccountDetails.getUserStatus(),
        is(UserStatusType.CONFIRMED.toString()));
  }

  @Test
  void shouldGetUserStatusWhenUserStatusUnconfirmed() {
    AdminGetUserResult result = new AdminGetUserResult();
    result.setUserStatus(UserStatusType.UNCONFIRMED);

    when(cognitoIdp.adminGetUser(any())).thenReturn(result);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(EMAIL);
    assertThat("Unexpected user status.", userAccountDetails.getUserStatus(),
        is(UserStatusType.UNCONFIRMED.toString()));
  }


  @Test
  void shouldGetUserStatusWhenUserStatusForcedPasswordChange() {
    AdminGetUserResult result = new AdminGetUserResult();
    result.setUserStatus(UserStatusType.FORCE_CHANGE_PASSWORD);

    when(cognitoIdp.adminGetUser(any())).thenReturn(result);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(EMAIL);
    assertThat("Unexpected user status.", userAccountDetails.getUserStatus(),
        is(UserStatusType.FORCE_CHANGE_PASSWORD.toString()));
  }

  @Test
  void shouldReturnNoMfaWhenNoPreferredMfa() {
    AdminGetUserResult result = new AdminGetUserResult();

    when(cognitoIdp.adminGetUser(any())).thenReturn(result);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(EMAIL);
    assertThat("Unexpected MFA status.", userAccountDetails.getMfaStatus(), is("NO_MFA"));
  }

  @Test
  void shouldReturnPreferredMfaWhenPreferredMfa() {
    AdminGetUserResult result = new AdminGetUserResult();
    result.setPreferredMfaSetting("PREFERRED_MFA");

    when(cognitoIdp.adminGetUser(any())).thenReturn(result);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(EMAIL);
    assertThat("Unexpected MFA status.", userAccountDetails.getMfaStatus(), is("PREFERRED_MFA"));
  }

  @Test
  void shouldReturnNoGroupsWhenUserNotFoundRetrievingGroups() {
    when(cognitoIdp.adminGetUser(any())).thenReturn(new AdminGetUserResult());
    when(cognitoIdp.adminListGroupsForUser(any())).thenThrow(UserNotFoundException.class);

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(EMAIL);
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

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(EMAIL);
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

    UserAccountDetailsDto userAccountDetails = service.getUserAccountDetails(EMAIL);
    List<String> groups = userAccountDetails.getGroups();
    assertThat("Unexpected user groups.", groups, notNullValue());
    assertThat("Unexpected user groups.", groups.size(), is(2));
    assertThat("Unexpected user groups.", groups, hasItems(GROUP_1, GROUP_2));
  }

  @Test
  void shouldThrowExceptionUpdatingEmailWhenEmailBelongsToAnotherAccount() {
    String newEmail = "new.email@example.com";

    ListUsersResult listUsersResult = new ListUsersResult()
        .withUsers(
            new UserType().withUsername(USER_ID_2)
        );

    when(cognitoIdp.listUsers(any())).thenReturn(listUsersResult);

    assertThrows(IllegalArgumentException.class,
        () -> service.updateContactDetails(USER_ID_1, newEmail, FORENAMES_1, SURNAME_1));

    verify(cognitoIdp, never()).adminUpdateUserAttributes(any());
  }

  @Test
  void shouldOnlyUpdateNamesWhenTheEmailHasNotChanged() {
    String newEmail = "new.email@example.com";

    ListUsersResult listUsersResult = new ListUsersResult()
        .withUsers(
            new UserType().withUsername(USER_ID_1)
        );

    when(cognitoIdp.listUsers(any())).thenReturn(listUsersResult);

    service.updateContactDetails(USER_ID_1, newEmail, FORENAMES_2, SURNAME_2);

    ArgumentCaptor<AdminUpdateUserAttributesRequest> updateRequestCaptor = ArgumentCaptor.forClass(
        AdminUpdateUserAttributesRequest.class);
    verify(cognitoIdp).adminUpdateUserAttributes(updateRequestCaptor.capture());

    AdminUpdateUserAttributesRequest updateRequest = updateRequestCaptor.getValue();

    assertThat("Unexpected user pool.", updateRequest.getUserPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected user ID.", updateRequest.getUsername(), is(USER_ID_1));

    List<AttributeType> userAttributes = updateRequest.getUserAttributes();
    assertThat("Unexpected attribute count.", userAttributes.size(), is(2));

    AttributeType userAttribute = userAttributes.get(0);
    assertThat("Unexpected attribute name.", userAttribute.getName(), is(ATTRIBUTE_FAMILY_NAME));
    assertThat("Unexpected attribute value.", userAttribute.getValue(), is(SURNAME_2));

    userAttribute = userAttributes.get(1);
    assertThat("Unexpected attribute name.", userAttribute.getName(), is(ATTRIBUTE_GIVEN_NAME));
    assertThat("Unexpected attribute value.", userAttribute.getValue(), is(FORENAMES_2));
  }

  @Test
  void shouldUpdateEmailAndNamesWhenNewEmailNotExistsInUserPool() {
    String newEmail = "new.email@example.com";

    ArgumentCaptor<ListUsersRequest> listUsersCaptor = ArgumentCaptor.forClass(
        ListUsersRequest.class);
    when(cognitoIdp.listUsers(listUsersCaptor.capture()))
        .thenThrow(UserNotFoundException.class)
        .thenReturn(new ListUsersResult()
            .withUsers(
                new UserType().withAttributes(
                    new AttributeType().withName("name").withValue("value")
                )
            )
        );

    service.updateContactDetails(USER_ID_1, newEmail, FORENAMES_2, SURNAME_2);

    List<ListUsersRequest> listUsersRequests = listUsersCaptor.getAllValues();
    assertThat("Unexpected list users request count.", listUsersRequests.size(), is(2));

    ListUsersRequest listUsersRequest = listUsersRequests.get(0);
    assertThat("Unexpected filter.", listUsersRequest.getFilter(),
        is(String.format("email=\"%s\"", newEmail)));

    ArgumentCaptor<AdminUpdateUserAttributesRequest> updateRequestCaptor = ArgumentCaptor.forClass(
        AdminUpdateUserAttributesRequest.class);
    verify(cognitoIdp).adminUpdateUserAttributes(updateRequestCaptor.capture());

    AdminUpdateUserAttributesRequest updateRequest = updateRequestCaptor.getValue();

    assertThat("Unexpected user pool.", updateRequest.getUserPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected user ID.", updateRequest.getUsername(), is(USER_ID_1));

    List<AttributeType> userAttributes = updateRequest.getUserAttributes();
    assertThat("Unexpected attribute count.", userAttributes.size(), is(4));

    AttributeType userAttribute = userAttributes.get(0);
    assertThat("Unexpected attribute name.", userAttribute.getName(), is(ATTRIBUTE_FAMILY_NAME));
    assertThat("Unexpected attribute value.", userAttribute.getValue(), is(SURNAME_2));

    userAttribute = userAttributes.get(1);
    assertThat("Unexpected attribute name.", userAttribute.getName(), is(ATTRIBUTE_GIVEN_NAME));
    assertThat("Unexpected attribute value.", userAttribute.getValue(), is(FORENAMES_2));

    userAttribute = userAttributes.get(2);
    assertThat("Unexpected attribute name.", userAttribute.getName(), is(ATTRIBUTE_EMAIL));
    assertThat("Unexpected attribute value.", userAttribute.getValue(), is(newEmail));

    userAttribute = userAttributes.get(3);
    assertThat("Unexpected attribute name.", userAttribute.getName(), is(ATTRIBUTE_EMAIL_VERIFIED));
    assertThat("Unexpected attribute value.", userAttribute.getValue(), is("true"));
  }

  @Test
  void shouldPublishEventAfterUpdatingEmail() {
    String previousEmail = "previous.email@example.com";
    String newEmail = "new.email@example.com";

    ArgumentCaptor<ListUsersRequest> listUsersCaptor = ArgumentCaptor.forClass(
        ListUsersRequest.class);
    when(cognitoIdp.listUsers(listUsersCaptor.capture()))
        .thenThrow(UserNotFoundException.class)
        .thenReturn(new ListUsersResult()
            .withUsers(
                new UserType().withAttributes(
                    new AttributeType().withName(ATTRIBUTE_EMAIL).withValue(previousEmail),
                    new AttributeType().withName(ATTRIBUTE_TRAINEE_ID).withValue(TRAINEE_ID_1)
                )
            ));

    service.updateContactDetails(USER_ID_1, newEmail, FORENAMES_1, SURNAME_1);

    List<ListUsersRequest> listUsersRequests = listUsersCaptor.getAllValues();
    assertThat("Unexpected list users request count.", listUsersRequests.size(), is(2));

    ListUsersRequest listUsersRequest = listUsersRequests.get(1);
    assertThat("Unexpected filter.", listUsersRequest.getFilter(),
        is(String.format("sub=\"%s\"", USER_ID_1)));

    verify(eventPublishService).publishEmailUpdateEvent(USER_ID_1, TRAINEE_ID_1, previousEmail,
        newEmail);
  }

  @ParameterizedTest
  @EnumSource(MfaType.class)
  void shouldResetMfa(MfaType mfaType) {
    ArgumentCaptor<AdminSetUserMFAPreferenceRequest> requestCaptor = ArgumentCaptor.forClass(
        AdminSetUserMFAPreferenceRequest.class);

    when(cognitoIdp.adminSetUserMFAPreference(requestCaptor.capture())).thenReturn(
        new AdminSetUserMFAPreferenceResult());
    when(service.getUserMfaType(EMAIL)).thenReturn(mfaType);

    service.resetUserAccountMfa(EMAIL);

    AdminSetUserMFAPreferenceRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool.", request.getUserPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected username.", request.getUsername(), is(EMAIL));
    assertThat("Unexpected SMS enabled flag.", request.getSMSMfaSettings().getEnabled(), is(false));
    assertThat("Unexpected TOTP enabled flag.", request.getSoftwareTokenMfaSettings().getEnabled(),
        is(false));
  }

  @ParameterizedTest
  @EnumSource(MfaType.class)
  void shouldUpdateUserAttributeWhenMfaReset(MfaType mfaType) {
    when(cognitoIdp.adminSetUserMFAPreference(any())).thenReturn(
        new AdminSetUserMFAPreferenceResult());
    when(service.getUserMfaType(EMAIL)).thenReturn(mfaType);

    service.resetUserAccountMfa(EMAIL);

    ArgumentCaptor<AdminUpdateUserAttributesRequest> updateRequestCaptor = ArgumentCaptor.forClass(
        AdminUpdateUserAttributesRequest.class);
    verify(cognitoIdp).adminUpdateUserAttributes(updateRequestCaptor.capture());

    AdminUpdateUserAttributesRequest updateRequest = updateRequestCaptor.getValue();
    assertThat("Unexpected user pool.", updateRequest.getUserPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected username.", updateRequest.getUsername(), is(EMAIL));

    List<AttributeType> userAttributes = updateRequest.getUserAttributes();
    assertThat("Unexpected attribute count.", userAttributes, hasSize(1));

    AttributeType userAttribute = userAttributes.get(0);
    assertThat("Unexpected attribute name.", userAttribute.getName(), is("custom:mfaType"));
    assertThat("Unexpected attribute value.", userAttribute.getValue(), is("NO_MFA"));
  }

  @ParameterizedTest
  @EnumSource(MfaType.class)
  void shouldPublishMetricWhenMfaReset(MfaType mfaType) {
    when(cognitoIdp.adminSetUserMFAPreference(any())).thenReturn(
        new AdminSetUserMFAPreferenceResult());
    when(service.getUserMfaType(EMAIL)).thenReturn(mfaType);

    service.resetUserAccountMfa(EMAIL);

    verify(metricsService).incrementMfaResetCounter(mfaType);
  }

  @Test
  void shouldDeleteCognitoAccount() {
    ArgumentCaptor<AdminDeleteUserRequest> requestCaptor = ArgumentCaptor.forClass(
        AdminDeleteUserRequest.class);

    when(cognitoIdp.adminDeleteUser(requestCaptor.capture())).thenReturn(
        new AdminDeleteUserResult());

    ListUsersResult listUsersResult = new ListUsersResult()
        .withUsers(
            new UserType().withUserStatus(UserStatusType.CONFIRMED)
        );
    when(cognitoIdp.listUsers(any())).thenReturn(listUsersResult);
    when(service.getUserMfaType(any())).thenReturn(TOTP);

    service.deleteCognitoAccount(EMAIL);

    AdminDeleteUserRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool.", request.getUserPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected delete account username.", request.getUsername(), is(EMAIL));
    verify(metricsService).incrementDeleteAccountCounter(TOTP, UserStatusType.CONFIRMED);
  }

  @Test
  void shouldGetAccountByCurrentEmailWhenDeletingDuplicates() {
    ArgumentCaptor<ListUsersRequest> listUsersCaptor = ArgumentCaptor.forClass(
        ListUsersRequest.class);
    when(cognitoIdp.listUsers(listUsersCaptor.capture())).thenAnswer(inv -> {
      ListUsersRequest request = inv.getArgument(0);
      Pattern filterPattern = Pattern.compile("(email|sub)=\"(.+)\"");
      Matcher matcher = filterPattern.matcher(request.getFilter());
      matcher.find();

      boolean isGetByEmail = matcher.group(1).equals("email");
      String username = isGetByEmail ? USER_ID_1 : matcher.group(2);
      String email = isGetByEmail ? EMAIL : "no-match@example.com";

      return new ListUsersResult()
          .withUsers(
              new UserType()
                  .withUsername(username)
                  .withAttributes(
                      new AttributeType().withName(ATTRIBUTE_EMAIL).withValue(email),
                      new AttributeType().withName(ATTRIBUTE_TRAINEE_ID).withValue(TRAINEE_ID_1)
                  )
                  .withUserStatus(UserStatusType.CONFIRMED)
          );
    });

    service.deleteDuplicateAccounts(TRAINEE_ID_1, Set.of(USER_ID_1, USER_ID_2, USER_ID_3), EMAIL);

    ListUsersRequest listUsersRequest = listUsersCaptor.getAllValues().get(0);
    assertThat("Unexpected user pool.", listUsersRequest.getUserPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected list users filter.", listUsersRequest.getFilter(),
        is(String.format("email=\"%s\"", EMAIL)));
  }

  @Test
  void shouldDeleteDuplicatesWhenTisEmailMatches() {
    when(cognitoIdp.listUsers(any())).thenAnswer(inv -> {
      ListUsersRequest request = inv.getArgument(0);
      Pattern filterPattern = Pattern.compile("(email|sub)=\"(.+)\"");
      Matcher matcher = filterPattern.matcher(request.getFilter());
      matcher.find();

      boolean isGetByEmail = matcher.group(1).equals("email");
      String username = isGetByEmail ? USER_ID_1 : matcher.group(2);
      String email = isGetByEmail ? EMAIL : "no-match@example.com";

      return new ListUsersResult()
          .withUsers(
              new UserType()
                  .withUsername(username)
                  .withAttributes(
                      new AttributeType().withName(ATTRIBUTE_EMAIL).withValue(email),
                      new AttributeType().withName(ATTRIBUTE_TRAINEE_ID).withValue(TRAINEE_ID_1)
                  )
                  .withUserStatus(UserStatusType.CONFIRMED)
          );
    });

    Optional<String> accountId = service.deleteDuplicateAccounts(TRAINEE_ID_1,
        Set.of(USER_ID_1, USER_ID_2, USER_ID_3), EMAIL);

    assertThat("Unexpected remaining account.", accountId, is(Optional.of(USER_ID_1)));

    ArgumentCaptor<AdminDeleteUserRequest> deleteUserCaptor = ArgumentCaptor.forClass(
        AdminDeleteUserRequest.class);
    verify(cognitoIdp, times(2)).adminDeleteUser(deleteUserCaptor.capture());

    List<AdminDeleteUserRequest> deleteUserRequests = deleteUserCaptor.getAllValues();
    assertThat("Unexpected request count.", deleteUserRequests, hasSize(2));

    Set<String> deletedIds = deleteUserRequests.stream()
        .peek(request -> assertThat("Unexpected user pool.", request.getUserPoolId(),
            is(USER_POOL_ID)))
        .map(AdminDeleteUserRequest::getUsername)
        .collect(Collectors.toSet());
    assertThat("Unexpected deleted account count.", deletedIds, hasSize(2));
    assertThat("Unexpected deleted account.", deletedIds, hasItems(USER_ID_2, USER_ID_3));

    verify(metricsService, times(2)).incrementDeleteAccountCounter(any(), any());
  }

  @Test
  void shouldNotDeleteDuplicatesWhenTisEmailNotMatchesAndNoSuccessfulAuth() {
    ArgumentCaptor<ListUsersRequest> listUsersCaptor = ArgumentCaptor.forClass(
        ListUsersRequest.class);
    when(cognitoIdp.listUsers(listUsersCaptor.capture())).thenAnswer(inv -> {
      ListUsersRequest request = inv.getArgument(0);
      Pattern filterPattern = Pattern.compile("(email|sub)=\"(.+)\"");
      Matcher matcher = filterPattern.matcher(request.getFilter());
      matcher.find();

      String filterValue = matcher.group(2);
      String username = filterValue.equals(EMAIL) ? UUID.randomUUID().toString() : filterValue;

      return new ListUsersResult()
          .withUsers(
              new UserType()
                  .withUsername(username)
                  .withAttributes(
                      new AttributeType().withName(ATTRIBUTE_EMAIL)
                          .withValue("no-match@example.com"),
                      new AttributeType().withName(ATTRIBUTE_TRAINEE_ID).withValue(TRAINEE_ID_1)
                  )
                  .withUserStatus(UserStatusType.CONFIRMED)
          );
    });

    when(cognitoIdp.adminListUserAuthEvents(any())).thenReturn(
        new AdminListUserAuthEventsResult()
            .withAuthEvents(List.of())
    );

    Optional<String> accountId = service.deleteDuplicateAccounts(TRAINEE_ID_1,
        Set.of(USER_ID_1, USER_ID_2, USER_ID_3), EMAIL);

    assertThat("Unexpected remaining account.", accountId, is(Optional.empty()));

    ListUsersRequest listUsersRequest = listUsersCaptor.getAllValues().get(0);
    assertThat("Unexpected user pool.", listUsersRequest.getUserPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected list users filter.", listUsersRequest.getFilter(),
        is(String.format("email=\"%s\"", EMAIL)));

    verify(cognitoIdp, times(0)).adminDeleteUser(any());
    verify(metricsService, times(0)).incrementDeleteAccountCounter(any(), any());
  }

  @Test
  void shouldNotDeleteDuplicatesWhenTisEmailNotMatchesAndSuccessfulAuth() {
    ArgumentCaptor<ListUsersRequest> listUsersCaptor = ArgumentCaptor.forClass(
        ListUsersRequest.class);
    when(cognitoIdp.listUsers(listUsersCaptor.capture())).thenAnswer(inv -> {
      ListUsersRequest request = inv.getArgument(0);
      Pattern filterPattern = Pattern.compile("(email|sub)=\"(.+)\"");
      Matcher matcher = filterPattern.matcher(request.getFilter());
      matcher.find();

      String filterValue = matcher.group(2);
      String username = filterValue.equals(EMAIL) ? UUID.randomUUID().toString() : filterValue;

      return new ListUsersResult()
          .withUsers(
              new UserType()
                  .withUsername(username)
                  .withAttributes(
                      new AttributeType().withName(ATTRIBUTE_EMAIL)
                          .withValue("no-match@example.com"),
                      new AttributeType().withName(ATTRIBUTE_TRAINEE_ID).withValue(TRAINEE_ID_1)
                  )
                  .withUserStatus(UserStatusType.CONFIRMED)
          );
    });

    when(cognitoIdp.adminListUserAuthEvents(any())).thenReturn(
        new AdminListUserAuthEventsResult()
            .withAuthEvents(List.of(
                new AuthEventType()
                    .withCreationDate(Date.from(Instant.now()))
                    .withEventType(EventType.SignIn)
                    .withEventResponse(EventResponseType.Pass)
            ))
    );

    Optional<String> accountId = service.deleteDuplicateAccounts(TRAINEE_ID_1,
        Set.of(USER_ID_1, USER_ID_2, USER_ID_3), EMAIL);

    assertThat("Unexpected remaining account.", accountId, is(Optional.empty()));

    ListUsersRequest listUsersRequest = listUsersCaptor.getAllValues().get(0);
    assertThat("Unexpected user pool.", listUsersRequest.getUserPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected list users filter.", listUsersRequest.getFilter(),
        is(String.format("email=\"%s\"", EMAIL)));

    verify(cognitoIdp, times(0)).adminDeleteUser(any());
    verify(metricsService, times(0)).incrementDeleteAccountCounter(any(), any());
  }

  @Test
  void shouldRetryPaginatingThroughAuthEventsWhenRateLimitedGettingAuthEvents() {
    when(cognitoIdp.listUsers(any())).thenAnswer(inv -> {
      ListUsersRequest request = inv.getArgument(0);
      Pattern filterPattern = Pattern.compile("(email|sub)=\"(.+)\"");
      Matcher matcher = filterPattern.matcher(request.getFilter());
      matcher.find();

      String filterValue = matcher.group(2);
      String username = filterValue.equals(EMAIL) ? UUID.randomUUID().toString() : filterValue;

      return new ListUsersResult()
          .withUsers(
              new UserType()
                  .withUsername(username)
                  .withAttributes(
                      new AttributeType().withName(ATTRIBUTE_EMAIL)
                          .withValue("no-match@example.com"),
                      new AttributeType().withName(ATTRIBUTE_TRAINEE_ID).withValue(TRAINEE_ID_1)
                  )
                  .withUserStatus(UserStatusType.CONFIRMED)
          );
    });

    ArgumentCaptor<AdminListUserAuthEventsRequest> authEventCaptor = ArgumentCaptor.forClass(
        AdminListUserAuthEventsRequest.class);
    when(cognitoIdp.adminListUserAuthEvents(authEventCaptor.capture()))
        .thenReturn(new AdminListUserAuthEventsResult().withAuthEvents(List.of())
            .withNextToken("tokenforpage2"))
        .thenThrow(TooManyRequestsException.class)
        .thenReturn(new AdminListUserAuthEventsResult().withAuthEvents(List.of()));

    service.deleteDuplicateAccounts(TRAINEE_ID_1, Set.of(USER_ID_1), EMAIL);

    List<AdminListUserAuthEventsRequest> requests = authEventCaptor.getAllValues();
    assertThat("Unexpected request count.", requests, hasSize(3));
    AdminListUserAuthEventsRequest request1 = requests.get(0);
    assertThat("Unexpected pagination token.", request1.getNextToken(), nullValue());
    AdminListUserAuthEventsRequest request2 = requests.get(1);
    assertThat("Unexpected pagination token.", request2.getNextToken(), is("tokenforpage2"));
    AdminListUserAuthEventsRequest request3 = requests.get(2);
    assertThat("Unexpected pagination token.", request3.getNextToken(), is("tokenforpage2"));
  }

  @Test
  void shouldEnrollToUserGroup() {
    ArgumentCaptor<AdminAddUserToGroupRequest> requestCaptor = ArgumentCaptor.forClass(
        AdminAddUserToGroupRequest.class);

    when(cognitoIdp.adminAddUserToGroup(requestCaptor.capture())).thenReturn(
        new AdminAddUserToGroupResult());

    service.enrollToUserGroup(EMAIL, GROUP_1);

    AdminAddUserToGroupRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool.", request.getUserPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected username.", request.getUsername(), is(EMAIL));
    assertThat("Unexpected user group.", request.getGroupName(), is(GROUP_1));
  }

  @Test
  void shouldWithdrawFromUserGroup() {
    ArgumentCaptor<AdminRemoveUserFromGroupRequest> requestCaptor = ArgumentCaptor.forClass(
        AdminRemoveUserFromGroupRequest.class);

    when(cognitoIdp.adminRemoveUserFromGroup(requestCaptor.capture())).thenReturn(
        new AdminRemoveUserFromGroupResult());

    service.withdrawFromUserGroup(EMAIL, GROUP_1);

    AdminRemoveUserFromGroupRequest request = requestCaptor.getValue();
    assertThat("Unexpected user pool.", request.getUserPoolId(), is(USER_POOL_ID));
    assertThat("Unexpected username.", request.getUsername(), is(EMAIL));
    assertThat("Unexpected user group.", request.getGroupName(), is(GROUP_1));
  }

  @Test
  void shouldReturnNoLoginsWhenUserNotFoundGettingUserLogins() {
    when(cognitoIdp.adminListUserAuthEvents(any())).thenThrow(UserNotFoundException.class);

    List<UserLoginDetailsDto> userLoginDetails = service.getUserLoginDetails(EMAIL);
    assertThat("Unexpected logins.", userLoginDetails.size(), is(0));
  }

  @Test
  void shouldGetUserLoginsWhenUserFoundGettingUserLogins() {
    Date eventDate = new Date();

    ChallengeResponseType challenge1 = new ChallengeResponseType()
        .withChallengeName("Password")
        .withChallengeResponse("Success");
    ChallengeResponseType challenge2 = new ChallengeResponseType()
        .withChallengeName("Mfa")
        .withChallengeResponse("Failure");

    EventContextDataType eventContextDataType = new EventContextDataType()
        .withDeviceName("DEVICE");

    AuthEventType authEventTypeOne = new AuthEventType()
        .withCreationDate(eventDate)
        .withEventId("EVENT_ID")
        .withEventType("EVENT_TYPE")
        .withEventResponse("RESPONSE")
        .withChallengeResponses(List.of(challenge1, challenge2))
        .withEventContextData(eventContextDataType);

    Date eventDateTwo = new Date();

    ChallengeResponseType challenge3 = new ChallengeResponseType()
        .withChallengeName("Password")
        .withChallengeResponse("Failure");

    EventContextDataType eventContextDataTypeTwo = new EventContextDataType()
        .withDeviceName("DEVICE_2");

    AuthEventType authEventTypeTwo = new AuthEventType()
        .withCreationDate(eventDateTwo)
        .withEventId("EVENT_ID_2")
        .withEventType("EVENT_TYPE_2")
        .withEventResponse("RESPONSE_2")
        .withChallengeResponses(List.of(challenge3))
        .withEventContextData(eventContextDataTypeTwo);

    AdminListUserAuthEventsResult result = new AdminListUserAuthEventsResult();
    result.setAuthEvents(List.of(authEventTypeOne, authEventTypeTwo));

    when(cognitoIdp.adminListUserAuthEvents(any())).thenReturn(result);

    List<UserLoginDetailsDto> userLogins = service.getUserLoginDetails(EMAIL);

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

    when(cognitoIdp.listUsers(any())).thenReturn(result);

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
  void shouldRetryPaginatingThroughAllUserAccountIdsWhenRateLimitedGettingUserAccountIds() {
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
    when(cognitoIdp.listUsers(requestCaptor.capture()))
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
    assertThat("Unexpected pagination token.", request1.getPaginationToken(), nullValue());
    ListUsersRequest request2 = requests.get(1);
    assertThat("Unexpected pagination token.", request2.getPaginationToken(), is("tokenforpage2"));
    ListUsersRequest request3 = requests.get(2);
    assertThat("Unexpected pagination token.", request3.getPaginationToken(), is("tokenforpage2"));
  }

  @Test
  void shouldCacheDuplicateUserAccountIdsWhenGettingUserAccountIds() {
    UserType user = new UserType().withAttributes(
        new AttributeType().withName(ATTRIBUTE_TRAINEE_ID).withValue(TRAINEE_ID_1),
        new AttributeType().withName(ATTRIBUTE_USER_ID).withValue(USER_ID_2)
    );

    ListUsersResult result = new ListUsersResult();
    result.setUsers(List.of(user));

    when(cognitoIdp.listUsers(any())).thenReturn(result);

    when(cache.get(TRAINEE_ID_1, Set.class)).thenReturn(new HashSet<>(Set.of(USER_ID_1)));

    service.getUserAccountIds(TRAINEE_ID_1);

    verify(cache).put(TRAINEE_ID_1, Set.of(USER_ID_1, USER_ID_2));
  }

  @Test
  void shouldGetUserAccountIdsFromCache() {
    ListUsersResult result = new ListUsersResult();
    result.setUsers(List.of());

    when(cognitoIdp.listUsers(any())).thenReturn(result);

    when(cache.get(TRAINEE_ID_1, Set.class)).thenReturn(Set.of(USER_ID_1, USER_ID_2));

    Set<String> userAccountIds = service.getUserAccountIds(TRAINEE_ID_1);

    assertThat("Unexpected user IDs count.", userAccountIds.size(), is(2));
    assertThat("Unexpected user IDs.", userAccountIds, hasItems(USER_ID_1, USER_ID_2));
  }

  @Test
  void shouldGetEmptyUserAccountIdsWhenAccountNotFoundAfterBuildingCache() {
    ListUsersResult result = new ListUsersResult();
    result.setUsers(List.of());

    when(cognitoIdp.listUsers(any())).thenReturn(result);

    when(cache.get(TRAINEE_ID_1, Set.class)).thenReturn(null);

    Set<String> userAccountIds = service.getUserAccountIds(TRAINEE_ID_1);

    assertThat("Unexpected user IDs count.", userAccountIds.size(), is(0));
  }

  @Test
  void shouldNotImmediatelyRepeatBuildingUserIdCache() {
    ListUsersResult result = new ListUsersResult();
    result.setUsers(List.of());

    when(cognitoIdp.listUsers(any())).thenReturn(result);

    service.getUserAccountIds(TRAINEE_ID_1);
    service.getUserAccountIds(TRAINEE_ID_2);

    verify(cognitoIdp, times(1)).listUsers(any());
  }

  @Test
  void shouldIdentifyTotpMfa() {
    AdminSetUserMFAPreferenceRequest mfaRequest = getMockedMfaRequest(true, false);
    when(service.getMfaPreferenceRequest(EMAIL)).thenReturn(mfaRequest);

    MfaType mfaType = service.getUserMfaType(EMAIL);
    assertThat("Unexpected MFA type.", mfaType, is(TOTP));
  }

  @Test
  void shouldIdentifySmsMfa() {
    AdminSetUserMFAPreferenceRequest mfaRequest = getMockedMfaRequest(false, true);
    when(service.getMfaPreferenceRequest(EMAIL)).thenReturn(mfaRequest);

    MfaType mfaType = service.getUserMfaType(EMAIL);
    assertThat("Unexpected MFA type.", mfaType, is(SMS));
  }

  @Test
  void shouldIdentifyNoMfa() {
    AdminSetUserMFAPreferenceRequest mfaRequest = getMockedMfaRequest(false, false);
    when(service.getMfaPreferenceRequest(EMAIL)).thenReturn(mfaRequest);

    MfaType mfaType = service.getUserMfaType(EMAIL);
    assertThat("Unexpected MFA type.", mfaType, is(NO_MFA));
  }

  /**
   * Helper function to get a mocked AdminSetUserMFAPreferenceRequest.
   *
   * @param enableTotp Whether to enable TOTP MFA in the request.
   * @param enableSms  Whether to enable SMS MFA in the request.
   * @return the mocked AdminSetUserMFAPreferenceRequest.
   */
  private AdminSetUserMFAPreferenceRequest getMockedMfaRequest(
      boolean enableTotp, boolean enableSms) {
    AdminSetUserMFAPreferenceRequest mfaRequest = mock(AdminSetUserMFAPreferenceRequest.class);
    SoftwareTokenMfaSettingsType totpMfa = new SoftwareTokenMfaSettingsType();
    totpMfa.setEnabled(enableTotp);
    when(mfaRequest.getSoftwareTokenMfaSettings()).thenReturn(totpMfa);
    SMSMfaSettingsType smsMfa = new SMSMfaSettingsType();
    smsMfa.setEnabled(enableSms);
    when(mfaRequest.getSMSMfaSettings()).thenReturn(smsMfa);
    return mfaRequest;
  }
}
