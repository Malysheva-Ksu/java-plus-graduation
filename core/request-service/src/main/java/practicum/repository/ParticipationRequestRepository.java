package practicum.repository;

import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import practicum.model.Event;
import practicum.model.ParticipationRequest;
import practicum.model.enums.RequestStatus;

import java.util.*;
import java.util.stream.Collectors;

public interface ParticipationRequestRepository extends JpaRepository<ParticipationRequest, Long> {
    Optional<ParticipationRequest> findByEventIdAndRequesterId(Long eventId, Long requesterId);

    boolean existsByEventIdAndRequesterId(Long eventId, Long requesterId);

    long countByEventIdAndStatus(Long eventId, RequestStatus status);

    List<ParticipationRequest> findAllByEventIdAndStatus(Long eventId, RequestStatus status);

    List<ParticipationRequest> findAllByRequesterId(Long requesterId);

    List<ParticipationRequest> findAllByEventId(Long eventId);

    Optional<ParticipationRequest> findByIdAndRequesterId(Long requestId, Long requesterId);

    List<ParticipationRequest> findAllByEventIdAndRequesterId(Long eventId, Long requesterId);

    List<ParticipationRequest> findAllByEvent(Event event);

    List<ParticipationRequest> findAllByRequester(SecurityProperties.User user);

    List<ParticipationRequest> findAllByIdIn(List<Long> requestIds);

    @Query("SELECT r.event.id, COUNT(r.id) FROM ParticipationRequest r WHERE r.event.id IN :eventIds AND r.status = 'CONFIRMED' GROUP BY r.event.id")
    List<Object[]> countConfirmedRequestsForEventsRaw(@Param("eventIds") Set<Long> eventIds);

    default Map<Long, Long> countConfirmedRequestsForEvents(Set<Long> eventIds) {
        return null;
    }

    long countByEventAndStatus(Long eventId, RequestStatus status);
}