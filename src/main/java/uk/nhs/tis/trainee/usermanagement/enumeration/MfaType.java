/*
 * The MIT License (MIT)
 *
 * Copyright 2024 Crown Copyright (Health Education England)
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

import com.amazonaws.services.cognitoidp.model.AdminGetUserResult;
import com.amazonaws.services.cognitoidp.model.ChallengeNameType;

public enum MfaType {
  NO_MFA, SMS, TOTP;

  /**
   * Get the MFA type from an {@link AdminGetUserResult}.
   *
   * @param result The result to use.
   * @return The MFA type.
   */
  public static MfaType fromAdminGetUserResult(AdminGetUserResult result) {
    String preferredMfaSetting = result.getPreferredMfaSetting();

    if (preferredMfaSetting == null) {
      return NO_MFA;
    }

    return switch (ChallengeNameType.fromValue(preferredMfaSetting)) {
      case SMS_MFA -> SMS;
      case SOFTWARE_TOKEN_MFA -> TOTP;
      default -> throw new IllegalArgumentException(
          "Cannot create enum from " + preferredMfaSetting + " value!");
    };
  }
}
