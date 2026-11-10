package dev.cuccorese.brokerage.trading;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, Integer> {

    List<Transfer> findByUserIdAndStatus(Integer userId, TransferStatus status);

    List<Transfer> findByUserIdOrderByDatetimeDescIdDesc(Integer userId);

    Optional<Transfer> findByIdAndUserId(Integer id, Integer userId);
}
