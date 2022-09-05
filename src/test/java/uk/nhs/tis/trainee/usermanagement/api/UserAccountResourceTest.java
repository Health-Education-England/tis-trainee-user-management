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

package uk.nhs.tis.trainee.usermanagement.api;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.model.AdminDeleteUserRequest;
import com.amazonaws.services.cognitoidp.model.AdminDeleteUserResult;
import com.amazonaws.services.cognitoidp.model.AdminGetUserResult;
import com.amazonaws.services.cognitoidp.model.AdminSetUserMFAPreferenceRequest;
import com.amazonaws.services.cognitoidp.model.AdminSetUserMFAPreferenceResult;
import com.amazonaws.services.cognitoidp.model.UserNotFoundException;
import com.amazonaws.services.cognitoidp.model.UserStatusType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserAccountResourceTest {

  private static final String USERNAME = "username";

  private MockMvc mockMvc;
  private AWSCognitoIdentityProvider cognitoIdp;

  @BeforeEach
  void setUp() {
    cognitoIdp = mock(AWSCognitoIdentityProvider.class);
    UserAccountResource resource = new UserAccountResource(cognitoIdp);
    mockMvc = MockMvcBuilders.standaloneSetup(resource).build();
  }

  @Test
  void shouldReturnNoAccountWhenUserNotFound() throws Exception {
    when(cognitoIdp.adminGetUser(any())).thenThrow(UserNotFoundException.class);

    mockMvc.perform(get("/api/user-account/details/{username}", USERNAME)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mfaStatus").value("NO_ACCOUNT"))
        .andExpect(jsonPath("$.userStatus").value("NO_ACCOUNT"));
  }

  @Test
  void shouldGetUserStatusWhenUserStatusConfirmed() throws Exception {
    AdminGetUserResult result = new AdminGetUserResult();
    result.setUserStatus(UserStatusType.CONFIRMED);

    when(cognitoIdp.adminGetUser(any())).thenReturn(result);

    mockMvc.perform(get("/api/user-account/details/{username}", USERNAME)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userStatus").value(UserStatusType.CONFIRMED.toString()));
  }

  @Test
  void shouldGetUserStatusWhenUserStatusUnconfirmed() throws Exception {
    AdminGetUserResult result = new AdminGetUserResult();
    result.setUserStatus(UserStatusType.UNCONFIRMED);

    when(cognitoIdp.adminGetUser(any())).thenReturn(result);

    mockMvc.perform(get("/api/user-account/details/{username}", USERNAME)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userStatus").value(UserStatusType.UNCONFIRMED.toString()));
  }

  @Test
  void shouldGetUserStatusWhenUserStatusForcedPasswordChange() throws Exception {
    AdminGetUserResult result = new AdminGetUserResult();
    result.setUserStatus(UserStatusType.FORCE_CHANGE_PASSWORD);

    when(cognitoIdp.adminGetUser(any())).thenReturn(result);

    mockMvc.perform(get("/api/user-account/details/{username}", USERNAME)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userStatus").value(UserStatusType.FORCE_CHANGE_PASSWORD.toString()));
  }

  @Test
  void shouldReturnNoMfaWhenNoPreferredMfa() throws Exception {
    AdminGetUserResult result = new AdminGetUserResult();

    when(cognitoIdp.adminGetUser(any())).thenReturn(result);

    mockMvc.perform(get("/api/user-account/details/{username}", USERNAME)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mfaStatus").value("NO_MFA"));
  }

  @Test
  void shouldReturnPreferredMfaWhenPreferredMfa() throws Exception {
    AdminGetUserResult result = new AdminGetUserResult();
    result.setPreferredMfaSetting("PREFERRED_MFA");

    when(cognitoIdp.adminGetUser(any())).thenReturn(result);

    mockMvc.perform(get("/api/user-account/details/{username}", USERNAME)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mfaStatus").value("PREFERRED_MFA"));
  }

  @Test
  void shouldResetSmsMfa() throws Exception {
    ArgumentCaptor<AdminSetUserMFAPreferenceRequest> requestCaptor = ArgumentCaptor.forClass(
        AdminSetUserMFAPreferenceRequest.class);

    when(cognitoIdp.adminSetUserMFAPreference(requestCaptor.capture())).thenReturn(
        new AdminSetUserMFAPreferenceResult());

    mockMvc.perform(post("/api/user-account/reset-mfa/{username}", USERNAME)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    AdminSetUserMFAPreferenceRequest request = requestCaptor.getValue();
    assertThat("Unexpected SMS enabled flag.", request.getSMSMfaSettings().getEnabled(), is(false));
  }

  @Test
  void shouldResetTotpMfa() throws Exception {
    ArgumentCaptor<AdminSetUserMFAPreferenceRequest> requestCaptor = ArgumentCaptor.forClass(
        AdminSetUserMFAPreferenceRequest.class);

    when(cognitoIdp.adminSetUserMFAPreference(requestCaptor.capture())).thenReturn(
        new AdminSetUserMFAPreferenceResult());

    mockMvc.perform(post("/api/user-account/reset-mfa/{username}", USERNAME)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    AdminSetUserMFAPreferenceRequest request = requestCaptor.getValue();
    assertThat("Unexpected TOTP enabled flag.", request.getSoftwareTokenMfaSettings().getEnabled(),
        is(false));
  }

  @Test
  void shouldDeleteCognitoAccount() throws Exception {
    ArgumentCaptor<AdminDeleteUserRequest> requestCaptor = ArgumentCaptor.forClass(
        AdminDeleteUserRequest.class);

    when(cognitoIdp.adminDeleteUser(requestCaptor.capture())).thenReturn(
        new AdminDeleteUserResult());

    mockMvc.perform(post("/api/user-account/delete-account/{username}", USERNAME)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    AdminDeleteUserRequest request = requestCaptor.getValue();
    assertThat("Unexpected delete account username.", request.getUsername(), is(USERNAME));
  }
}
