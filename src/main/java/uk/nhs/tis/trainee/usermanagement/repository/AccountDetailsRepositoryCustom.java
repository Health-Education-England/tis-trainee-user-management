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
import java.util.List;
import lombok.Builder;
import uk.nhs.tis.trainee.usermanagement.model.AccountDetails;

/**
 * Custom repository interface for {@link AccountDetails} entities, providing additional methods
 * beyond the standard CRUD operations.
 */
public interface AccountDetailsRepositoryCustom {

  /**
   * Upsert (update or insert) account details based on the provided
   * {@link AccountDetailsUpsertRequest} object. If an account with the same sub exists, it will be
   * updated; otherwise, a new account will be created.
   *
   * @param upsertRequest The account details to upsert.
   */
  void upsertBySub(AccountDetailsUpsertRequest upsertRequest);

  /**
   * Bulk upsert (update or insert) account details based on the provided list of
   * {@link AccountDetailsUpsertRequest} objects. If an account with the same sub exists, it will be
   * updated; otherwise, a new account will be created.
   *
   * @param upsertRequests The list of account details to upsert.
   * @return The result of the bulk write operation.
   */
  BulkWriteResult bulkUpsertBySub(List<AccountDetailsUpsertRequest> upsertRequests);

  /**
   * Record representing the request to upsert account details.
   *
   * @param sub       The user's subject identifier.
   * @param email     The user's email address.
   * @param traineeId The user's trainee ID.
   */
  @Builder
  record AccountDetailsUpsertRequest(String sub, String email, String traineeId) {

  }
}
