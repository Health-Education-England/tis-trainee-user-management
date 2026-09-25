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

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;
import uk.nhs.tis.trainee.usermanagement.model.AccountDetails;

/**
 * Repository for {@link AccountDetails} entities.
 */
public interface AccountDetailsRepository extends MongoRepository<AccountDetails, UUID>,
    AccountDetailsRepositoryCustom {

  /**
   * Find all account details for a given trainee ID.
   *
   * @param traineeId The trainee ID to find account details for.
   * @return A set of account details for the given trainee ID.
   */
  Set<AccountDetails> findAllByTraineeId(String traineeId);

  /**
   * Find account details for a given sub.
   *
   * @param sub The sub of the account to find.
   * @return The account details, if found.
   */
  Optional<AccountDetails> findBySub(String sub);

  /**
   * Delete account details for a given sub.
   *
   * @param sub The sub of the account to delete.
   */
  void deleteBySub(String sub);

  /**
   * Delete account details that were last modified before the given timestamp.
   *
   * @param timestamp The timestamp to compare against.
   * @return The number of account details deleted.
   */
  long deleteByLastModifiedBefore(Instant timestamp);
}
