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
    public long countEventsInStatus(@PathVariable Long eventId, @RequestParam RequestStatus status) {
        return participationRequestService.countEventsInStatus(eventId, status);
    }

    @GetMapping("/confirmed/count")
    public Map<Long, Long> countConfirmedRequestsForEvents(@RequestParam("eventIds") List<Long> eventIds) {
        return participationRequestService.countConfirmedRequestsForEvents(new HashSet<>(eventIds));
    }

    @GetMapping("/owner/{ownerId}/event/{eventId}")
    public List<ParticipationRequestDto> getRequestsForEventByOwner(
            @PathVariable Long ownerId,
            @PathVariable Long eventId
    ) {
        return participationRequestService.getRequestsByOwner(ownerId, eventId);
    }

    @PatchMapping("/user/{userId}/event/{eventId}/status")
    EventRequestStatusUpdateResult updateRequestStatus(
            @PathVariable Long userId,
            @PathVariable Long eventId,
            @RequestBody EventRequestStatusUpdateRequest requestStatusUpdateDto
    ) {
        return participationRequestService.updateRequests(userId, eventId, requestStatusUpdateDto);
    }

    @PostMapping("{eventId}/participant/{userId}")
    public boolean isUserParticipant(@PathVariable Long userId, @PathVariable Long eventId) {
        return participationRequestService.isUserParticipant(userId, eventId);
    }

}