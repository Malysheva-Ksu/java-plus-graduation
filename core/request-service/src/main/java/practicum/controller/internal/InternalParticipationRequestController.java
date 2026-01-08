package practicum.controller.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import practicum.model.dto.request.EventRequestStatusUpdateRequest;
import practicum.model.dto.request.EventRequestStatusUpdateResult;
import practicum.model.dto.request.ParticipationRequestDto;
import practicum.model.enums.RequestStatus;
import practicum.service.ParticipationRequestService;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/requests")
public class InternalParticipationRequestController {

    private final ParticipationRequestService participationRequestService;

    @GetMapping("/{eventId}/count")
    public long countEventsInStatus(@PathVariable("eventId") Long eventId,
                                    @RequestParam("status") RequestStatus status) {
        return participationRequestService.countEventsInStatus(eventId, status);
    }

    @GetMapping("/confirmed/count")
    public Map<Long, Long> countConfirmedRequestsForEvents(@RequestParam("eventIds") List<Long> eventIds) {
        return participationRequestService.countConfirmedRequestsForEvents(new HashSet<>(eventIds));
    }

    @GetMapping("/owner/{ownerId}/event/{eventId}")
    public List<ParticipationRequestDto> getRequestsForEventByOwner(@PathVariable("ownerId") Long ownerId,
                                                                    @PathVariable("eventId") Long eventId) {
        return participationRequestService.getRequestsByOwner(ownerId, eventId);
    }

    @PatchMapping("/user/{userId}/event/{eventId}/status")
    public EventRequestStatusUpdateResult updateRequestStatus(@PathVariable("userId") Long userId,
                                                              @PathVariable("eventId") Long eventId,
                                                              @RequestBody EventRequestStatusUpdateRequest eventRequestStatusUpdateRequest) {
        return participationRequestService.updateRequests(userId, eventId, eventRequestStatusUpdateRequest);
    }
}