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

package uk.nhs.tis.trainee.usermanagement.enumeration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.amazonaws.services.cognitoidp.model.AdminGetUserResult;
import com.amazonaws.services.cognitoidp.model.ChallengeNameType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MfaTypeTest {

  @ParameterizedTest
  @ValueSource(strings = {"INVALID", "NO_MFA", "MFA_SETUP"})
  void shouldThrowExceptionForInvalidPreferredMfa(String preferredMfa) {
    AdminGetUserResult result = new AdminGetUserResult()
        .withPreferredMfaSetting(preferredMfa);

    assertThrows(IllegalArgumentException.class, () -> MfaType.fromAdminGetUserResult(result));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', nullValues = "null", textBlock = """
      SMS_MFA            | SMS_MFA
      SOFTWARE_TOKEN_MFA | SOFTWARE_TOKEN_MFA
      null               | NO_MFA
      """)
  void shouldReturnMfaTypeForValidPreferredMfa(ChallengeNameType preferredMfa, MfaType expected) {
    AdminGetUserResult result = new AdminGetUserResult()
        .withPreferredMfaSetting(preferredMfa == null ? null : preferredMfa.toString());

    MfaType mfaType = MfaType.fromAdminGetUserResult(result);

    assertThat("Unexpected MFA type.", mfaType, is(expected));
  }
}
