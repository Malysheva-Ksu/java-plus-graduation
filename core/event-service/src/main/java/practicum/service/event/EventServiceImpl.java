package practicum.service.event;

import feign.FeignException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import practicum.model.dto.event.*;
import practicum.model.dto.location.LocationDto;
import practicum.model.dto.request.EventRequestStatusUpdateRequest;
import practicum.model.dto.request.EventRequestStatusUpdateResult;
import practicum.model.dto.request.ParticipationRequestDto;
import practicum.model.dto.user.UserDto;
import practicum.model.enums.*;
import practicum.repository.CategoryRepository;
import practicum.repository.EventRepository;
import practicum.repository.LocationRepository;

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

    private final EventRepository eventRepository;
    private final UserClient userClient;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final RequestClient participationRequestClient;
    private final EntityManager entityManager;
    private final StatsClient statsClient;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

    @Override
    public Optional<EventFullDto> getEvent(Long eventId) {
        return eventRepository.findById(eventId).map(EventMapper::toFullEventDto);
    }

    @Override
    public EventRequestStatusUpdateResult updateParticipationRequestStatus(
            Long userId, Long eventId, EventRequestStatusUpdateRequest requestStatusUpdateDto
    ) {
        try {
            return participationRequestClient.updateRequestStatus(userId, eventId, requestStatusUpdateDto);
        } catch (FeignException.Conflict e) {
            throw new ConflictException(e.getMessage());
        }
    }

    @Override
    public List<ParticipationRequestDto> getEventParticipationRequests(Long userId, Long eventId) {
        return participationRequestClient.getRequestsForEventByOwner(userId, eventId);
    }

    @Override
    @Transactional
    public void updateConfirmedRequests(Long eventId, Long confirmedRequests) {
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new NotFoundException("Событие не найдено"));
        event.setConfirmedRequests(confirmedRequests);
        eventRepository.save(event);
        eventRepository.updateConfirmedRequests(eventId, confirmedRequests);
    }

    @Override
    public List<EventShortDto> getEvents(Long userId, Integer from, Integer size) {
        Optional<UserDto> userDto = findUserById(userId);
        if (userDto.isEmpty()) throw new NotFoundException("Пользователь с ID=" + userId + " не найден.");

        Pageable page = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findAllByInitiator(userId, page);

        return EventMapper.toEventShortDtoList(events);
    }

    @Override
    public EventFullDto getEventByUser(Long userId, Long eventId) {
        Event event = findEventByIdAndInitiatorId(eventId, userId);
        long confirmedRequests = participationRequestClient.countEventsInStatus(eventId, RequestStatus.CONFIRMED);

        return EventMapper.toFullEventDto(event, confirmedRequests);
    }

    private void updateEventFromAdminRequest(Event event, UpdateEventAdminRequest dto) {
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
                    .orElseThrow(() -> new NotFoundException("Категория с ID=" + dto.getCategory() + " не найдена."));
            event.setCategory(category);
        }

        if (dto.getLocation() != null) {
            event.setLocation(getLocation(dto.getLocation()));
        }

        if (dto.getParticipantLimit() != null) {
            event.setParticipantLimit(dto.getParticipantLimit());
        }

        if (dto.getRequestModeration() != null) {
            event.setRequestModeration(dto.getRequestModeration());
        }
    }

    private void updateEventFromUserRequest(Event event, UpdateEventUserRequest dto) {
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
                    .orElseThrow(() -> new NotFoundException("Категория с ID=" + dto.getCategory() + " не найдена."));
            event.setCategory(category);
        }
        if (dto.getLocation() != null) {
            event.setLocation(getLocation(dto.getLocation()));
        }
    }

    @Override
    @Transactional
    public EventFullDto updateEventByUser(Long userId, Long eventId, UpdateEventUserRequest updateRequest) {
        Event event = eventRepository.findByIdAndInitiator(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Событие с ID=" + eventId + " и инициатором ID=" + userId + " не найдено."));

        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Нельзя изменить уже опубликованное событие.");
        }

        updateEventFromUserRequest(event, updateRequest);

        if (updateRequest.getStateAction() != null) {
            if (updateRequest.getStateAction() == UserEventStateAction.SEND_TO_REVIEW) {
                event.setState(EventState.PENDING);
            } else if (updateRequest.getStateAction() == UserEventStateAction.CANCEL_REVIEW) {
                event.setState(EventState.CANCELED);
            }
        }

        Event updatedEvent = eventRepository.save(event);
        return EventMapper.toFullEventDto(updatedEvent);
    }

    @Override
    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest updateRequest) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с ID=" + eventId + " не найдено."));

        if (updateRequest.getStateAction() != null) {
            if (updateRequest.getStateAction() == StateActionAdmin.PUBLISH_EVENT) {
                if (event.getState() != EventState.PENDING) {
                    throw new ConflictException("Нельзя опубликовать событие, так как оно не в состоянии ожидания. Текущий статус: " + event.getState());
                }
                event.setState(EventState.PUBLISHED);
                event.setPublishedOn(LocalDateTime.now());
            } else if (updateRequest.getStateAction() == StateActionAdmin.REJECT_EVENT) {
                if (event.getState() == EventState.PUBLISHED) {
                    throw new ConflictException("Нельзя отклонить уже опубликованное событие.");
                }
                event.setState(EventState.CANCELED);
            }
        }

        updateEventFromAdminRequest(event, updateRequest);

        return EventMapper.toFullEventDto(eventRepository.save(event));
    }

    @Override
    public List<EventShortDto> getEventsByUser(String text, List<Long> categories, Boolean paid,
                                               LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                               Boolean onlyAvailable, SortValue sort,
                                               Integer from, Integer size, HttpServletRequest request) {

        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new ValidationException("Дата начала не может быть позже даты окончания.");
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Event> query = cb.createQuery(Event.class);
        Root<Event> eventRoot = query.from(Event.class);

        List<Predicate> predicates = new ArrayList<>();

        if (text != null && !text.isBlank()) {
            predicates.add(cb.or(
                    cb.like(cb.lower(eventRoot.get("annotation")), "%" + text.toLowerCase() + "%"),
                    cb.like(cb.lower(eventRoot.get("description")), "%" + text.toLowerCase() + "%")
            ));
        }

        if (categories != null && !categories.isEmpty()) {
            predicates.add(eventRoot.get("category").get("id").in(categories));
        }

        if (paid != null) {
            predicates.add(cb.equal(eventRoot.get("paid"), paid));
        }

        LocalDateTime startDateTime = (rangeStart != null) ? rangeStart : LocalDateTime.now();
        predicates.add(cb.greaterThan(eventRoot.get("eventDate"), startDateTime));
        if (rangeEnd != null) {
            predicates.add(cb.lessThan(eventRoot.get("eventDate"), rangeEnd));
        }

        predicates.add(cb.equal(eventRoot.get("state"), EventState.PUBLISHED));

        query.where(predicates.toArray(new Predicate[0]));

        if (sort == SortValue.VIEWS) {
            query.orderBy(cb.desc(eventRoot.get("views")));
        } else {
            query.orderBy(cb.desc(eventRoot.get("eventDate")));
        }

        List<Event> events = entityManager.createQuery(query)
                .setFirstResult(from)
                .setMaxResults(size)
                .getResultList();

        sendHitAsync(request.getRequestURI(), request.getRemoteAddr());

        List<EventShortDto> shortDtos = EventMapper.toEventShortDtoList(events);

        if (onlyAvailable != null && onlyAvailable) {
            return shortDtos.stream()
                    .filter(dto -> dto.getParticipantLimit() == 0 || dto.getConfirmedRequests() < dto.getParticipantLimit())
                    .collect(Collectors.toList());
        }

        return shortDtos;
    }

    @Override
    @Transactional(readOnly = true)
    public EventFullDto getEvent(Long eventId, HttpServletRequest request) {
        Event event = eventRepository.findByIdAndState(eventId, EventState.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Опубликованное событие с ID=" + eventId + " не найдено."));

        List<ViewStatsDto> stats = statsClient.getStats(
                event.getPublishedOn() != null ? event.getPublishedOn() : event.getCreatedOn(),
                LocalDateTime.now().plusSeconds(1),
                List.of(request.getRequestURI()),
                true
        );

        if (!stats.isEmpty()) {
            event.setViews(stats.get(0).getHits());
        } else {
            event.setViews(0L);
        }

        sendHitAsync(request.getRequestURI(), request.getRemoteAddr());

        long confirmedRequests = participationRequestClient.countEventsInStatus(eventId, RequestStatus.CONFIRMED);

        return EventMapper.toFullEventDto(event, confirmedRequests);
    }

    @Override
    public List<EventFullDto> getEventsByAdmin(
            List<Long> users, List<EventState> states, List<Long> categories,
            LocalDateTime rangeStart, LocalDateTime rangeEnd, Integer from, Integer size
    ) {
        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new ValidationException("Дата начала не может быть позже даты окончания.");
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Event> query = cb.createQuery(Event.class);
        Root<Event> eventRoot = query.from(Event.class);

        List<Predicate> predicates = new ArrayList<>();

        if (users != null && !users.isEmpty()) {
            predicates.add(eventRoot.get("initiator").in(users));
        }

        if (states != null && !states.isEmpty()) {
            predicates.add(eventRoot.get("state").in(states));
        }

        if (categories != null && !categories.isEmpty()) {
            predicates.add(eventRoot.get("category").get("id").in(categories));
        }

        if (rangeStart != null) {
            predicates.add(cb.greaterThanOrEqualTo(eventRoot.get("eventDate"), rangeStart));
        }

        if (rangeEnd != null) {
            predicates.add(cb.lessThanOrEqualTo(eventRoot.get("eventDate"), rangeEnd));
        }

        query.where(predicates.toArray(new Predicate[0]));

        List<Event> events = entityManager.createQuery(query)
                .setFirstResult(from)
                .setMaxResults(size)
                .getResultList();

        return events.stream()
                .map(EventMapper::toFullEventDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<EventShortDto> searchPublicEvents(String text, List<Long> categories, Boolean paid,
                                                  LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                                  Boolean onlyAvailable, SortValue sort,
                                                  Integer from, Integer size, HttpServletRequest request) {

        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new ValidationException("Дата начала не может быть позже даты окончания.");
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Event> query = cb.createQuery(Event.class);
        Root<Event> eventRoot = query.from(Event.class);

        List<Predicate> predicates = new ArrayList<>();

        if (text != null && !text.isBlank()) {
            predicates.add(cb.or(
                    cb.like(cb.lower(eventRoot.get("annotation")), "%" + text.toLowerCase() + "%"),
                    cb.like(cb.lower(eventRoot.get("description")), "%" + text.toLowerCase() + "%")
            ));
        }

        if (categories != null && !categories.isEmpty()) {
            predicates.add(eventRoot.get("category").get("id").in(categories));
        }

        if (paid != null) {
            predicates.add(cb.equal(eventRoot.get("paid"), paid));
        }

        LocalDateTime startDateTime = (rangeStart != null) ? rangeStart : LocalDateTime.now();
        predicates.add(cb.greaterThan(eventRoot.get("eventDate"), startDateTime));
        if (rangeEnd != null) {
            predicates.add(cb.lessThan(eventRoot.get("eventDate"), rangeEnd));
        }

        predicates.add(cb.equal(eventRoot.get("state"), EventState.PUBLISHED));

        query.where(predicates.toArray(new Predicate[0]));

        if (sort == SortValue.VIEWS) {
            query.orderBy(cb.desc(eventRoot.get("views")));
        } else {
            query.orderBy(cb.desc(eventRoot.get("eventDate")));
        }

        List<Event> events = entityManager.createQuery(query)
                .setFirstResult(from)
                .setMaxResults(size)
                .getResultList();

        sendHitAsync(request.getRequestURI(), request.getRemoteAddr());

        List<EventShortDto> shortDtos = EventMapper.toEventShortDtoList(events);

        if (onlyAvailable != null && onlyAvailable) {
            return shortDtos.stream()
                    .filter(dto -> {
                        return dto.getParticipantLimit() == 0 || dto.getConfirmedRequests() < dto.getParticipantLimit();
                    })
                    .collect(Collectors.toList());
        }

        return shortDtos;
    }

    private void validateEventDate(LocalDateTime eventDate, int hours) {
        if (eventDate.isBefore(LocalDateTime.now().plusHours(hours))) {
            throw new ValidationException("Дата события должна быть как минимум через " + hours + " часа от текущего момента.");
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementEventViews(Long eventId) {
        eventRepository.incrementViews(eventId);
    }

    private List<Event> findEventsWithPredicates(List<Predicate> predicates, SortValue sort, int from, int size) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Event> query = builder.createQuery(Event.class);
        Root<Event> root = query.from(Event.class);

        root.fetch("initiator", JoinType.LEFT);
        root.fetch("category", JoinType.LEFT);

        query.where(predicates.toArray(new Predicate[0]));

        if (sort != null) {
            if (sort == SortValue.VIEWS) {
                query.orderBy(builder.desc(root.get("views")));
            } else {
                query.orderBy(builder.asc(root.get("eventDate")));
            }
        }

        return entityManager.createQuery(query)
                .setFirstResult(from)
                .setMaxResults(size)
                .getResultList();
    }

    private Location getLocation(LocationDto locationDto) {
        return locationRepository.findByLatAndLon(locationDto.getLat(), locationDto.getLon())
                .orElseGet(() -> locationRepository.save(LocationMapper.toLocation(locationDto)));
    }

    private List<Predicate> buildAdminSearchPredicates(List<Long> users, List<EventState> states, List<Long> categories,
                                                       LocalDateTime rangeStart, LocalDateTime rangeEnd) {
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

    @Async
    public void sendHitAsync(String uri, String ip) {
        HitDto hitDto = new HitDto(null, appName, uri, ip, LocalDateTime.now());
        statsClient.saveHit(hitDto);
    }

    private Event findEventByIdAndInitiatorId(Long eventId, Long userId) {
        return eventRepository.findByIdAndInitiator(eventId, userId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Событие с ID=%d и инициатором ID=%d не найдено.", eventId, userId)
                ));
    }

    private List<Predicate> buildPublicSearchPredicates(String text, List<Long> categories, Boolean paid,
                                                        LocalDateTime rangeStart, LocalDateTime rangeEnd, Boolean onlyAvailable) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Event> query = builder.createQuery(Event.class);
        Root<Event> root = query.from(Event.class);
        List<Predicate> predicates = new ArrayList<>();

        predicates.add(builder.equal(root.get("state"), EventState.PUBLISHED));

        if (text != null && !text.isBlank()) {
            String searchText = "%" + text.toLowerCase() + "%";
            predicates.add(builder.or(
                    builder.like(builder.lower(root.get("annotation")), searchText),
                    builder.like(builder.lower(root.get("description")), searchText)
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

        if (onlyAvailable != null && onlyAvailable) {
            predicates.add(builder.or(
                    builder.equal(root.get("participantLimit"), 0),
                    builder.lessThan(root.get("confirmedRequests"), root.get("participantLimit"))
            ));
        }

        return predicates;
    }

    private Optional<UserDto> findUserById(Long userId) {
        List<UserDto> userDtos = userClient.getUsers(List.of(userId));
        return userDtos.isEmpty() ? Optional.empty() : Optional.of(userDtos.getFirst());
    }
}