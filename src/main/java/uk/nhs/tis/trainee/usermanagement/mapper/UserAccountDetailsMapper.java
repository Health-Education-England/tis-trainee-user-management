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

package uk.nhs.tis.trainee.usermanagement.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.SPRING;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;
import uk.nhs.tis.trainee.usermanagement.dto.UserAccountDetailsDto;
import uk.nhs.tis.trainee.usermanagement.enumeration.MfaType;

@Mapper(componentModel = SPRING, imports = MfaType.class)
public interface UserAccountDetailsMapper {

  /**
   * Convert a {@link UserType} to a {@link UserAccountDetailsDto}.
   *
   * @param user   The user type to convert.
   * @param groups Groups to associate with the user.
   * @return The converted DTO.
   */
  @Mapping(target = "id", expression = "java(getId(user.attributes()))")
  @Mapping(target = "email", expression = "java(getEmail(user.attributes()))")
  @Mapping(target = "mfaStatus", expression = "java(getCustomMfaType(user.attributes()))")
  @Mapping(target = "userStatus", expression = "java(user.userStatusAsString())")
  @Mapping(target = "groups", source = "groups")
  @Mapping(target = "accountCreated", expression = "java(user.userCreateDate())")
  @Mapping(target = "traineeId", expression = "java(getTraineeId(user.attributes()))")
  UserAccountDetailsDto toDto(UserType user, List<String> groups);

  /**
   * Convert a {@link AdminGetUserResponse} to a {@link UserAccountDetailsDto}.
   *
   * @param result The result to convert.
   * @param groups Groups to associate with the user.
   * @return The converted DTO.
   */
  @Mapping(target = "id", expression = "java(getId(result.userAttributes()))")
  @Mapping(target = "email", expression = "java(getEmail(result.userAttributes()))")
  @Mapping(target = "mfaStatus",
      expression = "java(MfaType.fromAdminGetUserResult(result).toString())")
  @Mapping(target = "userStatus", expression = "java(result.userStatusAsString())")
  @Mapping(target = "groups", source = "groups")
  @Mapping(target = "accountCreated", expression = "java(result.userCreateDate())")
  @Mapping(target = "traineeId", expression = "java(getTraineeId(result.userAttributes()))")
  UserAccountDetailsDto toDto(AdminGetUserResponse result, List<String> groups);

  /**
   * Get the user ID from the user attributes.
   *
   * @param attributes The attribute list.
   * @return The attribute value, or null if not found in the list.
   */
  default String getId(List<AttributeType> attributes) {
    return getAttribute("sub", attributes);

  }

  /**
   * Get the email from the user attributes.
   *
   * @param attributes The attribute list.
   * @return The attribute value, or null if not found in the list.
   */
  default String getEmail(List<AttributeType> attributes) {
    return getAttribute("email", attributes);
  }

  /**
   * Get the custom MFA Type attribute from the user attributes.
   *
   * @param attributes The attribute list.
   * @return The attribute value, or null if not found in the list.
   */
  default String getCustomMfaType(List<AttributeType> attributes) {
    return getAttribute("custom:mfaType", attributes);
  }

  /**
   * Get the trainee ID from the user attributes.
   *
   * @param attributes The attribute list.
   * @return The attribute value, or null if not found in the list.
   */
  default String getTraineeId(List<AttributeType> attributes) {
    return getAttribute("custom:tisId", attributes);

  }

  /**
   * Get the named attribute from an {@link AttributeType} list.
   *
   * @param name       The name of the attribute to find.
   * @param attributes The attribute list.
   * @return The attribute value, or null if not found.
   */
  default String getAttribute(String name, List<AttributeType> attributes) {
    return attributes == null ? null : attributes.stream()
        .filter(ua -> ua.name().equals(name))
        .map(AttributeType::value)
        .findAny()
        .orElse(null);
  }
}
