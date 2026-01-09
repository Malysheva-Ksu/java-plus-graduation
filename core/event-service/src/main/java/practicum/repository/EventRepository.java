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

    @Query("SELECT e FROM Event e " +
            "JOIN FETCH e.category " +
            "WHERE (:text IS NULL OR (LOWER(e.annotation) LIKE LOWER(CONCAT('%', :text, '%')) " +
            "OR LOWER(e.description) LIKE LOWER(CONCAT('%', :text, '%')))) " +
            "AND (:categories IS NULL OR e.category.id IN :categories) " +
            "AND (:paid IS NULL OR e.paid = :paid) " +
            "AND (e.state = 'PUBLISHED')")
    List<Event> findByPublicFilters(@Param("text") String text,
                                    @Param("categories") List<Long> categories,
                                    @Param("paid") Boolean paid,
                                    Pageable pageable);

    List<Event> findAllByInitiator(Long initiatorId, Pageable pageable);

    Optional<Event> findByIdAndInitiator(Long eventId, Long initiatorId);

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