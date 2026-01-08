package practicum.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import practicum.model.dto.event.EventFullDto;

import java.util.Optional;

@FeignClient(
        name = "event-service",
        path = "/api/v1/events"
)
public interface EventClient {

    @GetMapping("/{eventId}")
    Optional<EventFullDto> getEvent(@PathVariable("eventId") Long eventId);

    @PatchMapping("/{eventId}/confirmed-requests")
    void updateConfirmedRequests(@PathVariable("eventId") Long eventId,
                                 @RequestParam("confirmedRequests") Long confirmedRequests);
}