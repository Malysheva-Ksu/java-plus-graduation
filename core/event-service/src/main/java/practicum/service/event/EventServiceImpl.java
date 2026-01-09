package practicum.service.event;

import feign.FeignException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import practicum.HitDto;
import practicum.StatsClient;
import practicum.ViewStatsDto;
import practicum.client.RequestClient;
import practicum.client.UserClient;
import practicum.exception.ConflictException;
import practicum.exception.NotFoundException;
import practicum.exception.ValidationException;
import practicum.mapper.EventMapper;
import practicum.mapper.LocationMapper;
import practicum.model.Category;
import practicum.model.Event;
import practicum.model.Location;
import practicum.model.User;
import practicum.model.dto.event.*;
import practicum.model.dto.location.LocationDto;
import practicum.model.dto.request.EventRequestStatusUpdateRequest;
import practicum.model.dto.request.EventRequestStatusUpdateResult;
import practicum.model.dto.request.ParticipationRequestDto;
import practicum.model.dto.user.UserDto;
import practicum.model.enums.*;
import practicum.repository.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    @Value("${app.name:ewm-main-service}")
    private String appName;

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final UserClient userClient;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final RequestClient requestClient;
    private final EntityManager entityManager;
    private final StatsClient statsClient;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional
    public EventFullDto createEvent(NewEventDto newEventDto, Long userId) {
        validateEventDate(newEventDto.getEventDate(), 1);

        Optional<UserDto> userDto = findUserById(userId);
        if (userDto.isEmpty()) new NotFoundException("Пользователь с ID=" + userId + " не найден.");

        Category category = categoryRepository.findById(newEventDto.getCategory())
                .orElseThrow(() -> new NotFoundException("Категория с ID=" + newEventDto.getCategory() + " не найдена."));

        Location location = getLocation(newEventDto.getLocation());
        Event event = EventMapper.toEvent(newEventDto, category, userDto.get(), location);

        return EventMapper.toFullEventDto(eventRepository.save(event));
    }

    private Location getLocation(LocationDto locationDto) {
        return locationRepository.findByLatAndLon(locationDto.getLat(), locationDto.getLon())
                .orElseGet(() -> locationRepository.save(LocationMapper.toLocation(locationDto)));
    }

    @Override
    public Optional<EventFullDto> getEvent(Long eventId) {
        return eventRepository.findById(eventId).map(EventMapper::toFullEventDto);
    }

    @Override
    public List<EventShortDto> getEvents(Long userId, Integer from, Integer size) {
        UserDto initiator = findUserById(userId)
                .orElseThrow(() ->
                        new NotFoundException("Пользователь с идентификатором " + userId + " не найден."));

        Pageable pageable = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findAllByInitiatorId(initiator.getId(), pageable);
        return EventMapper.toEventShortDtoList(events);
    }

    @Override
    public List<EventShortDto> getEventsByUser(String text, List<Long> categories, Boolean paid,
                                               LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                               Boolean onlyAvailable, SortValue sort,
                                               Integer from, Integer size, HttpServletRequest request) {

        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new ValidationException("Некорректные параметры временного интервала.");
        }

        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Event> criteria = builder.createQuery(Event.class);
        Root<Event> root = criteria.from(Event.class);

        root.fetch("category", JoinType.LEFT);

        List<Predicate> conditions = new ArrayList<>();

        if (text != null && !text.isBlank()) {
            String pattern = "%" + text.toLowerCase() + "%";
            conditions.add(builder.or(
                    builder.like(builder.lower(root.get("annotation")), pattern),
                    builder.like(builder.lower(root.get("description")), pattern)
            ));
        }

        if (categories != null && !categories.isEmpty()) {
            conditions.add(root.get("category").get("id").in(categories));
        }

        if (paid != null) {
            conditions.add(builder.equal(root.get("paid"), paid));
        }

        LocalDateTime limitStart = (rangeStart != null) ? rangeStart : LocalDateTime.now();
        conditions.add(builder.greaterThan(root.get("eventDate"), limitStart));

        if (rangeEnd != null) {
            conditions.add(builder.lessThan(root.get("eventDate"), rangeEnd));
        }

        conditions.add(builder.equal(root.get("state"), EventState.PUBLISHED));

        if (onlyAvailable != null && onlyAvailable) {
            conditions.add(builder.or(
                    builder.equal(root.get("participantLimit"), 0),
                    builder.lessThan(root.get("confirmedRequests"), root.get("participantLimit"))
            ));
        }

        criteria.where(conditions.toArray(new Predicate[0]));

        if (sort == SortValue.VIEWS) {
            criteria.orderBy(builder.desc(root.get("views")));
        } else {
            criteria.orderBy(builder.desc(root.get("eventDate")));
        }

        List<Event> result = entityManager.createQuery(criteria)
                .setFirstResult(from)
                .setMaxResults(size)
                .getResultList();

        sendHitAsync(request.getRequestURI(), request.getRemoteAddr());

        return EventMapper.toEventShortDtoList(result);
    }

    @Override
    public EventFullDto getEventByUser(Long userId, Long eventId) {
        Event event = findEventByIdAndInitiatorId(eventId, userId);
        long confirmed = requestClient
                .countEventsInStatus(eventId, RequestStatus.CONFIRMED);

        return EventMapper.toFullEventDto(event, confirmed);
    }

    @Override
    @Transactional
    public EventFullDto updateEventByUser(Long userId, Long eventId, UpdateEventUserRequest dto) {
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "Событие " + eventId + " для пользователя " + userId + " не найдено.")
                );

        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Редактирование уже опубликованного события недоступно.");
        }

        applyUserUpdate(event, dto);
        applyUserStateAction(event, dto.getStateAction());

        Event updated = eventRepository.save(event);
        return EventMapper.toFullEventDto(updated);
    }

    @Override
    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest dto) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() ->
                        new NotFoundException("Событие с идентификатором " + eventId + " не найдено.")
                );

        handleAdminStateChange(event, dto.getStateAction());
        applyAdminUpdate(event, dto);

        Event saved = eventRepository.save(event);
        return EventMapper.toFullEventDto(saved);
    }

    @Override
    public EventRequestStatusUpdateResult updateParticipationRequestStatus(
            Long userId, Long eventId, EventRequestStatusUpdateRequest updateRequest) {
        try {
            return requestClient.updateRequestStatus(userId, eventId, updateRequest);
        } catch (FeignException.Conflict ex) {
            throw new ConflictException("Не удалось изменить статус заявок: " + ex.getMessage());
        }
    }

    @Override
    public List<ParticipationRequestDto> getEventParticipationRequests(Long userId, Long eventId) {
        return requestClient.getRequestsForEventByOwner(userId, eventId);
    }

    @Override
    public void updateConfirmedRequests(Long eventId, Long confirmedRequests) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException(
                        "Невозможно обновить подтверждённые заявки: событие с id=" + eventId + " не найдено.")
                );
        event.setConfirmedRequests(confirmedRequests);
        eventRepository.save(event);
    }

    @Override
    public List<EventFullDto> getEventsByAdmin(
            List<Long> users,
            List<EventState> states,
            List<Long> categories,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd,
            Integer from,
            Integer size
    ) {
        validateRange(rangeStart, rangeEnd);

        List<Predicate> predicates =
                buildAdminSearchPredicates(users, states, categories, rangeStart, rangeEnd);

        List<Event> events = findEventsWithPredicates(predicates, null, from, size);

        return events.stream()
                .map(EventMapper::toFullEventDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<EventShortDto> searchPublicEvents(String text,
                                                  List<Long> categories,
                                                  Boolean paid,
                                                  LocalDateTime rangeStart,
                                                  LocalDateTime rangeEnd,
                                                  Boolean onlyAvailable,
                                                  SortValue sort,
                                                  Integer from,
                                                  Integer size,
                                                  HttpServletRequest request) {
        validateRange(rangeStart, rangeEnd);

        List<Predicate> predicates = buildPublicSearchPredicates(
                text, categories, paid, rangeStart, rangeEnd, onlyAvailable
        );

        List<Event> events = findEventsWithPredicates(predicates, sort, from, size);

        handleStatsForSearch(request);
        return mapAndFilterAvailableEvents(events, onlyAvailable);
    }

    @Override
    @Transactional(readOnly = true)
    public EventFullDto getEvent(Long eventId, HttpServletRequest request) {
        Event event = loadPublishedEventOrThrow(eventId);
        updateViewsFromStats(event, request);
        sendHitAsync(request.getRequestURI(), request.getRemoteAddr());

        long confirmed = requestClient
                .countEventsInStatus(eventId, RequestStatus.CONFIRMED);

        return EventMapper.toFullEventDto(event, confirmed);
    }

    private Event loadPublishedEventOrThrow(Long eventId) {
        return eventRepository.findByIdAndState(eventId, EventState.PUBLISHED)
                .orElseThrow(() -> new NotFoundException(
                        "Опубликованное событие с id=" + eventId + " не обнаружено.")
                );
    }

    private void updateViewsFromStats(Event event, HttpServletRequest request) {
        LocalDateTime start = event.getPublishedOn() != null
                ? event.getPublishedOn()
                : event.getCreatedOn();

        List<ViewStatsDto> stats = statsClient.getStats(
                start,
                LocalDateTime.now().plusSeconds(1),
                List.of(request.getRequestURI()),
                true
        );

        long hits = stats.isEmpty() ? 0L : stats.get(0).getHits();
        event.setViews(hits);
    }

    private void handleStatsForSearch(HttpServletRequest request) {
        sendHitAsync(request.getRequestURI(), request.getRemoteAddr());
    }

    private List<EventShortDto> mapAndFilterAvailableEvents(List<Event> events, Boolean onlyAvailable) {
        List<EventShortDto> shortDtos = EventMapper.toEventShortDtoList(events);

        if (!Boolean.TRUE.equals(onlyAvailable)) {
            return shortDtos;
        }

        return shortDtos.stream()
                .filter(dto -> dto.getParticipantLimit() == 0
                        || dto.getConfirmedRequests() < dto.getParticipantLimit())
                .collect(Collectors.toList());
    }

    private void applyAdminUpdate(Event event, UpdateEventAdminRequest dto) {
        if (dto.getAnnotation() != null) {
            event.setAnnotation(dto.getAnnotation());
        }
        if (dto.getDescription() != null) {
            event.setDescription(dto.getDescription());
        }
        if (dto.getEventDate() != null) {
            validateEventDate(dto.getEventDate(), 1);
            event.setEventDate(dto.getEventDate());
        }
        if (dto.getPaid() != null) {
            event.setPaid(dto.getPaid());
        }
        if (dto.getTitle() != null) {
            event.setTitle(dto.getTitle());
        }
        if (dto.getCategory() != null) {
            Category category = categoryRepository.findById(dto.getCategory())
                    .orElseThrow(() -> new NotFoundException(
                            "Категория с id=" + dto.getCategory() + " не найдена при обновлении события."));
            event.setCategory(category);
        }
        if (dto.getLocation() != null) {
            event.setLocation(resolveLocation(dto.getLocation()));
        }
        if (dto.getParticipantLimit() != null) {
            event.setParticipantLimit(dto.getParticipantLimit());
        }
        if (dto.getRequestModeration() != null) {
            event.setRequestModeration(dto.getRequestModeration());
        }
    }

    private void applyUserUpdate(Event event, UpdateEventUserRequest dto) {
        if (dto.getAnnotation() != null) {
            event.setAnnotation(dto.getAnnotation());
        }
        if (dto.getDescription() != null) {
            event.setDescription(dto.getDescription());
        }
        if (dto.getEventDate() != null) {
            validateEventDate(dto.getEventDate(), 1);
            event.setEventDate(dto.getEventDate());
        }
        if (dto.getPaid() != null) {
            event.setPaid(dto.getPaid());
        }
        if (dto.getParticipantLimit() != null) {
            event.setParticipantLimit(dto.getParticipantLimit());
        }
        if (dto.getRequestModeration() != null) {
            event.setRequestModeration(dto.getRequestModeration());
        }
        if (dto.getTitle() != null) {
            event.setTitle(dto.getTitle());
        }
        if (dto.getCategory() != null) {
            Category category = categoryRepository.findById(dto.getCategory())
                    .orElseThrow(() -> new NotFoundException(
                            "Указанная категория (id=" + dto.getCategory() + ") не найдена."));
            event.setCategory(category);
        }
        if (dto.getLocation() != null) {
            event.setLocation(resolveLocation(dto.getLocation()));
        }
    }

    private void handleAdminStateChange(Event event, StateActionAdmin action) {
        if (action == null) {
            return;
        }

        if (action == StateActionAdmin.PUBLISH_EVENT) {
            publishEvent(event);
        } else if (action == StateActionAdmin.REJECT_EVENT) {
            rejectEvent(event);
        }
    }

    private void publishEvent(Event event) {
        if (event.getState() != EventState.PENDING) {
            throw new ConflictException(
                    "Опубликовать можно только событие в статусе ожидания. Текущее состояние: " + event.getState()
            );
        }
        event.setState(EventState.PUBLISHED);
        event.setPublishedOn(LocalDateTime.now());
    }

    private void rejectEvent(Event event) {
        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Отказать в публикации уже опубликованному событию нельзя.");
        }
        event.setState(EventState.CANCELED);
    }

    private void applyUserStateAction(Event event, UserEventStateAction action) {
        if (action == null) {
            return;
        }
        switch (action) {
            case SEND_TO_REVIEW -> event.setState(EventState.PENDING);
            case CANCEL_REVIEW -> event.setState(EventState.CANCELED);
        }
    }

    private void validateEventDate(LocalDateTime eventDate, int hours) {
        if (eventDate.isBefore(LocalDateTime.now().plusHours(hours))) {
            throw new ValidationException(
                    "Дата и время проведения должны быть минимум через " + hours +
                            " час(а) от текущего момента."
            );
        }
    }

    private void validateRange(LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new ValidationException("Интервал дат указан некорректно: начало позже окончания.");
        }
    }

    private Location resolveLocation(LocationDto locationDto) {
        return locationRepository.findByLatAndLon(locationDto.getLat(), locationDto.getLon())
                .orElseGet(() ->
                        locationRepository.save(LocationMapper.toLocation(locationDto))
                );
    }

    private Event findEventByIdAndInitiatorId(Long eventId, Long userId) {
        return eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "Событие с id=" + eventId + " для инициатора id=" + userId + " не найдено."
                ));
    }

    private Optional<UserDto> findUserById(Long userId) {
        List<UserDto> userDtos = userClient.getUsers(List.of(userId));
        return userDtos.isEmpty() ? Optional.empty() : Optional.of(userDtos.getFirst());
    }

    private List<Predicate> buildAdminSearchPredicates(List<Long> users,
                                                       List<EventState> states,
                                                       List<Long> categories,
                                                       LocalDateTime rangeStart,
                                                       LocalDateTime rangeEnd) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Event> query = builder.createQuery(Event.class);
        Root<Event> root = query.from(Event.class);

        List<Predicate> predicates = new ArrayList<>();

        if (users != null && !users.isEmpty()) {
            predicates.add(root.get("initiator").in(users));
        }
        if (states != null && !states.isEmpty()) {
            predicates.add(root.get("state").in(states));
        }
        if (categories != null && !categories.isEmpty()) {
            predicates.add(root.get("category").get("id").in(categories));
        }
        if (rangeStart != null) {
            predicates.add(builder.greaterThanOrEqualTo(root.get("eventDate"), rangeStart));
        }
        if (rangeEnd != null) {
            predicates.add(builder.lessThanOrEqualTo(root.get("eventDate"), rangeEnd));
        }

        return predicates;
    }

    private List<Predicate> buildPublicSearchPredicates(String text,
                                                        List<Long> categories,
                                                        Boolean paid,
                                                        LocalDateTime rangeStart,
                                                        LocalDateTime rangeEnd,
                                                        Boolean onlyAvailable) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Event> query = builder.createQuery(Event.class);
        Root<Event> root = query.from(Event.class);

        List<Predicate> predicates = new ArrayList<>();

        predicates.add(builder.equal(root.get("state"), EventState.PUBLISHED));

        if (text != null && !text.isBlank()) {
            String pattern = "%" + text.toLowerCase() + "%";
            predicates.add(builder.or(
                    builder.like(builder.lower(root.get("annotation")), pattern),
                    builder.like(builder.lower(root.get("description")), pattern)
            ));
        }

        if (categories != null && !categories.isEmpty()) {
            predicates.add(root.get("category").get("id").in(categories));
        }

        if (paid != null) {
            predicates.add(builder.equal(root.get("paid"), paid));
        }

        if (rangeStart == null && rangeEnd == null) {
            predicates.add(builder.greaterThanOrEqualTo(root.get("eventDate"), LocalDateTime.now()));
        } else {
            if (rangeStart != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("eventDate"), rangeStart));
            }
            if (rangeEnd != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("eventDate"), rangeEnd));
            }
        }

        if (Boolean.TRUE.equals(onlyAvailable)) {
            predicates.add(builder.or(
                    builder.equal(root.get("participantLimit"), 0),
                    builder.lessThan(root.get("confirmedRequests"), root.get("participantLimit"))
            ));
        }

        return predicates;
    }

    private List<Event> findEventsWithPredicates(List<Predicate> predicates,
                                                 SortValue sort,
                                                 int from,
                                                 int size) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Event> query = builder.createQuery(Event.class);
        Root<Event> root = query.from(Event.class);

        root.fetch("initiator", JoinType.LEFT);
        root.fetch("category", JoinType.LEFT);

        query.where(predicates.toArray(new Predicate[0]));
        applySorting(builder, query, root, sort);

        return entityManager.createQuery(query)
                .setFirstResult(from)
                .setMaxResults(size)
                .getResultList();
    }

    private void applySorting(CriteriaBuilder builder,
                              CriteriaQuery<Event> query,
                              Root<Event> root,
                              SortValue sort) {
        if (sort == null) {
            return;
        }

        if (sort == SortValue.VIEWS) {
            query.orderBy(builder.desc(root.get("views")));
        } else {
            query.orderBy(builder.asc(root.get("eventDate")));
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementEventViews(Long eventId) {
        eventRepository.incrementViews(eventId);
    }

    @Async
    public void sendHitAsync(String uri, String ip) {
        HitDto hitDto = new HitDto(
                null,
                appName,
                uri,
                ip,
                LocalDateTime.now()
        );
        statsClient.saveHit(hitDto);
    }
}