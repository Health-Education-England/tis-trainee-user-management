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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.bulk.BulkWriteResult;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
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

  @Nested
  class UpsertBySub {

    @Test
    void shouldPerformUpsert() {
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
      assertThat("Unexpected setOnInsert keys.", setOnInsert.keySet(), containsInAnyOrder("_id"));
      assertThat("Unexpected insert id.", setOnInsert.get("_id"), notNullValue());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldUnsetEmailAndTraineeIdWhenNullOrEmpty(String value) {
      AccountDetailsUpsertRequest request = AccountDetailsUpsertRequest.builder()
          .sub(SUB)
          .email(value)
          .traineeId(value)
          .build();

      repository.upsertBySub(request);

      ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.captor();
      verify(mongoOperations).upsert(any(Query.class), updateCaptor.capture(),
          eq(AccountDetails.class));

      Document update = updateCaptor.getValue().getUpdateObject();
      Document set = update.get("$set", Document.class);
      assertThat("Unexpected set keys.", set.keySet(), containsInAnyOrder("sub", "lastModified"));

      Document unset = update.get("$unset", Document.class);
      assertThat("Unexpected unset keys.", unset.keySet(),
          containsInAnyOrder("email", "traineeId"));
    }

    @Test
    void shouldUnsetStaleEmail() {
      AccountDetailsUpsertRequest request = AccountDetailsUpsertRequest.builder()
          .sub(SUB)
          .email(EMAIL)
          .traineeId(TRAINEE_ID)
          .build();

      repository.upsertBySub(request);

      ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
      ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.captor();
      verify(mongoOperations).updateMulti(queryCaptor.capture(), updateCaptor.capture(),
          eq(AccountDetails.class));

      Document query = queryCaptor.getValue().getQueryObject();
      assertThat("Unexpected query keys.", query.keySet(), containsInAnyOrder("sub", "email"));
      assertThat("Unexpected query.", query.get("email"), is(EMAIL));

      Document subCondition = query.get("sub", Document.class);
      assertThat("Unexpected sub condition keys.", subCondition.keySet(),
          containsInAnyOrder("$ne"));
      assertThat("Unexpected sub condition.", subCondition.get("$ne"), is(SUB));

      Document update = updateCaptor.getValue().getUpdateObject();
      assertThat("Unexpected update keys.", update.keySet(), containsInAnyOrder("$unset"));

      Document unset = update.get("$unset", Document.class);
      assertThat("Unexpected unset keys.", unset.keySet(), containsInAnyOrder("email"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldNotUnsetStaleEmailWhenNullOrEmpty(String value) {
      AccountDetailsUpsertRequest request = AccountDetailsUpsertRequest.builder()
          .sub(SUB)
          .email(value)
          .traineeId(TRAINEE_ID)
          .build();

      repository.upsertBySub(request);

      verify(mongoOperations, never()).updateMulti(any(Query.class), any(Update.class),
          eq(AccountDetails.class));
    }
  }

  @Nested
  class BulkUpsertBySub {

    @Test
    void shouldPerformBulkUpsert() {
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

      final BulkWriteResult result = repository.bulkUpsertBySub(List.of(request1, request2));

      ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
      ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.captor();

      verify(mongoOperations).bulkOps(BulkOperations.BulkMode.UNORDERED, AccountDetails.class);
      verify(bulkOperations, times(2)).upsert(queryCaptor.capture(), updateCaptor.capture());
      verify(bulkOperations).execute();

      List<Query> queries = queryCaptor.getAllValues();
      assertThat("Unexpected query.", queries.get(0).getQueryObject().get("sub"), is(SUB));
      assertThat("Unexpected query.", queries.get(1).getQueryObject().get("sub"),
          is(request2.sub()));

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
          containsInAnyOrder("_id"));
      assertThat("Unexpected first insert id.", firstSetOnInsert.get("_id"), notNullValue());

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
          containsInAnyOrder("_id"));
      assertThat("Unexpected second insert id.", secondSetOnInsert.get("_id"), notNullValue());

      assertThat("Unexpected bulk execute result.", result, is(bulkWriteResult));
    }

    @Test
    void shouldUnsetEmailAndTraineeIdWhenNullOrEmpty() {
      BulkOperations bulkOperations = mock(BulkOperations.class);
      BulkWriteResult bulkWriteResult = mock(BulkWriteResult.class);

      when(mongoOperations.bulkOps(BulkOperations.BulkMode.UNORDERED,
          AccountDetails.class)).thenReturn(bulkOperations);
      when(bulkOperations.upsert(any(Query.class), any(Update.class))).thenReturn(bulkOperations);
      when(bulkOperations.execute()).thenReturn(bulkWriteResult);

      AccountDetailsUpsertRequest request1 = AccountDetailsUpsertRequest.builder()
          .sub(SUB)
          .email(null)
          .traineeId(TRAINEE_ID)
          .build();

      AccountDetailsUpsertRequest request2 = AccountDetailsUpsertRequest.builder()
          .sub(UUID.randomUUID().toString())
          .email("")
          .traineeId("")
          .build();

      repository.bulkUpsertBySub(List.of(request1, request2));

      ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.captor();
      verify(bulkOperations, times(2)).upsert(any(Query.class), updateCaptor.capture());

      List<Update> updates = updateCaptor.getAllValues();

      Document firstUpdate = updates.get(0).getUpdateObject();
      Document firstSet = firstUpdate.get("$set", Document.class);
      assertThat("Unexpected set keys.", firstSet.keySet(),
          containsInAnyOrder("sub", "traineeId", "lastModified"));
      Document firstUnset = firstUpdate.get("$unset", Document.class);
      assertThat("Unexpected unset keys.", firstUnset.keySet(), containsInAnyOrder("email"));

      Document secondUpdate = updates.get(1).getUpdateObject();
      Document secondSet = secondUpdate.get("$set", Document.class);
      assertThat("Unexpected set keys.", secondSet.keySet(),
          containsInAnyOrder("sub", "lastModified"));
      Document secondUnset = secondUpdate.get("$unset", Document.class);
      assertThat("Unexpected unset keys.", secondUnset.keySet(),
          containsInAnyOrder("email", "traineeId"));
    }

    @Test
    void shouldUnsetStaleEmail() {
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
          .sub("sub-2")
          .email("test2@example.com")
          .traineeId(UUID.randomUUID().toString())
          .build();

      repository.bulkUpsertBySub(List.of(request1, request2));

      ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
      ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.captor();
      verify(mongoOperations).updateMulti(queryCaptor.capture(), updateCaptor.capture(),
          eq(AccountDetails.class));

      Document query = queryCaptor.getValue().getQueryObject();
      assertThat("Unexpected query keys.", query.keySet(), containsInAnyOrder("sub", "email"));

      Document emailCondition = query.get("email", Document.class);
      assertThat("Unexpected email condition keys.", emailCondition.keySet(),
          containsInAnyOrder("$in"));
      assertThat("Unexpected email condition.", (Collection<?>) emailCondition.get("$in"),
          containsInAnyOrder(EMAIL, "test2@example.com"));

      Document subCondition = query.get("sub", Document.class);
      assertThat("Unexpected sub condition keys.", subCondition.keySet(),
          containsInAnyOrder("$nin"));
      assertThat("Unexpected sub condition.", (Collection<?>) subCondition.get("$nin"),
          containsInAnyOrder(SUB, "sub-2"));

      Document update = updateCaptor.getValue().getUpdateObject();
      assertThat("Unexpected update keys.", update.keySet(), containsInAnyOrder("$unset"));

      Document unset = update.get("$unset", Document.class);
      assertThat("Unexpected unset keys.", unset.keySet(), containsInAnyOrder("email"));
    }

    @Test
    void shouldNotUnsetStaleEmailWhenNullOrEmpty() {
      BulkOperations bulkOperations = mock(BulkOperations.class);
      BulkWriteResult bulkWriteResult = mock(BulkWriteResult.class);

      when(mongoOperations.bulkOps(BulkOperations.BulkMode.UNORDERED,
          AccountDetails.class)).thenReturn(bulkOperations);
      when(bulkOperations.upsert(any(Query.class), any(Update.class))).thenReturn(bulkOperations);
      when(bulkOperations.execute()).thenReturn(bulkWriteResult);

      AccountDetailsUpsertRequest request1 = AccountDetailsUpsertRequest.builder()
          .sub(SUB)
          .email(null)
          .traineeId(TRAINEE_ID)
          .build();
      AccountDetailsUpsertRequest request2 = AccountDetailsUpsertRequest.builder()
          .sub(SUB)
          .email("")
          .traineeId(TRAINEE_ID)
          .build();

      repository.bulkUpsertBySub(List.of(request1, request2));

      verify(mongoOperations, never()).updateMulti(any(Query.class), any(Update.class),
          eq(AccountDetails.class));
    }
  }
}
