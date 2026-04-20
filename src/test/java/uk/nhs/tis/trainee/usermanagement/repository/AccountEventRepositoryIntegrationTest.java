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

package uk.nhs.tis.trainee.usermanagement.repository;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.data.domain.Sort.Direction.ASC;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.client.FindIterable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.tis.trainee.usermanagement.DockerImageNames;
import uk.nhs.tis.trainee.usermanagement.config.MongoConfiguration;
import uk.nhs.tis.trainee.usermanagement.model.AccountEvent;

@DataMongoTest
@Import(MongoConfiguration.class)
@ActiveProfiles("test")
@Testcontainers
class AccountEventRepositoryIntegrationTest {

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongoContainer = new MongoDBContainer(
      DockerImageNames.MONGO);

  @BeforeEach
  void setUp() {
    template.findAllAndRemove(new Query(), AccountEvent.class);
  }

  @Autowired
  private MongoTemplate template;

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      _id_      | _id
      userId    | userId
      traineeId | traineeId
      """)
  void shouldCreateSingleFieldIndexes(String indexName, String fieldName) {
    IndexOperations indexOperations = template.indexOps(AccountEvent.class);
    List<IndexInfo> indexes = indexOperations.getIndexInfo();

    assertThat("Unexpected index count.", indexes, hasSize(3));

    IndexInfo index = indexes.stream()
        .filter(i -> i.getName().equals(indexName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Expected index not found."));

    List<IndexField> indexFields = index.getIndexFields();
    assertThat("Unexpected index field count.", indexFields, hasSize(1));

    IndexField indexField = indexFields.get(0);
    assertThat("Unexpected index field key.", indexField.getKey(), is(fieldName));
    assertThat("Unexpected index field direction.", indexField.getDirection(), is(ASC));

    assertThat("Unexpected hidden index.", index.isHidden(), is(false));
    assertThat("Unexpected hashed index.", index.isHashed(), is(false));
    assertThat("Unexpected sparse index.", index.isSparse(), is(false));
    assertThat("Unexpected unique index.", index.isUnique(), is(false));
    assertThat("Unexpected wildcard index.", index.isWildcard(), is(false));
  }

  @Test
  void shouldStoreWithUuidIdType() throws JsonProcessingException {
    AccountEvent event = AccountEvent.builder().build();
    template.insert(event);

    String document = template.execute(AccountEvent.class, collection -> {
      FindIterable<Document> documents = collection.find();
      return documents.cursor().next().toJson();
    });

    ObjectNode jsonDocument = (ObjectNode) new ObjectMapper().readTree(document);

    String idType = jsonDocument.get("_id").get("$binary").get("subType").textValue();
    assertThat("Unexpected ID format.", idType, is("04"));
  }

  @Test
  void shouldSetCreatedWhenInserted() {
    AccountEvent event = AccountEvent.builder().build();
    template.insert(event);

    List<AccountEvent> savedRecords = template.find(new Query(), AccountEvent.class);
    assertThat("Unexpected saved records.", savedRecords.size(), is(1));
    AccountEvent savedRecord = savedRecords.get(0);
    assertThat("Unexpected saved record id.", savedRecord.id(), notNullValue());
    Instant roughlyNow = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    Instant roughlyCreated = savedRecord.created().truncatedTo(ChronoUnit.SECONDS);
    assertThat("Unexpected saved record created timestamp.", roughlyCreated.equals(roughlyNow),
        is(true));
  }
}
