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
import com.amazonaws.services.cognitoidp.model.AdminAddUserToGroupRequest;
import com.amazonaws.services.cognitoidp.model.AdminAddUserToGroupResult;
import com.amazonaws.services.cognitoidp.model.AdminListGroupsForUserResult;
import com.amazonaws.services.cognitoidp.model.AdminRemoveUserFromGroupRequest;
import com.amazonaws.services.cognitoidp.model.AdminRemoveUserFromGroupResult;
import com.amazonaws.services.cognitoidp.model.GroupType;
import com.amazonaws.services.cognitoidp.model.UserNotFoundException;
import java.util.ArrayList;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserGroupsResourceTest {

  private static final String USERNAME = "username";
  private static final String CONSULTATION_GROUP_NAME = "dsp-beta-consultation-group";
  private static final String BETA_GROUP_NAME = "dsp-private-beta-group";
  private static final String COGNITO_USER_POOL_ID = "eu-west-2_dummy";

  private MockMvc mockMvc;
  private AWSCognitoIdentityProvider cognitoIdp;

  @BeforeEach
  void setUp() {
    cognitoIdp = mock(AWSCognitoIdentityProvider.class);
    UserGroupsResource resource = new UserGroupsResource(cognitoIdp);
    mockMvc = MockMvcBuilders.standaloneSetup(resource).build();
    ReflectionTestUtils.setField(resource, "userPoolId", COGNITO_USER_POOL_ID);
    ReflectionTestUtils.setField(resource, "consultationGroupName", CONSULTATION_GROUP_NAME);
  }

  @Test
  void shouldGetUserGroupsList() throws Exception {
    AdminListGroupsForUserResult result = new AdminListGroupsForUserResult();
    GroupType group1 = new GroupType();
    group1.setGroupName(CONSULTATION_GROUP_NAME);
    GroupType group2 = new GroupType();
    group2.setGroupName(BETA_GROUP_NAME);
    result.setGroups(List.of(group1, group2));

    when(cognitoIdp.adminListGroupsForUser(any())).thenReturn(result);

    mockMvc.perform(get("/api/user-groups/{username}", USERNAME)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userGroups[*]")
            .value(Matchers.hasItems(CONSULTATION_GROUP_NAME, BETA_GROUP_NAME)));
  }

  @Test
  void shouldReturnNoUserGroupWhenUserNotFound() throws Exception {
    when(cognitoIdp.adminListGroupsForUser(any())).thenThrow(UserNotFoundException.class);

    mockMvc.perform(get("/api/user-groups/{username}", USERNAME)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userGroups[*]").value(new ArrayList<>()));
  }

  @Test
  void shouldEnrollDspConsultationGroup() throws Exception {
    ArgumentCaptor<AdminAddUserToGroupRequest> requestCaptor = ArgumentCaptor.forClass(
        AdminAddUserToGroupRequest.class);

    when(cognitoIdp.adminAddUserToGroup(requestCaptor.capture())).thenReturn(
        new AdminAddUserToGroupResult());

    mockMvc.perform(post("/api/user-groups/dsp-consultants/enroll/{username}", USERNAME)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    AdminAddUserToGroupRequest request = requestCaptor.getValue();
    assertThat("Unexpected userpool.", request.getUserPoolId(), is(COGNITO_USER_POOL_ID));
    assertThat("Unexpected user group.", request.getGroupName(), is(CONSULTATION_GROUP_NAME));
    assertThat("Unexpected username.", request.getUsername(), is(USERNAME));
  }

  @Test
  void shouldWithdrawDspConsultation() throws Exception {
    ArgumentCaptor<AdminRemoveUserFromGroupRequest> requestCaptor = ArgumentCaptor.forClass(
        AdminRemoveUserFromGroupRequest.class);

    when(cognitoIdp.adminRemoveUserFromGroup(requestCaptor.capture())).thenReturn(
        new AdminRemoveUserFromGroupResult());

    mockMvc.perform(post("/api/user-groups/dsp-consultants/withdraw/{username}", USERNAME)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    AdminRemoveUserFromGroupRequest request = requestCaptor.getValue();
    assertThat("Unexpected userpool.", request.getUserPoolId(), is(COGNITO_USER_POOL_ID));
    assertThat("Unexpected user group.", request.getGroupName(), is(CONSULTATION_GROUP_NAME));
    assertThat("Unexpected username.", request.getUsername(), is(USERNAME));
  }
}
