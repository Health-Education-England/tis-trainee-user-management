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

package uk.nhs.tis.trainee.usermanagement.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.Getter;

/**
 * An abstract representation of a record event.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class RecordEvent {

  private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
      .findAndAddModules()
      .build();

  /**
   * Unpack the record node of the event JSON.
   *
   * @param recordNode The node to unpack.
   */
  @JsonProperty("record")
  private void unpackRecord(JsonNode recordNode) {
    unpackData(recordNode.get("data"));
  }

  /**
   * Unpack the data node of the event JSON.
   *
   * @param dataNode The data node to unpack.
   */
  protected abstract void unpackData(JsonNode dataNode);

  /**
   * Get a configured object mapper to use for object conversion.
   *
   * @return The configured object mapper.
   */
  protected ObjectMapper getObjectMapper() {
    return OBJECT_MAPPER;
  }
}
