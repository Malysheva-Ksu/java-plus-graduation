package practicum.controller.privateApi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import practicum.model.dto.request.ParticipationRequestDto;
import practicum.service.ParticipationRequestService;

import java.util.Collection;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/users/{userId}/requests")
public class ParticipationRequestController {
    private final ParticipationRequestService requestService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ParticipationRequestDto createRequest(
            @PathVariable Long userId,
            @RequestParam(required = false) Long eventId) {

        log.info("PRIVATE-API: Попытка создания запроса. User: {}, Event: {}", userId, eventId);

        if (eventId == null) {
            log.warn("Ошибка создания запроса: отсутствует eventId");
            throw new IllegalArgumentException("Идентификатор события (eventId) является обязательным.");
        }

        return requestService.createRequest(userId, eventId);
    }

    @GetMapping
    public Collection<ParticipationRequestDto> getUserRequests(
            @PathVariable Long userId
    ) {
        log.info("PRIVATE-API: Получение заявок пользователя ID={}", userId);
        return requestService.getUserRequests(userId);
    }

    @PatchMapping("/{requestId}/cancel")
    public ResponseEntity<ParticipationRequestDto> cancelRequest(
            @PathVariable Long userId,
            @PathVariable Long requestId) {
        log.info("PRIVATE-API: Отмена заявки ID={} пользователем ID={}", requestId, userId);
        ParticipationRequestDto canceledRequest = requestService.cancelRequest(userId, requestId);
        return ResponseEntity.ok(canceledRequest);
    }
}