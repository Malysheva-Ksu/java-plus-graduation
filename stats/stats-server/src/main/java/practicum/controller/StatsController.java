package practicum.controller;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import practicum.HitDto;
import practicum.ViewStatsDto;
import practicum.service.StatsService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
public class StatsController {

    private static final Logger log = LoggerFactory.getLogger(StatsController.class);

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @PostMapping("/hit")
    @Transactional
    public ResponseEntity<HitDto> createHit(@RequestBody @Valid HitDto hitDto) {

        HitDto createdHit =  statsService.create(hitDto);

        return ResponseEntity.status(HttpStatus.CREATED).body(createdHit);
    }

    @GetMapping("/stats")
    public ResponseEntity<List<ViewStatsDto>> getStats(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end,
            @RequestParam(required = false) List<String> uris,
            @RequestParam(defaultValue = "false") Boolean unique) {

        List<ViewStatsDto> stats = statsService.getStats(start, end, uris, unique);

        return ResponseEntity.ok(stats);
    }
}