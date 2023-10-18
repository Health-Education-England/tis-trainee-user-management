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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.nhs.tis.trainee.usermanagement.dto.UserAccountDetailsDto;
import uk.nhs.tis.trainee.usermanagement.service.UserAccountService;

class UserAccountResourceTest {

  private static final String USERNAME = "username";

  private MockMvc mockMvc;
  private UserAccountService service;

  @BeforeEach
  void setUp() {
    service = mock(UserAccountService.class);
    UserAccountResource resource = new UserAccountResource(service);
    mockMvc = MockMvcBuilders.standaloneSetup(resource).build();
  }

  @Test
  void shouldGetUserAccountDetails() throws Exception {
    List<String> groups = List.of("GROUP_1", "GROUP_2");
    Instant ACCOUNT_CREATED = Instant.now();
    UserAccountDetailsDto userAccountDetails = new UserAccountDetailsDto("MFA_STATUS",
        "USER_STATUS", groups, ACCOUNT_CREATED);

    when(service.getUserAccountDetails(USERNAME)).thenReturn(userAccountDetails);

    mockMvc.perform(get("/api/user-account/details/{username}", USERNAME)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mfaStatus").value("MFA_STATUS"))
        .andExpect(jsonPath("$.userStatus").value("USER_STATUS"))
        .andExpect(jsonPath("$.groups").isArray())
        .andExpect(jsonPath("$.groups.length()").value(2))
        .andExpect(jsonPath("$.groups[0]").value("GROUP_1"))
        .andExpect(jsonPath("$.groups[1]").value("GROUP_2"))
        .andExpect(jsonPath("$.accountCreated").value(ACCOUNT_CREATED.toString()));
  }

  @Test
  void shouldResetMfa() throws Exception {
    mockMvc.perform(post("/api/user-account/reset-mfa/{username}", USERNAME)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    verify(service).resetUserAccountMfa(USERNAME);
  }

  @Test
  void shouldDeleteCognitoAccount() throws Exception {
    mockMvc.perform(delete("/api/user-account/{username}", USERNAME)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    verify(service).deleteCognitoAccount(USERNAME);
  }
}
