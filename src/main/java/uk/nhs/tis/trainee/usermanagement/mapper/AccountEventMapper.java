/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
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

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uk.nhs.tis.trainee.usermanagement.dto.EmailUpdateEventDto;
import uk.nhs.tis.trainee.usermanagement.model.AccountEvent;
import uk.nhs.tis.trainee.usermanagement.model.AccountEvent.EmailUpdatedDetail;

/**
 * A mapper for converting {@link AccountEvent} to {@link EmailUpdateEventDto}.
 */
@Mapper(componentModel = SPRING)
public interface AccountEventMapper {

  /**
   * Convert an {@link AccountEvent} to an {@link EmailUpdateEventDto}.
   *
   * @param event The account event to convert.
   * @return The converted DTO.
   */
  @Mapping(target = "previousEmail",
      expression = "java(toEmailUpdatedDetail(event) != null " +
          "? toEmailUpdatedDetail(event).before() " +
          ": null)")
  @Mapping(target = "newEmail",
      expression = "java(toEmailUpdatedDetail(event) != null " +
          "? toEmailUpdatedDetail(event).after() " +
          ": null)")
  EmailUpdateEventDto toEmailUpdateEventDto(AccountEvent event);

  /**
   * Cast the event detail to {@link EmailUpdatedDetail}, returning null if not applicable.
   *
   * @param event The account event.
   * @return The detail cast to {@link EmailUpdatedDetail}, or null.
   */
  default EmailUpdatedDetail toEmailUpdatedDetail(AccountEvent event) {
    return event != null && event.detail() instanceof EmailUpdatedDetail detail ? detail : null;
  }
}