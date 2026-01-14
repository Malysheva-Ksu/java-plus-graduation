package practicum.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import practicum.model.UserAction;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UserActionRepository extends JpaRepository<UserAction, Long> {
    Optional<UserAction> findByUserIdAndEventId(long userId, long eventId);

    @Query("""
            SELECT a.eventId
            FROM UserAction a
            WHERE a.userId = :userId
            AND a.eventId != :eventId
            """)
    Set<Long> findByUserIdExcludeEventId(long userId, long eventId);

    @Query("""
            SELECT a.eventId
            FROM UserAction a
            WHERE a.userId = :userId
            """)
    List<Long> findByUserId(long userId, Pageable pageable);

    List<UserAction> findByEventIdIn(Set<Long> eventId);

}