package practicum.mapper;

import practicum.model.Category;
import practicum.model.Event;
import practicum.model.Location;
import practicum.model.User;
import practicum.model.dto.event.EventFullDto;
import practicum.model.dto.event.EventShortDto;
import practicum.model.dto.event.NewEventDto;
import practicum.model.dto.user.UserShortDto;
import practicum.model.enums.EventState;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class EventMapper {

    private EventMapper() {
    }

    public static Event toEvent(NewEventDto source,
                                Category category,
                                User initiator,
                                Location location) {
        if (source == null) {
            return null;
        }

        return Event.builder()
                .annotation(source.getAnnotation())
                .category(category)
                .title(source.getTitle())
                .description(source.getDescription())
                .eventDate(source.getEventDate())
                .location(location)
                .paid(source.getPaid())
                .participantLimit(resolveParticipantLimit(source))
                .requestModeration(resolveRequestModeration(source))
                .initiator(initiator)
                .state(EventState.PENDING)
                .createdOn(LocalDateTime.now())
                .publishedOn(null)
                .views(0L)
                .confirmedRequests(0L)
                .build();
    }

    private static long resolveParticipantLimit(NewEventDto dto) {
        return dto.getParticipantLimit() != null ? dto.getParticipantLimit() : 0;
    }

    private static boolean resolveRequestModeration(NewEventDto dto) {
        return dto.getRequestModeration() != null ? dto.getRequestModeration() : true;
    }

    public static EventShortDto toEventShortDto(Event event) {
        if (event == null) {
            return null;
        }

        return new EventShortDto(
                event.getId(),
                event.getAnnotation(),
                CategoryMapper.toCategoryDto(event.getCategory()),
                event.getEventDate(),
                toUserShortDto(event.getInitiator()),
                event.getPaid(),
                event.getTitle(),
                event.getViews(),
                defaultConfirmed(event),
                event.getParticipantLimit()
        );
    }

    public static Set<EventShortDto> toEventShortDtoSet(Set<Event> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptySet();
        }

        return events.stream()
                .map(EventMapper::toEventShortDto)
                .collect(Collectors.toSet());
    }

    public static List<EventShortDto> toEventShortDtoList(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }

        return events.stream()
                .map(EventMapper::toEventShortDto)
                .collect(Collectors.toList());
    }

    public static EventFullDto toFullEventDto(Event event, long confirmedRequestsCount) {
        if (event == null) {
            return null;
        }

        return new EventFullDto(
                event.getId(),
                event.getAnnotation(),
                CategoryMapper.toCategoryDto(event.getCategory()),
                confirmedRequestsCount,
                event.getCreatedOn(),
                event.getDescription(),
                event.getEventDate(),
                toUserShortDto(event.getInitiator()),
                LocationMapper.toLocationDto(event.getLocation()),
                event.getPaid(),
                event.getParticipantLimit(),
                event.getPublishedOn(),
                event.getRequestModeration(),
                event.getState(),
                event.getTitle(),
                event.getViews()
        );
    }

    public static EventFullDto toFullEventDto(Event event) {
        if (event == null) {
            return null;
        }
        return toFullEventDto(event, defaultConfirmed(event));
    }

    public static List<EventFullDto> toEventFullDtoList(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }

        return events.stream()
                .map(EventMapper::toFullEventDto)
                .collect(Collectors.toList());
    }

    private static long defaultConfirmed(Event event) {
        return event.getConfirmedRequests() != null ? event.getConfirmedRequests() : 0L;
    }

    private static UserShortDto toUserShortDto(User user) {
        if (user == null) {
            return null;
        }
        return new UserShortDto(user.getId(), user.getName());
    }
}