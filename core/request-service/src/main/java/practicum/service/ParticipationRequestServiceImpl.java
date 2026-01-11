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

        private final UserClient userClient;
        private final EventClient eventClient;
        private final ParticipationRequestRepository requestRepository;

        @Override
        public List<ParticipationRequestDto> getUserRequests(Long userId) {
            log.info("Запрос списка всех заявок пользователя с id={}", userId);
            validateUserExists(userId);
            return convertToDtoList(requestRepository.findAllByRequester(userId));
        }

        @Override
        @Transactional
        public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
            log.info("Отмена заявки id={} пользователем id={}", requestId, userId);

            ParticipationRequest request = requestRepository.findByIdAndRequester(requestId, userId)
                    .orElseThrow(() -> new NotFoundException(
                            String.format("Заявка с id=%d не принадлежит пользователю id=%d", requestId, userId)));

            if (RequestStatus.CONFIRMED.equals(request.getStatus())) {
                throw new ConflictException("Подтвержденную заявку нельзя аннулировать.");
            }

            request.setStatus(RequestStatus.CANCELED);
            return ParticipationRequestMapper.toParticipationRequestDto(requestRepository.save(request));
        }

        @Override
        @Transactional
        public ParticipationRequestDto createRequest(Long userId, Long eventId) {
            log.info("Создание заявки: пользователь={} -> событие={}", userId, eventId);

            EventFullDto event = fetchEvent(eventId);

            if (event.getInitiator().equals(userId)) {
                throw new ConflictException("Владелец не может участвовать в собственном мероприятии.");
            }

            if (event.getState() != EventState.PUBLISHED) {
                throw new ConflictException("Регистрация возможна только на опубликованные события.");
            }

            if (requestRepository.existsByEventAndRequester(eventId, userId)) {
                throw new ConflictException("Дубликат заявки запрещен.");
            }

            if (event.getParticipantLimit() > 0) {
                long alreadyConfirmed = requestRepository.countByEventAndStatus(eventId, RequestStatus.CONFIRMED);
                if (alreadyConfirmed >= event.getParticipantLimit()) {
                    throw new ConflictException("Свободные места на событие закончились.");
                }
            }

            ParticipationRequest newRequest = ParticipationRequest.builder()
                    .created(LocalDateTime.now())
                    .requester(userId)
                    .event(event.getId())
                    .build();

            boolean autoConfirm = !event.getRequestModeration() || event.getParticipantLimit() == 0;
            newRequest.setStatus(autoConfirm ? RequestStatus.CONFIRMED : RequestStatus.PENDING);

            ParticipationRequest saved = requestRepository.save(newRequest);
            log.info("Заявка сохранена с ID={} и статусом {}", saved.getId(), saved.getStatus());
            return ParticipationRequestMapper.toParticipationRequestDto(saved);
        }

        @Override
        public List<ParticipationRequestDto> getRequestsByOwner(Long userId, Long eventId) {
            log.info("Просмотр заявок владельцем id={} для события id={}", userId, eventId);
            EventFullDto event = fetchEvent(eventId);

            if (!event.getInitiator().equals(userId)) {
                throw new ConflictException("Доступ запрещен: пользователь не является организатором.");
            }

            return convertToDtoList(requestRepository.findAllByEvent(eventId));
        }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateRequests(Long userId, Long eventId, EventRequestStatusUpdateRequest statusUpdateRequest) {
        EventFullDto eventFullDto = eventClient.getEvent(eventId);

        if (!eventFullDto.getInitiator().equals(userId)) {
            throw new ConflictException("Только инициатор события может обновлять статусы заявок.");
        }

        if (!eventFullDto.getRequestModeration() || eventFullDto.getParticipantLimit() == 0) {
            return new EventRequestStatusUpdateResult(List.of(), List.of());
        }

        List<ParticipationRequest> requestsToUpdate = requestRepository.findAllByIdIn(statusUpdateRequest.getRequestIds());

        if (requestsToUpdate.stream().anyMatch(req -> req.getStatus() != RequestStatus.PENDING)) {
            throw new ConflictException("Можно изменять только заявки в статусе PENDING.");
        }

        List<ParticipationRequest> confirmedRequests = new ArrayList<>();
        List<ParticipationRequest> rejectedRequests = new ArrayList<>();

        long currentConfirmedCount = eventFullDto.getConfirmedRequests();
        long limit = eventFullDto.getParticipantLimit();

        if (statusUpdateRequest.getStatus() == RequestStatus.REJECTED) {
            requestsToUpdate.forEach(req -> req.setStatus(RequestStatus.REJECTED));
            rejectedRequests.addAll(requestsToUpdate);
        } else {
            if (currentConfirmedCount >= limit && limit != 0) {
                throw new ConflictException("Лимит участников уже достигнут.");
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
        }

        requestRepository.saveAll(requestsToUpdate);

        if (currentConfirmedCount >= limit && limit != 0) {
            List<ParticipationRequest> otherPending = requestRepository.findAllByEventAndStatus(eventId, RequestStatus.PENDING);
            otherPending.forEach(req -> req.setStatus(RequestStatus.REJECTED));
            requestRepository.saveAll(otherPending);
            rejectedRequests.addAll(otherPending);
        }

        eventClient.updateConfirmedRequests(eventId, currentConfirmedCount);

        return new EventRequestStatusUpdateResult(
                convertToDtoList(confirmedRequests),
                convertToDtoList(rejectedRequests)
        );
    }

    private EventFullDto fetchEvent(Long id) {
        EventFullDto event = eventClient.getEvent(id);
        if (event == null) {
            throw new NotFoundException("Мероприятие id=" + id + " не существует.");
        }
        return event;
    }

        private void validateUserExists(Long id) {
            List<UserDto> users = userClient.getUsers(List.of(id));
            if (users.isEmpty()) {
                throw new NotFoundException("Пользователь id=" + id + " не найден.");
            }
        }

        private List<ParticipationRequestDto> convertToDtoList(List<ParticipationRequest> source) {
            return (source == null) ? List.of() : source.stream()
                    .map(ParticipationRequestMapper::toParticipationRequestDto)
                    .collect(Collectors.toList());
        }

        @Override
        public long countEventsInStatus(Long eventId, RequestStatus status) {
            return requestRepository.countByEventAndStatus(eventId, status);
        }

        @Override
        public Map<Long, Long> countConfirmedRequestsForEvents(Set<Long> eventIds) {
            return requestRepository.countConfirmedRequestsForEvents(eventIds);
        }
    }