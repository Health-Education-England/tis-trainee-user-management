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

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.nhs.tis.trainee.usermanagement.dto.UserAccountDetailsDto;
import uk.nhs.tis.trainee.usermanagement.dto.UserLoginDetailsDto;
import uk.nhs.tis.trainee.usermanagement.service.UserAccountService;

@WebMvcTest(UserAccountResource.class)
class UserAccountResourceTest {

  private static final String USERNAME = "username";

  @Autowired
  private MappingJackson2HttpMessageConverter jacksonMessageConverter;

  @MockBean
  private UserAccountService service;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    service = mock(UserAccountService.class);
    UserAccountResource resource = new UserAccountResource(service);
    mockMvc = MockMvcBuilders.standaloneSetup(resource)
        .setMessageConverters(jacksonMessageConverter)
        .build();
  }

  @Test
  void shouldReturnExistenceFalseWhenUserAccountNotExists() throws Exception {
    UserAccountDetailsDto userAccountDetails = new UserAccountDetailsDto("NO_ACCOUNT", "NO_ACCOUNT",
        List.of(), null);

    when(service.getUserAccountDetails(USERNAME)).thenReturn(userAccountDetails);

    mockMvc.perform(get("/api/user-account/exists/{username}", USERNAME)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exists").isBoolean())
        .andExpect(jsonPath("$.exists").value(false));
  }

  @Test
  void shouldReturnExistenceTrueWhenUserAccountExists() throws Exception {
    UserAccountDetailsDto userAccountDetails = new UserAccountDetailsDto("MFA_STATUS",
        "USER_STATUS", List.of(), Instant.now());

    when(service.getUserAccountDetails(USERNAME)).thenReturn(userAccountDetails);

    mockMvc.perform(get("/api/user-account/exists/{username}", USERNAME)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exists").isBoolean())
        .andExpect(jsonPath("$.exists").value(true));
  }

  @Test
  void shouldGetUserAccountDetails() throws Exception {
    List<String> groups = List.of("GROUP_1", "GROUP_2");
    Instant accountCreated = Instant.now();
    UserAccountDetailsDto userAccountDetails = new UserAccountDetailsDto("MFA_STATUS",
        "USER_STATUS", groups, accountCreated);

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
        .andExpect(jsonPath("$.accountCreated").value(accountCreated.toString()));
  }

  @Test
  void shouldGetUserLoginDetails() throws Exception {
    Instant eventInstant = Instant.now();
    Date eventDate = Date.from(eventInstant);
    DateTimeFormatter f = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSxxx");
    String eventDateString = OffsetDateTime.ofInstant(eventInstant, ZoneOffset.UTC).format(f);

    List<UserLoginDetailsDto> userLogins = List.of(
        new UserLoginDetailsDto(
            "EVENT_ID",
            eventDate,
            "EVENT",
            "RESULT",
            "CHALLENGES",
            "DEVICE"));

    when(service.getUserLoginDetails(USERNAME)).thenReturn(userLogins);

    mockMvc.perform(get("/api/user-account/logins/{username}", USERNAME)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].eventId").value("EVENT_ID"))
        .andExpect(jsonPath("$[0].eventDate").value(eventDateString))
        .andExpect(jsonPath("$[0].event").value("EVENT"))
        .andExpect(jsonPath("$[0].result").value("RESULT"))
        .andExpect(jsonPath("$[0].challenges").value("CHALLENGES"))
        .andExpect(jsonPath("$[0].device").value("DEVICE"));
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
