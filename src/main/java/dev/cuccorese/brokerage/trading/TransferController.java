package dev.cuccorese.brokerage.trading;

import dev.cuccorese.brokerage.shared.idempotency.IdempotencyKey;
import dev.cuccorese.brokerage.shared.idempotency.IdempotentResult;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users/{userId}/transfers")
class TransferController {

    private final TransferService transfers;

    /**
     * Answers 201 for every transfer it stores, including withdrawals REJECTED for lack of cash, and
     * again for a retry with the same idempotency key, marked {@code Idempotent-Replayed: true}.
     */
    @PostMapping
    ResponseEntity<TransferView> make(@PathVariable Integer userId, @Valid @RequestBody TransferRequest request,
            @Parameter(description = "Optional. A retry with the same key returns the first transfer instead of making"
                    + " another; the key is kept at least 24 hours and cannot be reused for a different transfer.")
            @RequestHeader(name = IdempotencyKey.HEADER, required = false)
            @Pattern(regexp = IdempotencyKey.SYNTAX, message = IdempotencyKey.SYNTAX_MESSAGE) String idempotencyKey) {
        IdempotentResult<Transfer> made = transfers.make(request.toMakeTransfer(userId),
                IdempotencyKey.fromHeader(idempotencyKey));
        TransferView transfer = TransferView.of(made.resource());
        ResponseEntity.BodyBuilder response =
                ResponseEntity.created(URI.create("/users/" + userId + "/transfers/" + transfer.id()));
        if (made.replayed()) {
            response.header(IdempotencyKey.REPLAYED_HEADER, "true");
        }
        return response.body(transfer);
    }

    @GetMapping
    List<TransferView> history(@PathVariable Integer userId) {
        return transfers.historyOf(userId).stream().map(TransferView::of).toList();
    }

    @GetMapping("/{transferId}")
    TransferView get(@PathVariable Integer userId, @PathVariable Integer transferId) {
        return TransferView.of(transfers.get(userId, transferId));
    }
}
