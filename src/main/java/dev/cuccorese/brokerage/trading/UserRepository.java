package dev.cuccorese.brokerage.trading;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface UserRepository extends JpaRepository<User, Integer> {

    /** Holding this lock serialises everything that spends the user's cash or shares. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findWithLockById(Integer id);
}
