package practicum.service;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public StatsServiceImpl(StatsRepository statsRepository) {
        this.statsRepository = statsRepository;
    }

    @Override
    @Transactional
    public HitDto create(HitDto hitDto) {

        Hit hit = HitMapper.toHit(hitDto);

        Hit savedHit =  statsRepository.save(hit);

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

        boolean isUriFilterActive = uris != null && !uris.isEmpty();

        List<ViewStatsDto> stats;

            if (unique) {
                stats = isUriFilterActive ?
                        statsRepository.getStatsUniqueIpForUris(start, end, uris) :
                        statsRepository.getStatsUniqueIp(start, end);
            } else {
                stats = isUriFilterActive ?
                        statsRepository.getStatsAllForUris(start, end, uris) :
                        statsRepository.getStatsAll(start, end);
            }

        return stats;
    }
}