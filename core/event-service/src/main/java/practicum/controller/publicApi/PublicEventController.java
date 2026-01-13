package practicum.controller.publicApi;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import practicum.model.dto.event.EventFullDto;
import practicum.model.dto.event.EventShortDto;
import practicum.model.enums.SortValue;
import practicum.service.event.EventService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@Validated
@RequestMapping("/events")
@RequiredArgsConstructor
@Slf4j
public class PublicEventController {
    private final EventService eventService;

    @GetMapping
    public List<EventShortDto> getEventsWithParamsByUser(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) List<Long> categories,
            @RequestParam(required = false) Boolean paid,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime rangeStart,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime rangeEnd,
            @RequestParam(defaultValue = "false") Boolean onlyAvailable,
            @RequestParam(required = false) SortValue sort,
            @RequestParam(defaultValue = "0") @PositiveOrZero Integer from,
            @RequestParam(defaultValue = "10") @Positive Integer size,
            HttpServletRequest request
    ) {
        return eventService.getEventsByUser(
                text, categories, paid, rangeStart, rangeEnd, onlyAvailable, sort, from, size, request
        );
    }

    @GetMapping("/{id}")
    public EventFullDto getEvent(@PathVariable Long id, @RequestHeader("X-EWM-USER-ID") Long userId, HttpServletRequest request) {
        return eventService.getEvent(id, userId, request);
    }

    @GetMapping("/recommendations")
    public List<EventShortDto> getRecommendationsForUser(@PathVariable Long userId, HttpServletRequest request) {
        return eventService.getRecommendations(userId);
    }

    @PutMapping("/{eventId}/like")
    public void like(@PathVariable Long userId, @PathVariable Long eventId) {
        eventService.like(userId, eventId);
    }
}