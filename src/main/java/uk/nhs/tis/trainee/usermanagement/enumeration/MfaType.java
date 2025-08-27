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

import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;

/**
 * An enumeration of allowed MFA types.
 */
public enum MfaType {
  EMAIL_OTP, NO_MFA, SMS_MFA, SOFTWARE_TOKEN_MFA;

  /**
   * Get the MFA type from an {@link AdminGetUserResponse}.
   *
   * @param result The result to use.
   * @return The MFA type.
   */
  public static MfaType fromAdminGetUserResult(AdminGetUserResponse result) {
    String preferredMfaSetting = result.preferredMfaSetting();

    if (preferredMfaSetting == null) {
      return NO_MFA;
    }

    // TODO: revert to check valid Cognito ChallengeNameType values once the SDK is upgraded.
    if (preferredMfaSetting.equals(NO_MFA.toString())) {
      throw new IllegalArgumentException(
          "Cannot create enum from " + preferredMfaSetting + " value!");
    }

    return MfaType.valueOf(preferredMfaSetting);
  }
}
