package practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import practicum.model.Event;
import practicum.model.ParticipationRequest;
import practicum.model.User;
import practicum.model.enums.RequestStatus;

import java.util.*;
import java.util.stream.Collectors;

public interface ParticipationRequestRepository extends JpaRepository<ParticipationRequest, Long> {
    boolean existsByEventAndRequester(Long eventId, Long requesterId);

    long countByEventAndStatus(Long eventId, RequestStatus status);

    List<ParticipationRequest> findAllByEventAndStatus(Long eventId, RequestStatus status);

    List<ParticipationRequest> findAllByRequester(Long requesterId);

    List<ParticipationRequest> findAllByEvent(Long eventId);

    Optional<ParticipationRequest> findByIdAndRequester(Long requestId, Long requesterId);

    List<ParticipationRequest> findAllByIdIn(List<Long> requestIds);

    @Query("""
            SELECT r.event, COUNT(r.id)
            FROM ParticipationRequest r
            WHERE r.event IN :eventIds
              AND r.status = 'CONFIRMED'
            GROUP BY r.event
            """)
    List<Object[]> countConfirmedRequestsForEventsRaw(@Param("eventIds") Set<Long> eventIds);

    default Map<Long, Long> countConfirmedRequestsForEvents(Set<Long> eventIds) {
        if (Objects.isNull(eventIds) || eventIds.isEmpty()) return Collections.emptyMap();
        return countConfirmedRequestsForEventsRaw(eventIds).stream()
                .collect(Collectors.toMap(obj -> (Long) obj[0], obj -> (Long) obj[1]));
    }
}