package dev.cuccorese.brokerage.shared.idempotency;

import dev.cuccorese.brokerage.shared.ErrorCode;
import dev.cuccorese.brokerage.shared.UnprocessableContentException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** What one key of a user created for one operation, and the hash of the request that sent it. */
@Entity
@Table(name = "idempotency_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "userid")
    private Integer userId;

    @Enumerated(EnumType.STRING)
    private IdempotentOperation operation;

    @Column(name = "idempotencykey")
    private String idempotencyKey;

    @Column(name = "requesthash")
    private String requestHash;

    @Column(name = "resourceid")
    private Integer resourceId;

    @Column(name = "createdat")
    private Instant createdAt;

    IdempotencyRecord(Integer userId, IdempotentOperation operation, IdempotencyKey key, RequestHash request,
            Integer resourceId, Instant createdAt) {
        this.userId = userId;
        this.operation = operation;
        this.idempotencyKey = key.value();
        this.requestHash = request.sha256();
        this.resourceId = resourceId;
        this.createdAt = createdAt;
    }

    /**
     * The id of the resource this key created, provided {@code request} is the request that created it.
     *
     * @throws UnprocessableContentException when the key comes back with a different request
     */
    Integer resourceFor(RequestHash request) {
        if (!requestHash.equals(request.sha256())) {
            throw new UnprocessableContentException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    IdempotencyKey.HEADER + " " + idempotencyKey + " was already used for a different request");
        }
        return resourceId;
    }
}
