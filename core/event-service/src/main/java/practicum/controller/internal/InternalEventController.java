package practicum.controller.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import practicum.model.dto.event.EventFullDto;
import practicum.service.event.EventService;

import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/events")
public class InternalEventController {

    private final EventService eventService;

    @GetMapping("/{id}")
    public Optional<EventFullDto> getEvent(@PathVariable Long id) {
        return eventService.getEvent(id);
    }

    @PatchMapping("/{eventId}/confirmed-requests")
    public void updateConfirmedRequests(@PathVariable Long eventId,
                                        @RequestParam Long confirmedRequests) {
        eventService.updateConfirmedRequests(eventId, confirmedRequests);
    }
}