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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.nhs.tis.trainee.usermanagement.service.UserAccountService;

class UserGroupsResourceTest {

  private static final String USERNAME = "username";
  private static final String CONSULTATION_GROUP_NAME = "dsp-beta-consultation-group";

  private MockMvc mockMvc;
  private UserAccountService service;

  @BeforeEach
  void setUp() {
    service = mock(UserAccountService.class);
    UserGroupsResource resource = new UserGroupsResource(service, CONSULTATION_GROUP_NAME);
    mockMvc = MockMvcBuilders.standaloneSetup(resource).build();
  }

  @Test
  void shouldEnrollDspConsultationGroup() throws Exception {
    mockMvc.perform(post("/api/user-groups/dsp-consultants/enroll/{username}", USERNAME)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    verify(service).enrollToUserGroup(USERNAME, CONSULTATION_GROUP_NAME);
  }

  @Test
  void shouldWithdrawDspConsultation() throws Exception {
    mockMvc.perform(post("/api/user-groups/dsp-consultants/withdraw/{username}", USERNAME)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    verify(service).withdrawFromUserGroup(USERNAME, CONSULTATION_GROUP_NAME);
  }
}
