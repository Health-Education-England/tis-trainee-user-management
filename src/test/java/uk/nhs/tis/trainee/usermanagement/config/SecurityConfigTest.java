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

package uk.nhs.tis.trainee.usermanagement.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.transformuk.hee.tis.security.JwtAuthenticationProvider;
import com.transformuk.hee.tis.security.model.JwtAuthToken;
import com.transformuk.hee.tis.security.service.JwtProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private JwtAuthenticationProvider authenticationProvider;

  @MockBean
  private JwtProfileService jwtProfileService;

  @BeforeEach
  void setup() {
    // Setup authenticationProvider mock to simulate JWT extraction and authority check.
    when(authenticationProvider.authenticate(any())).thenAnswer(invocation -> {
      JwtAuthToken token = invocation.getArgument(0);
      String tokenValue = token.getToken();
      if ("admin-token".equals(tokenValue)) {
        return new JwtAuthToken("admin-token"); //FIXME add data admin role i.e. ROLE_TSS_DATA_ADMIN
      } else if ("user-token".equals(tokenValue)) {
        return new JwtAuthToken("user-token"); //FIXME add support admin authority i.e. trainee-support:modify
      }
      return null;
    });
  }

  /*
  TODO: fix these tests

  @Test
  void shouldAllowTssDataAdminToAccessMoveEndpoint() throws Exception {
    mockMvc.perform(post("/api/trainee-profile/move/123/to/456")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer admin-token"))
        .andExpect(status().isOk());
  }

  @Test
  void shouldDenyAccessToMoveEndpointWithoutTssDataAdminRole() throws Exception {
    mockMvc.perform(post("/api/trainee-profile/move/123/to/456")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer user-token"))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldDenyAnonymousAccessToMoveEndpoint() throws Exception {
    mockMvc.perform(post("/api/trainee-profile/move/123/to/456")
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }
  */
}
