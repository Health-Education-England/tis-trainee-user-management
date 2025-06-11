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

import com.amazonaws.services.cognitoidp.model.AdminGetUserResult;
import com.amazonaws.services.cognitoidp.model.AttributeType;
import com.amazonaws.services.cognitoidp.model.UserType;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
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
  @Mapping(target = "id", source = "user.attributes", qualifiedByName = "getId")
  @Mapping(target = "email", source = "user.attributes", qualifiedByName = "getEmail")
  @Mapping(target = "mfaStatus", source = "user.attributes", qualifiedByName = "getCustomMfaType")
  @Mapping(target = "userStatus", source = "user.userStatus")
  @Mapping(target = "groups", source = "groups")
  @Mapping(target = "accountCreated", source = "user.userCreateDate")
  @Mapping(target = "traineeId", source = "user.attributes", qualifiedByName = "getTraineeId")
  UserAccountDetailsDto toDto(UserType user, List<String> groups);

  /**
   * Convert a {@link AdminGetUserResult} to a {@link UserAccountDetailsDto}.
   *
   * @param result The result to convert.
   * @param groups Groups to associate with the user.
   * @return The converted DTO.
   */
  @Mapping(target = "id", source = "result.userAttributes", qualifiedByName = "getId")
  @Mapping(target = "email", source = "result.userAttributes", qualifiedByName = "getEmail")
  @Mapping(target = "mfaStatus",
      expression = "java(MfaType.fromAdminGetUserResult(result).toString())")
  @Mapping(target = "userStatus", source = "result.userStatus")
  @Mapping(target = "groups", source = "groups")
  @Mapping(target = "accountCreated", source = "result.userCreateDate")
  @Mapping(target = "traineeId", source = "result.userAttributes", qualifiedByName = "getTraineeId")
  UserAccountDetailsDto toDto(AdminGetUserResult result, List<String> groups);

  /**
   * Get the user ID from the user attributes.
   *
   * @param attributes The attribute list.
   * @return The attribute value, or null if not found in the list.
   */
  @Named("getId")
  default String getId(List<AttributeType> attributes) {
    return getAttribute("sub", attributes);

  }

  /**
   * Get the email from the user attributes.
   *
   * @param attributes The attribute list.
   * @return The attribute value, or null if not found in the list.
   */
  @Named("getEmail")
  default String getEmail(List<AttributeType> attributes) {
    return getAttribute("email", attributes);
  }

  /**
   * Get the custom MFA Type attribute from the user attributes.
   *
   * @param attributes The attribute list.
   * @return The attribute value, or null if not found in the list.
   */
  @Named("getCustomMfaType")
  default String getCustomMfaType(List<AttributeType> attributes) {
    return getAttribute("custom:mfaType", attributes);
  }

  /**
   * Get the trainee ID from the user attributes.
   *
   * @param attributes The attribute list.
   * @return The attribute value, or null if not found in the list.
   */
  @Named("getTraineeId")
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
        .filter(ua -> ua.getName().equals(name))
        .map(AttributeType::getValue)
        .findAny()
        .orElse(null);
  }
}
