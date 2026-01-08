package practicum.service;

import org.springframework.transaction.annotation.Transactional;
import practicum.model.dto.request.EventRequestStatusUpdateRequest;
import practicum.model.dto.request.EventRequestStatusUpdateResult;
import practicum.model.dto.request.ParticipationRequestDto;
import practicum.model.enums.RequestStatus;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ParticipationRequestService {

    @Transactional
    ParticipationRequestDto createRequest(Long userId, Long eventId);

    @Transactional
    EventRequestStatusUpdateResult updateRequests(Long userId, Long eventId, EventRequestStatusUpdateRequest statusUpdateRequest);

    List<ParticipationRequestDto> getUserRequests(Long userId);

    List<ParticipationRequestDto> getRequestsByOwner(Long userId, Long eventId);

    @Transactional
    ParticipationRequestDto cancelRequest(Long userId, Long requestId);

    long countEventsInStatus(Long eventId, RequestStatus status);

    Map<Long, Long> countConfirmedRequestsForEvents(Set<Long> eventIds);
}