package practicum.mapper;

import practicum.model.dto.event.EventFullDto;
import practicum.model.dto.event.EventShortDto;
import practicum.model.dto.event.NewEventDto;
import practicum.model.dto.user.UserDto;
import practicum.model.dto.user.UserShortDto;
import practicum.model.enums.EventState;
import practicum.model.Category;
import practicum.model.Event;
import practicum.model.Location;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class EventMapper {
    private EventMapper() {
    }

    public static Event toEvent(NewEventDto dto, Category category, UserDto user, Location location) {
        if (Objects.isNull(dto)) return null;

        return Event.builder()
                .annotation(dto.getAnnotation())
                .category(category)
                .title(dto.getTitle())
                .description(dto.getDescription())
                .eventDate(dto.getEventDate())
                .location(location)
                .paid(dto.getPaid() != null ? dto.getPaid() : false)
                .participantLimit(dto.getParticipantLimit() != null ? dto.getParticipantLimit() : 0)
                .requestModeration(dto.getRequestModeration() != null ? dto.getRequestModeration() : true)
                .initiatorId(user.getId())
                .state(EventState.PENDING)
                .createdOn(LocalDateTime.now())
                .views(0L)
                .confirmedRequests(0L)
                .build();
    }

    public static EventShortDto toEventShortDto(Event event) {
        if (Objects.isNull(event)) return null;

        return EventShortDto.builder()
                .id(event.getId())
                .annotation(event.getAnnotation())
                .category(CategoryMapper.toCategoryDto(event.getCategory()))
                .eventDate(event.getEventDate())
                .initiator(new UserShortDto(event.getInitiatorId(), null))
                .paid(event.getPaid())
                .title(event.getTitle())
                .views(event.getViews())
                .confirmedRequests(event.getConfirmedRequests())
                .participantLimit(event.getParticipantLimit())
                .rating(event.getRating())
                .build();
    }

    public static EventFullDto toFullEventDto(Event event, UserDto userDto, long confirmedRequestsCount) {
        if (Objects.isNull(event)) return null;

        return EventFullDto.builder()
                .id(event.getId())
                .annotation(event.getAnnotation())
                .category(CategoryMapper.toCategoryDto(event.getCategory()))
                .confirmedRequests(confirmedRequestsCount)
                .initiator(new UserShortDto(userDto.getId(), userDto.getName()))
                .createdOn(event.getCreatedOn())
                .description(event.getDescription())
                .eventDate(event.getEventDate())
                .location(LocationMapper.toLocationDto(event.getLocation()))
                .paid(event.getPaid())
                .participantLimit(event.getParticipantLimit())
                .publishedOn(event.getPublishedOn())
                .requestModeration(event.getRequestModeration())
                .state(event.getState())
                .title(event.getTitle())
                .views(event.getViews())
                .rating(event.getRating())
                .build();
    }

    public static EventFullDto toFullEventDto(Event event) {
        if (Objects.isNull(event)) return null;

        long confirmed = Objects.nonNull(event.getConfirmedRequests()) ? event.getConfirmedRequests() : 0L;

        return EventFullDto.builder()
                .id(event.getId())
                .annotation(event.getAnnotation())
                .category(CategoryMapper.toCategoryDto(event.getCategory()))
                .confirmedRequests(confirmed)
                .initiator(new UserShortDto(event.getInitiatorId(), null))
                .createdOn(event.getCreatedOn())
                .description(event.getDescription())
                .eventDate(event.getEventDate())
                .location(LocationMapper.toLocationDto(event.getLocation()))
                .paid(event.getPaid())
                .participantLimit(event.getParticipantLimit())
                .publishedOn(event.getPublishedOn())
                .requestModeration(event.getRequestModeration())
                .state(event.getState())
                .title(event.getTitle())
                .views(event.getViews())
                .rating(event.getRating())
                .build();
    }

    public static List<EventShortDto> toEventShortDtoList(List<Event> events) {
        if (Objects.isNull(events) || events.isEmpty()) return Collections.emptyList();
        return events.stream()
                .map(EventMapper::toEventShortDto)
                .collect(Collectors.toList());
    }

    public static Set<EventShortDto> toEventShortDtoSet(Set<Event> events) {
        if (Objects.isNull(events) || events.isEmpty()) return Collections.emptySet();
        return events.stream()
                .map(EventMapper::toEventShortDto)
                .collect(Collectors.toSet());
    }

    public static List<EventFullDto> toEventFullDtoList(List<Event> events) {
        if (Objects.isNull(events) || events.isEmpty()) return Collections.emptyList();
        return events.stream()
                .map(EventMapper::toFullEventDto)
                .collect(Collectors.toList());
    }
}