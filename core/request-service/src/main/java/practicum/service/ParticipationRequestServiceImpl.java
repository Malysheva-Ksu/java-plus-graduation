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
        public EventRequestStatusUpdateResult updateRequests(
                Long userId, Long eventId, EventRequestStatusUpdateRequest updateInfo
        ) {
            log.info("Массовое обновление статусов для события id={}", eventId);
            EventFullDto event = fetchEvent(eventId);

            if (!event.getInitiator().equals(userId)) {
                throw new ConflictException("Право редактирования есть только у создателя события.");
            }if (!event.getRequestModeration() || event.getParticipantLimit() == 0) {
                return new EventRequestStatusUpdateResult(List.of(), List.of());
            }

            List<ParticipationRequest> targetRequests = requestRepository.findAllByIdIn(updateInfo.getRequestIds());

            if (targetRequests.stream().anyMatch(r -> r.getStatus() != RequestStatus.PENDING)) {
                throw new ConflictException("Статус можно менять только у заявок в ожидании (PENDING).");
            }

            List<ParticipationRequest> confirmed = new ArrayList<>();
            List<ParticipationRequest> rejected = new ArrayList<>();

            long limit = event.getParticipantLimit();
            long currentCount = event.getConfirmedRequests() != null ? event.getConfirmedRequests() : 0L;

            if (updateInfo.getStatus() == RequestStatus.REJECTED) {
                targetRequests.forEach(r -> r.setStatus(RequestStatus.REJECTED));
                rejected.addAll(targetRequests);
            } else {
                for (ParticipationRequest req : targetRequests) {
                    if (currentCount < limit) {
                        req.setStatus(RequestStatus.CONFIRMED);
                        confirmed.add(req);
                        currentCount++;
                    } else {
                        req.setStatus(RequestStatus.REJECTED);
                        rejected.add(req);
                    }
                }

                if (currentCount >= limit) {
                    List<ParticipationRequest> restPending = requestRepository.findAllByEventAndStatus(eventId, RequestStatus.PENDING);
                    restPending.forEach(r -> r.setStatus(RequestStatus.REJECTED));
                    rejected.addAll(restPending);
                }
                event.setConfirmedRequests(currentCount);
            }

            requestRepository.saveAll(confirmed);
            requestRepository.saveAll(rejected);
            requestRepository.flush();

            eventClient.updateConfirmedRequests(event.getId(), event.getConfirmedRequests());

            return new EventRequestStatusUpdateResult(convertToDtoList(confirmed), convertToDtoList(rejected));
        }

        private EventFullDto fetchEvent(Long id) {
            return eventClient.getEvent(id)
                    .orElseThrow(() -> new NotFoundException("Мероприятие id=" + id + " не существует."));
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