package practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import practicum.exception.ConflictException;
import practicum.exception.NotFoundException;
import practicum.mapper.ParticipationRequestMapper;
import practicum.model.Event;
import practicum.model.ParticipationRequest;
import practicum.model.User;
import practicum.model.dto.request.EventRequestStatusUpdateRequest;
import practicum.model.dto.request.EventRequestStatusUpdateResult;
import practicum.model.dto.request.ParticipationRequestDto;
import practicum.model.enums.EventState;
import practicum.model.enums.RequestStatus;
import practicum.repository.EventRepository;
import practicum.repository.ParticipationRequestRepository;
import practicum.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParticipationRequestServiceImpl implements ParticipationRequestService {

    private final ParticipationRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;

    @Override
    @Transactional
    public ParticipationRequestDto createRequest(Long userId, Long eventId) {
        log.info("Пользователь id={} создает запрос на участие в событии id={}", userId, eventId);

        User user = getUser(userId);
        Event event = getEvent(eventId);

        validateRequestCreation(userId, event);

        ParticipationRequest request = ParticipationRequest.builder()
                .requester(user)
                .event(event)
                .created(LocalDateTime.now())
                .build();

        boolean needsModeration = event.getRequestModeration() && event.getParticipantLimit() != 0;
        request.setStatus(needsModeration ? RequestStatus.PENDING : RequestStatus.CONFIRMED);

        ParticipationRequest savedRequest = requestRepository.save(request);
        log.info("Успешно создан запрос id={} со статусом {}", savedRequest.getId(), savedRequest.getStatus());

        return ParticipationRequestMapper.toParticipationRequestDto(savedRequest);
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateRequests(Long userId, Long eventId, EventRequestStatusUpdateRequest statusUpdateRequest) {
        log.info("Пользователь id={} обновляет статусы заявок {} для события id={}", userId, statusUpdateRequest.getRequestIds(), eventId);

        Event event = getEvent(eventId);
        if (!event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("Только инициатор события может обновлять статусы заявок.");
        }

        if (!event.getRequestModeration() || event.getParticipantLimit() == 0) {
            log.warn("Событие id={} не требует модерации заявок или не имеет лимита.", eventId);
            return new EventRequestStatusUpdateResult(List.of(), List.of());
        }

        List<ParticipationRequest> requestsToUpdate = requestRepository.findAllByIdIn(statusUpdateRequest.getRequestIds());
        if (requestsToUpdate.stream().anyMatch(req -> req.getStatus() != RequestStatus.PENDING)) {
            throw new ConflictException("Можно изменять только заявки в статусе PENDING.");
        }

        List<ParticipationRequest> confirmedRequests = new ArrayList<>();
        List<ParticipationRequest> rejectedRequests = new ArrayList<>();
        RequestStatus newStatus = statusUpdateRequest.getStatus();

        long currentConfirmedCount = event.getConfirmedRequests() != null ? event.getConfirmedRequests() : 0L;
        long limit = event.getParticipantLimit();

        if (newStatus == RequestStatus.REJECTED) {
            requestsToUpdate.forEach(request -> request.setStatus(RequestStatus.REJECTED));
            rejectedRequests.addAll(requestsToUpdate);
        } else if (newStatus == RequestStatus.CONFIRMED) {
            if (currentConfirmedCount >= limit) {
                requestsToUpdate.forEach(request -> request.setStatus(RequestStatus.REJECTED));
                rejectedRequests.addAll(requestsToUpdate);
                throw new ConflictException("Лимит участников уже достигнут. Невозможно подтвердить новые заявки.");
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
                List<ParticipationRequest> otherPendingRequests = requestRepository.findAllByEventIdAndStatus(eventId, RequestStatus.PENDING);
                otherPendingRequests.forEach(req -> req.setStatus(RequestStatus.REJECTED));
                rejectedRequests.addAll(otherPendingRequests);
                log.info("Достигнут лимит участников для события {}. Автоматически отклонено {} других заявок.", eventId, otherPendingRequests.size());
            }

            event.setConfirmedRequests(currentConfirmedCount);
        }

        requestRepository.saveAll(confirmedRequests);
        requestRepository.saveAll(rejectedRequests);
        requestRepository.flush();

        eventRepository.save(event);
        eventRepository.flush();

        return new EventRequestStatusUpdateResult(
                toDtoList(confirmedRequests),
                toDtoList(rejectedRequests)
        );
    }

    @Override
    public List<ParticipationRequestDto> getUserRequests(Long userId) {
        log.info("Получение всех заявок на участие для пользователя id={}", userId);
        getUser(userId);
        return toDtoList(requestRepository.findAllByRequesterId(userId));
    }

    @Override
    public List<ParticipationRequestDto> getRequestsByOwner(Long userId, Long eventId) {
        log.info("Владелец id={} получает заявки для своего события id={}", userId, eventId);
        Event event = getEvent(eventId);
        if (!event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("Пользователь " + userId + " не является инициатором события " + eventId);
        }
        return toDtoList(requestRepository.findAllByEventId(eventId));
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        log.info("Пользователь id={} отменяет свой запрос id={}", userId, requestId);
        ParticipationRequest request = requestRepository.findByIdAndRequesterId(requestId, userId)
                .orElseThrow(() -> new NotFoundException("Запрос с id=" + requestId + " от пользователя " + userId + " не найден."));

        if (request.getStatus() == RequestStatus.CONFIRMED) {
            throw new ConflictException("Невозможно отменить уже подтвержденную заявку.");
        }

        request.setStatus(RequestStatus.CANCELED);
        return ParticipationRequestMapper.toParticipationRequestDto(requestRepository.save(request));
    }

    @Override
    public long countEventsInStatus(Long eventId, RequestStatus status) {
        return requestRepository.countByEventAndStatus(eventId, status);
    }

    @Override
    public Map<Long, Long> countConfirmedRequestsForEvents(Set<Long> eventIds) {
        return requestRepository.countConfirmedRequestsForEvents(eventIds);
    }

    private void validateRequestCreation(Long userId, Event event) {
        if (requestRepository.existsByEventIdAndRequesterId(event.getId(), userId)) {
            throw new ConflictException("Запрос от пользователя " + userId + " на событие " + event.getId() + " уже существует.");
        }

        if (event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("Инициатор не может подавать заявку на собственное событие.");
        }

        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Нельзя подать заявку на неопубликованное событие. Текущий статус: " + event.getState());
        }

        if (event.getParticipantLimit() > 0) {
            long confirmedCount = requestRepository.countByEventIdAndStatus(event.getId(), RequestStatus.CONFIRMED);
            if (confirmedCount >= event.getParticipantLimit()) {
                throw new ConflictException("Лимит участников для события " + event.getId() + " был достигнут.");
            }
        }
    }

    private Event getEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено."));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден."));
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