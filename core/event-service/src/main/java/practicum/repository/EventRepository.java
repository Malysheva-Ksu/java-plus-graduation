package practicum.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import practicum.model.Event;
import practicum.model.enums.EventState;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findAllByInitiatorId(Long initiatorId, Pageable pageable);

    Optional<Event> findByIdAndInitiatorId(Long eventId, Long initiatorId);

    Set<Event> findAllByIdIn(Set<Long> eventIds);

    Optional<Event> findByIdAndState(Long eventId, EventState state);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Event e
            SET e.views = e.views + 1
            WHERE e.id = :eventId
            """)
    void incrementViews(@Param("eventId") Long eventId);

    boolean existsByCategoryId(Long categoryId);
}