package dev.cuccorese.brokerage.trading;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users/{userId}/transfers")
class TransferController {

    private final TransferService transfers;

    /** Answers 201 for every transfer it stores, including withdrawals REJECTED for lack of cash. */
    @PostMapping
    ResponseEntity<TransferView> make(@PathVariable Integer userId, @Valid @RequestBody TransferRequest request) {
        TransferView transfer = TransferView.of(transfers.make(userId, request.type(), request.pesos()));
        return ResponseEntity.created(URI.create("/users/" + userId + "/transfers/" + transfer.id())).body(transfer);
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
