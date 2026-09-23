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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.bulk.BulkWriteResult;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import uk.nhs.tis.trainee.usermanagement.model.AccountDetails;
import uk.nhs.tis.trainee.usermanagement.repository.AccountDetailsRepositoryCustom.AccountDetailsUpsertRequest;

class AccountDetailsRepositoryImplTest {

  private static final String SUB = UUID.randomUUID().toString();
  private static final String EMAIL = "test@example.com";
  private static final String TRAINEE_ID = UUID.randomUUID().toString();

  private AccountDetailsRepositoryImpl repository;
  private MongoOperations mongoOperations;

  @BeforeEach
  void setUp() {
    mongoOperations = mock(MongoOperations.class);
    repository = new AccountDetailsRepositoryImpl(mongoOperations);
  }

  @Test
  void shouldUpsertBySub() {
    AccountDetailsUpsertRequest request = AccountDetailsUpsertRequest.builder()
        .sub(SUB)
        .email(EMAIL)
        .traineeId(TRAINEE_ID)
        .build();

    repository.upsertBySub(request);

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.captor();

    verify(mongoOperations).upsert(queryCaptor.capture(), updateCaptor.capture(),
        eq(AccountDetails.class));

    Query query = queryCaptor.getValue();
    assertThat("Unexpected query.", query.getQueryObject().get("sub"), is(SUB));

    Document update = updateCaptor.getValue().getUpdateObject();
    assertThat("Unexpected update keys.", update.keySet(),
        containsInAnyOrder("$set", "$setOnInsert"));

    Document set = update.get("$set", Document.class);
    assertThat("Unexpected set keys.", set.keySet(),
        containsInAnyOrder("sub", "email", "traineeId", "lastModified"));
    assertThat("Unexpected update sub.", set.get("sub"), is(SUB));
    assertThat("Unexpected update email.", set.get("email"), is(EMAIL));
    assertThat("Unexpected update traineeId.", set.get("traineeId"), is(TRAINEE_ID));
    assertThat("Unexpected update lastModified.", set.get("lastModified"), notNullValue());

    Document setOnInsert = update.get("$setOnInsert", Document.class);
    assertThat("Unexpected setOnInsert keys.", setOnInsert.keySet(), containsInAnyOrder("id"));
    assertThat("Unexpected insert id.", setOnInsert.get("id"), notNullValue());
  }

  @Test
  void shouldBulkUpsertBySub() {
    BulkOperations bulkOperations = mock(BulkOperations.class);
    BulkWriteResult bulkWriteResult = mock(BulkWriteResult.class);

    when(mongoOperations.bulkOps(BulkOperations.BulkMode.UNORDERED,
        AccountDetails.class)).thenReturn(bulkOperations);
    when(bulkOperations.upsert(any(Query.class), any(Update.class))).thenReturn(bulkOperations);
    when(bulkOperations.execute()).thenReturn(bulkWriteResult);

    AccountDetailsUpsertRequest request1 = AccountDetailsUpsertRequest.builder()
        .sub(SUB)
        .email(EMAIL)
        .traineeId(TRAINEE_ID)
        .build();

    AccountDetailsUpsertRequest request2 = AccountDetailsUpsertRequest.builder()
        .sub(UUID.randomUUID().toString())
        .email("test2@example.com")
        .traineeId(UUID.randomUUID().toString())
        .build();

    BulkWriteResult result = repository.bulkUpsertBySub(List.of(request1, request2));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.captor();

    verify(mongoOperations).bulkOps(BulkOperations.BulkMode.UNORDERED, AccountDetails.class);
    verify(bulkOperations, times(2)).upsert(queryCaptor.capture(), updateCaptor.capture());
    verify(bulkOperations).execute();

    List<Query> queries = queryCaptor.getAllValues();
    assertThat("Unexpected query.", queries.get(0).getQueryObject().get("sub"), is(SUB));
    assertThat("Unexpected query.", queries.get(1).getQueryObject().get("sub"), is(request2.sub()));

    List<Update> updates = updateCaptor.getAllValues();
    Document firstUpdate = updates.get(0).getUpdateObject();
    assertThat("Unexpected update keys.", firstUpdate.keySet(),
        containsInAnyOrder("$set", "$setOnInsert"));

    Document firstSet = firstUpdate.get("$set", Document.class);
    assertThat("Unexpected set keys.", firstSet.keySet(),
        containsInAnyOrder("sub", "email", "traineeId", "lastModified"));
    assertThat("Unexpected update sub.", firstSet.get("sub"), is(SUB));
    assertThat("Unexpected update email.", firstSet.get("email"), is(EMAIL));
    assertThat("Unexpected update traineeId.", firstSet.get("traineeId"), is(TRAINEE_ID));
    assertThat("Unexpected update lastModified.", firstSet.get("lastModified"), notNullValue());

    Document firstSetOnInsert = firstUpdate.get("$setOnInsert", Document.class);
    assertThat("Unexpected first setOnInsert keys.", firstSetOnInsert.keySet(),
        containsInAnyOrder("id"));
    assertThat("Unexpected first insert id.", firstSetOnInsert.get("id"), notNullValue());

    Document secondUpdate = updates.get(1).getUpdateObject();
    assertThat("Unexpected update key.", secondUpdate.keySet(),
        containsInAnyOrder("$set", "$setOnInsert"));

    Document secondSet = secondUpdate.get("$set", Document.class);
    assertThat("Unexpected set keys.", secondSet.keySet(),
        containsInAnyOrder("sub", "email", "traineeId", "lastModified"));
    assertThat("Unexpected update sub.", secondSet.get("sub"), is(request2.sub()));
    assertThat("Unexpected update email.", secondSet.get("email"), is(request2.email()));
    assertThat("Unexpected update traineeId.", secondSet.get("traineeId"),
        is(request2.traineeId()));
    assertThat("Unexpected update lastModified.", secondSet.get("lastModified"), notNullValue());

    Document secondSetOnInsert = secondUpdate.get("$setOnInsert", Document.class);
    assertThat("Unexpected second setOnInsert keys.", secondSetOnInsert.keySet(),
        containsInAnyOrder("id"));
    assertThat("Unexpected second insert id.", secondSetOnInsert.get("id"), notNullValue());

    assertThat("Unexpected bulk execute result.", result, is(bulkWriteResult));
  }
}
