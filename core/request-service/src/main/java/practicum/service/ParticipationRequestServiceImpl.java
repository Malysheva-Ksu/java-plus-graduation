package practicum.service;

import jakarta.ws.rs.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import practicum.client.EventClient;
import practicum.client.UserClient;
import practicum.exception.ConflictException;
import practicum.exception.NotFoundException;
import practicum.exception.ValidationException;
import practicum.mapper.ParticipationRequestMapper;
import practicum.model.Event;
import practicum.model.ParticipationRequest;
import practicum.model.User;
import practicum.model.dto.event.EventFullDto;
import practicum.model.dto.request.EventRequestStatusUpdateRequest;
import practicum.model.dto.request.EventRequestStatusUpdateResult;
import practicum.model.dto.request.ParticipationRequestDto;
import practicum.model.dto.user.UserDto;
import practicum.model.enums.EventState;
import practicum.model.enums.RequestStatus;
import practicum.repository.EventRepository;
import practicum.repository.ParticipationRequestRepository;
import practicum.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParticipationRequestServiceImpl implements ParticipationRequestService {

    private final ParticipationRequestRepository requestRepository;
    private final UserClient userClient;
    private final EventClient eventClient;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;

    @Transactional
    public ParticipationRequestDto createRequest(Long userId, Long eventId) {
        if (requestRepository.existsByEventIdAndRequesterId(eventId, userId)) {
            throw new ConflictException("Запрос на участие уже существует");
        }

        User requester = userRepository.findById(userId).orElseGet(() -> {
            UserDto userDto = userClient.getUser(userId)
                    .orElseThrow(() -> new NotFoundException("User not found"));
            return userRepository.save(User.builder()
                    .id(userDto.getId())
                    .name(userDto.getName())
                    .email(userDto.getEmail())
                    .build());
        });

        EventFullDto eventDto = loadEvent(eventId);
        Event eventRef = eventRepository.findById(eventId).orElseGet(() -> {
            Event newE = new Event();
            newE.setId(eventDto.getId());
            return eventRepository.save(newE);
        });

        validateRequestCreation(userId, eventDto);

        ParticipationRequest request = ParticipationRequest.builder()
                .requester(requester)
                .event(eventRef)
                .created(LocalDateTime.now())
                .status(calculateInitialStatus(eventDto))
                .build();

        ParticipationRequest saved = requestRepository.save(request);

        if (saved.getStatus() == RequestStatus.CONFIRMED) {
            updateConfirmedCount(eventId);
        }

        return ParticipationRequestMapper.toParticipationRequestDto(saved);
    }

    private void updateConfirmedCount(Long eventId) {
        long confirmedCount = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);

        log.info("Обновление количества подтвержденных заявок для eventId={}: {}", eventId, confirmedCount);

        try {
            eventClient.updateConfirmedRequests(eventId, confirmedCount);
        } catch (Exception e) {
            log.error("Не удалось обновить счетчик заявок в event-service для eventId={}: {}",
                    eventId, e.getMessage());
        }
    }

    private RequestStatus calculateInitialStatus(EventFullDto event) {
        if (!Boolean.TRUE.equals(event.getRequestModeration()) ||
                (event.getParticipantLimit() != null && event.getParticipantLimit() == 0)) {
            return RequestStatus.CONFIRMED;
        }
        return RequestStatus.PENDING;
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateRequests(Long userId,
                                                         Long eventId,
                                                         EventRequestStatusUpdateRequest statusUpdateRequest) {
        log.info("Пользователь id={} изменяет статусы заявок {} для события id={}",
                userId, statusUpdateRequest.getRequestIds(), eventId);

        EventFullDto eventDto = loadEvent(eventId);

        if (!Objects.equals(eventDto.getInitiator(), userId)) {
            throw new ConflictException("Изменять статусы заявок может только инициатор события.");
        }

        boolean moderationRequired = Boolean.TRUE.equals(eventDto.getRequestModeration());
        long limit = eventDto.getParticipantLimit() != null ? eventDto.getParticipantLimit() : 0L;

        if (!moderationRequired || limit == 0) {
            log.info("Событие id={} не требует модерации или не имеет ограничения по участникам.", eventId);
            return new EventRequestStatusUpdateResult(List.of(), List.of());
        }

        List<ParticipationRequest> requestsToUpdate =
                requestRepository.findAllByIdIn(statusUpdateRequest.getRequestIds());

        if (requestsToUpdate.stream().anyMatch(req -> req.getStatus() != RequestStatus.PENDING)) {
            throw new ConflictException("Можно обрабатывать только заявки в статусе PENDING.");
        }

        List<ParticipationRequest> confirmedRequests = new ArrayList<>();
        List<ParticipationRequest> rejectedRequests = new ArrayList<>();
        RequestStatus newStatus = statusUpdateRequest.getStatus();

        long currentConfirmedCount =
                requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);

        if (newStatus == RequestStatus.REJECTED) {
            requestsToUpdate.forEach(req -> req.setStatus(RequestStatus.REJECTED));
            rejectedRequests.addAll(requestsToUpdate);
        } else if (newStatus == RequestStatus.CONFIRMED) {
            if (currentConfirmedCount >= limit) {
                requestsToUpdate.forEach(req -> req.setStatus(RequestStatus.REJECTED));
                rejectedRequests.addAll(requestsToUpdate);
                throw new ConflictException("Максимальное количество участников уже достигнуто.");
            }

            for (ParticipationRequest request : requestsToUpdate) {
                if (currentConfirmedCount < limit) {
                    request.setStatus(RequestStatus.CONFIRMED);
                    confirmedRequests.add(request);
                    currentConfirmedCount++;
                } else {
                    request.setStatus(RequestStatus.REJECTED);
                    rejectedRequests.add(request);
                }
            }

            if (currentConfirmedCount >= limit) {
                List<ParticipationRequest> otherPending =
                        requestRepository.findAllByEventIdAndStatus(eventId, RequestStatus.PENDING);
                otherPending.forEach(req -> req.setStatus(RequestStatus.REJECTED));
                rejectedRequests.addAll(otherPending);
                log.info("Для события {} достигнут лимит участников. Автоматически отклонено ещё {} заявок.",
                        eventId, otherPending.size());
            }

            eventClient.updateConfirmedRequests(eventId, currentConfirmedCount);
        }

        requestRepository.saveAll(confirmedRequests);
        requestRepository.saveAll(rejectedRequests);
        requestRepository.flush();

        return new EventRequestStatusUpdateResult(
                toDtoList(confirmedRequests),
                toDtoList(rejectedRequests)
        );
    }

    @Override
    public List<ParticipationRequestDto> getUserRequests(Long userId) {
        log.info("Получение всех заявок на участие пользователя id={}", userId);
        ensureUserExists(userId);
        return toDtoList(requestRepository.findAllByRequesterId(userId));
    }

    @Override
    public List<ParticipationRequestDto> getRequestsByOwner(Long userId, Long eventId) {
        log.info("Инициатор id={} запрашивает список заявок для события id={}", userId, eventId);

        EventFullDto eventDto = loadEvent(eventId);
        if (!Objects.equals(eventDto.getInitiator(), userId)) {
            throw new ConflictException(
                    "Пользователь " + userId + " не является инициатором события " + eventId
            );
        }

        return toDtoList(requestRepository.findAllByEventId(eventId));
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        ParticipationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Запрос id=" + requestId + " не найден"));

        if (!request.getRequester().getId().equals(userId)) {
            throw new ValidationException("Нельзя отменить чужой запрос");
        }

        request.setStatus(RequestStatus.CANCELED);
        return ParticipationRequestMapper.toParticipationRequestDto(requestRepository.save(request));
    }

    @Override
    public long countEventsInStatus(Long eventId, RequestStatus status) {
        return requestRepository.countByEventIdAndStatus(eventId, status);
    }

    @Override
    public Map<Long, Long> countConfirmedRequestsForEvents(Set<Long> eventIds) {
        return requestRepository.countConfirmedRequestsForEvents(eventIds);
    }

    private void validateRequestCreation(Long userId, EventFullDto event) {
        if (requestRepository.existsByEventIdAndRequesterId(event.getId(), userId)) {
            throw new ConflictException(
                    "Запрос от пользователя " + userId + " на событие " + event.getId() + " уже существует."
            );
        }

        if (Objects.equals(event.getInitiator(), userId)) {
            throw new ConflictException("Инициатор не может отправлять заявку на собственное событие.");
        }

        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException(
                    "Нельзя подать заявку на событие, которое не опубликовано. Текущий статус: " + event.getState()
            );
        }

        Long limit = event.getParticipantLimit();
        if (limit != null && limit > 0) {
            long confirmedCount =
                    requestRepository.countByEventIdAndStatus(event.getId(), RequestStatus.CONFIRMED);
            if (confirmedCount >= limit) {
                throw new ConflictException(
                        "Лимит участников для события " + event.getId() + " уже исчерпан."
                );
            }
        }
    }

    private EventFullDto loadEvent(Long eventId) {
        return eventClient.getEvent(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено."));
    }

    private void ensureUserExists(Long userId) {
        List<UserDto> users = userClient.getUsers(List.of(userId));
        if (users == null || users.isEmpty()) {
            throw new NotFoundException("Пользователь с id=" + userId + " не найден.");
        }
    }

    private User loadUserEntity(Long userId) {
        UserDto dto = userClient.getUsers(List.of(userId)).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден."));

        return User.builder()
                .id(dto.getId())
                .email(dto.getEmail())
                .name(dto.getName())
                .build();
    }

    private Event toEventRef(EventFullDto dto) {
        Event event = new Event();
        event.setId(dto.getId());
        return event;
    }

    private List<ParticipationRequestDto> toDtoList(List<ParticipationRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        return requests.stream()
                .map(ParticipationRequestMapper::toParticipationRequestDto)
                .collect(Collectors.toList());
    }
}