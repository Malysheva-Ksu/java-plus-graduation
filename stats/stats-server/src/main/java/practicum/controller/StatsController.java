package practicum.controller;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import practicum.HitDto;
import practicum.ViewStatsDto;
import practicum.service.StatsService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
public class StatsController {

    private static final Logger log = LoggerFactory.getLogger(StatsController.class);

    private final StatsService statsService;
    private final CircuitBreakerFactory circuitBreakerFactory;

    public StatsController(StatsService statsService,
                           CircuitBreakerFactory circuitBreakerFactory) {
        this.statsService = statsService;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    @PostMapping("/hit")
    @Transactional
    public ResponseEntity<HitDto> createHit(@RequestBody @Valid HitDto hitDto) {

        CircuitBreaker circuitBreaker = circuitBreakerFactory.create("statsService");

        HitDto createdHit = circuitBreaker.run(() -> {

            return statsService.create(hitDto);

        }, throwable -> {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Сервис статистики временно недоступен при записи данных.",
                    throwable
            );
        });

        return ResponseEntity.status(HttpStatus.CREATED).body(createdHit);
    }

    @GetMapping("/stats")
    public ResponseEntity<List<ViewStatsDto>> getStats(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end,
            @RequestParam(required = false) List<String> uris,
            @RequestParam(defaultValue = "false") Boolean unique) {


        CircuitBreaker circuitBreaker = circuitBreakerFactory.create("statsService");

        List<ViewStatsDto> stats = circuitBreaker.run(() -> {

            return statsService.getStats(start, end, uris, unique);

        }, throwable -> {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Не удалось получить статистику из-за сбоя внешнего сервиса.",
                    throwable
            );
        });

        return ResponseEntity.ok(stats);
    }
}