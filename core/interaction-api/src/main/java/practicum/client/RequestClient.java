package practicum.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import practicum.model.dto.request.EventRequestStatusUpdateRequest;
import practicum.model.dto.request.EventRequestStatusUpdateResult;
import practicum.model.dto.request.ParticipationRequestDto;
import practicum.model.enums.RequestStatus;

import java.util.List;
import java.util.Map;
import java.util.Set;

@FeignClient(
        name = "request-service",
        path = "/api/v1/requests"
)
public interface RequestClient {

    @GetMapping("/{eventId}/count")
    long countEventsInStatus(@PathVariable("eventId") Long eventId,
                             @RequestParam("status") RequestStatus status);

    @GetMapping("/confirmed/count")
    Map<Long, Long> countConfirmedRequestsForEvents(@RequestParam("eventIds") Set<Long> eventIds);

    @GetMapping("/owner/{ownerId}/event/{eventId}")
    List<ParticipationRequestDto> getRequestsForEventByOwner(@PathVariable("ownerId") Long ownerId,
                                                             @PathVariable("eventId") Long eventId);

    @PatchMapping("/user/{userId}/event/{eventId}/status")
    EventRequestStatusUpdateResult updateRequestStatus(@PathVariable("userId") Long userId,
                                                       @PathVariable("eventId") Long eventId,
                                                       @RequestBody EventRequestStatusUpdateRequest eventRequestStatusUpdateRequest);

    @PostMapping("{eventId}/participant/{userId}")
    boolean isUserParticipant(@PathVariable Long userId, @PathVariable Long eventId);
}