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

import com.mongodb.bulk.BulkWriteResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import uk.nhs.tis.trainee.usermanagement.model.AccountDetails;

/**
 * Implementation of the custom repository interface for {@link AccountDetails} entities.
 */
public class AccountDetailsRepositoryImpl implements AccountDetailsRepositoryCustom {

  private final MongoOperations mongoOperations;

  /**
   * Constructor for {@link AccountDetailsRepositoryImpl}.
   *
   * @param mongoOperations The MongoOperations instance used for database operations.
   */
  public AccountDetailsRepositoryImpl(MongoOperations mongoOperations) {
    this.mongoOperations = mongoOperations;
  }

  @Override
  public void upsertBySub(AccountDetailsUpsertRequest upsertRequest) {
    Query query = Query.query(Criteria.where("sub").is(upsertRequest.sub()));
    Update update = new Update()
        .setOnInsert("id", UUID.randomUUID())
        .set("sub", upsertRequest.sub())
        .set("email", upsertRequest.email())
        .set("traineeId", upsertRequest.traineeId())
        .set("lastModified", Instant.now());
    mongoOperations.upsert(query, update, AccountDetails.class);
  }

  @Override
  public BulkWriteResult bulkUpsertBySub(List<AccountDetailsUpsertRequest> upsertRequests) {
    BulkOperations bulkOps = mongoOperations.bulkOps(BulkOperations.BulkMode.UNORDERED,
        AccountDetails.class);

    upsertRequests.forEach(request -> {
      Query query = Query.query(Criteria.where("sub").is(request.sub()));
      Update update = new Update()
          .setOnInsert("id", UUID.randomUUID())
          .set("sub", request.sub())
          .set("email", request.email())
          .set("traineeId", request.traineeId())
          .set("lastModified", Instant.now());
      bulkOps.upsert(query, update);
    });
    return bulkOps.execute();
  }
}
