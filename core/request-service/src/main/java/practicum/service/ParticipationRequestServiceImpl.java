package practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import practicum.client.EventClient;
import practicum.client.UserClient;
import practicum.exception.ConflictException;
import practicum.exception.NotFoundException;
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
import practicum.repository.ParticipationRequestRepository;

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

    @Override
    @Transactional
    public ParticipationRequestDto createRequest(Long userId, Long eventId) {
        log.info("Пользователь id={} создаёт запрос на участие в событии id={}", userId, eventId);

        User requester = loadUserEntity(userId);
        EventFullDto eventDto = loadEvent(eventId);

        validateRequestCreation(userId, eventDto);

        Event eventRef = toEventRef(eventDto);

        ParticipationRequest request = ParticipationRequest.builder()
                .requester(requester)
                .event(eventRef)
                .created(LocalDateTime.now())
                .build();

        boolean needsModeration = Boolean.TRUE.equals(eventDto.getRequestModeration())
                && eventDto.getParticipantLimit() != null
                && eventDto.getParticipantLimit() != 0;

        request.setStatus(needsModeration ? RequestStatus.PENDING : RequestStatus.CONFIRMED);

        ParticipationRequest savedRequest = requestRepository.save(request);
        log.info("Создан запрос id={} со статусом {}", savedRequest.getId(), savedRequest.getStatus());

        if (savedRequest.getStatus() == RequestStatus.CONFIRMED) {
            long confirmedCount = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
            eventClient.updateConfirmedRequests(eventId, confirmedCount);
        }

        return ParticipationRequestMapper.toParticipationRequestDto(savedRequest);
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateRequests(Long userId,
                                                         Long eventId,
                                                         EventRequestStatusUpdateRequest statusUpdateRequest) {
        log.info("Пользователь id={} изменяет статусы заявок {} для события id={}",
                userId, statusUpdateRequest.getRequestIds(), eventId);

        EventFullDto eventDto = loadEvent(eventId);

        if (!Objects.equals(eventDto.getInitiator().getId(), userId)) {
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
        if (!Objects.equals(eventDto.getInitiator().getId(), userId)) {
            throw new ConflictException(
                    "Пользователь " + userId + " не является инициатором события " + eventId
            );
        }

        return toDtoList(requestRepository.findAllByEventId(eventId));
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        log.info("Пользователь id={} отменяет заявку id={}", userId, requestId);

        ParticipationRequest request = requestRepository.findByIdAndRequesterId(requestId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "Заявка с id=" + requestId + " пользователя " + userId + " не найдена.")
                );

        if (request.getStatus() == RequestStatus.CONFIRMED) {
            throw new ConflictException("Нельзя отменить уже подтверждённую заявку.");
        }

        request.setStatus(RequestStatus.CANCELED);
        ParticipationRequest saved = requestRepository.save(request);

        return ParticipationRequestMapper.toParticipationRequestDto(saved);
    }

    @Override
    public long countEventsInStatus(Long eventId, RequestStatus status) {
        return requestRepository.countByEventAndStatus(eventId, status);
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

        if (Objects.equals(event.getInitiator().getId(), userId)) {
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