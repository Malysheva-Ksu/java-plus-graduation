package practicum.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import practicum.model.EventSimilarity;

import java.util.List;
import java.util.Optional;

public interface EventSimilarityRepository extends JpaRepository<EventSimilarity, Long> {
    Optional<EventSimilarity> findByEventAAndEventB(long eventA, long eventB);

    @Query("""
            SELECT s
            FROM EventSimilarity s
            WHERE s.eventA = :eventId
            OR s.eventB = :eventId
            """)
    List<EventSimilarity> findAllByEventId(long eventId);

    @Query("""
            SELECT s
            FROM EventSimilarity s
            WHERE (s.eventA IN :eventIds OR s.eventB IN :eventIds)
            AND NOT (s.eventA IN :eventIds AND s.eventB IN :eventIds)
            """)
    List<EventSimilarity> findNewSimilar(List<Long> eventIds, Pageable pageable);

    @Query("""
            SELECT s
            FROM EventSimilarity s
            WHERE s.eventA = :eventId
            OR s.eventB = :eventId
            """)
    List<EventSimilarity> findNeighbours(long eventId, Pageable pageable);
}