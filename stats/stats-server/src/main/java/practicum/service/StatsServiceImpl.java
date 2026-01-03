package practicum.service;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import practicum.HitDto;
import practicum.ViewStatsDto;
import practicum.mapper.HitMapper;
import practicum.model.Hit;
import practicum.repository.StatsRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class StatsServiceImpl implements StatsService {

    private static final Logger log = LoggerFactory.getLogger(StatsServiceImpl.class);

    private final StatsRepository statsRepository;
    private final CircuitBreakerFactory circuitBreakerFactory;

    public StatsServiceImpl(StatsRepository statsRepository,
                            CircuitBreakerFactory circuitBreakerFactory) {
        this.statsRepository = statsRepository;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    @Override
    @Transactional
    public HitDto create(HitDto hitDto) {

        Hit hit = HitMapper.toHit(hitDto);

        CircuitBreaker circuitBreaker = circuitBreakerFactory.create("statsDatabase");

        Hit savedHit = circuitBreaker.run(() -> {

            return statsRepository.save(hit);

        }, throwable -> {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Внутренняя ошибка сервиса: не удалось сохранить запись в базу данных.",
                    throwable
            );
        });

        return HitMapper.toHitDto(savedHit);
    }

    @Override
    public List<ViewStatsDto> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, boolean unique) {

        if (start.isAfter(end)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Дата начала не может быть позже даты окончания."
            );
        }

        CircuitBreaker circuitBreaker = circuitBreakerFactory.create("statsDatabase");
        boolean isUriFilterActive = uris != null && !uris.isEmpty();

        List<ViewStatsDto> stats = circuitBreaker.run(() -> {

            if (unique) {
                return isUriFilterActive ?
                        statsRepository.getStatsUniqueIpForUris(start, end, uris) :
                        statsRepository.getStatsUniqueIp(start, end);
            } else {
                return isUriFilterActive ?
                        statsRepository.getStatsAllForUris(start, end, uris) :
                        statsRepository.getStatsAll(start, end);
            }

        }, throwable -> {
                    throwable.getMessage();
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Сервис временно недоступен: невозможно получить данные статистики из БД.",
                    throwable
            );
        });

        log.info("Статистика успешно получена. Найдено записей: {}", stats.size());
        return stats;
    }
}