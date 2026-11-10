package dev.cuccorese.brokerage.shared.idempotency;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByUserIdAndOperationAndIdempotencyKey(Integer userId,
            IdempotentOperation operation, String idempotencyKey);

    @Modifying
    @Query("delete from IdempotencyRecord r where r.createdAt < :cutoff")
    void deleteCreatedBefore(Instant cutoff);
}
