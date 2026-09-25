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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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

  private static final String ID_FIELD = "_id";
  private static final String SUB_FIELD = "sub";
  private static final String EMAIL_FIELD = "email";
  private static final String TRAINEE_ID_FIELD = "traineeId";
  private static final String LAST_MODIFIED_FIELD = "lastModified";
  private static final String CLASS_FIELD = "_class";

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
    String email = upsertRequest.email();

    if (email != null && !email.isEmpty()) {
      // Unset any stale email assignment that would break the unique constraint.
      mongoOperations.updateMulti(Query.query(Criteria
              .where(EMAIL_FIELD).is(email)
              .and(SUB_FIELD).ne(upsertRequest.sub())
          ),
          new Update().unset(EMAIL_FIELD), AccountDetails.class);
    }

    Query query = Query.query(Criteria.where(SUB_FIELD).is(upsertRequest.sub()));
    mongoOperations.upsert(query, buildUpsertUpdate(upsertRequest), AccountDetails.class);
  }

  @Override
  public BulkWriteResult bulkUpsertBySub(List<AccountDetailsUpsertRequest> upsertRequests) {
    // Unset any stale email assignments that would break the unique constraints.
    Set<String> requestEmails = upsertRequests.stream()
        .map(AccountDetailsUpsertRequest::email)
        .filter(email -> email != null && !email.isEmpty())
        .collect(Collectors.toSet());

    if (!requestEmails.isEmpty()) {
      Set<String> requestSubs = upsertRequests.stream()
          .map(AccountDetailsUpsertRequest::sub)
          .collect(Collectors.toSet());

      mongoOperations.updateMulti(Query.query(Criteria
              .where(EMAIL_FIELD).in(requestEmails)
              .and(SUB_FIELD).nin(requestSubs)
          ),
          new Update().unset(EMAIL_FIELD), AccountDetails.class);
    }

    // Perform bulk upsert operations for each request.
    BulkOperations bulkOps = mongoOperations.bulkOps(BulkOperations.BulkMode.UNORDERED,
        AccountDetails.class);

    upsertRequests.forEach(request -> {
      Query query = Query.query(Criteria.where(SUB_FIELD).is(request.sub()));
      bulkOps.upsert(query, buildUpsertUpdate(request));
    });
    return bulkOps.execute();
  }

  /**
   * Build the update document for an upsert, setting non-empty values and unsetting fields whose
   * value is null or empty so stale data is not retained.
   *
   * @param request The account details to apply to the update.
   * @return The populated {@link Update}.
   */
  private Update buildUpsertUpdate(AccountDetailsUpsertRequest request) {
    Update update = new Update()
        .setOnInsert(ID_FIELD, UUID.randomUUID())
        .set(SUB_FIELD, request.sub())
        .set(LAST_MODIFIED_FIELD, Instant.now())
        .set(CLASS_FIELD, AccountDetails.class.getName());
    setOrUnset(update, EMAIL_FIELD, request.email());
    setOrUnset(update, TRAINEE_ID_FIELD, request.traineeId());
    return update;
  }

  /**
   * Set the given field to the value if it is not null/empty, otherwise unset the field.
   *
   * @param update The update to modify.
   * @param field  The field to set or unset.
   * @param value  The value to set, if present.
   */
  private void setOrUnset(Update update, String field, String value) {
    if (value != null && !value.isEmpty()) {
      update.set(field, value);
    } else {
      update.unset(field);
    }
  }
}
