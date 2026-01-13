package practicum.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import practicum.model.EventSimilarity;
import practicum.repository.EventSimilarityRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EventSimilarityService {
    private final EventSimilarityRepository eventSimilarityRepository;

    @Transactional
    public void save(EventSimilarity eventSimilarity) {
        Optional<EventSimilarity> similarity =
                eventSimilarityRepository.findByEventAAndEventB(
                        eventSimilarity.getEventA(),
                        eventSimilarity.getEventB()
                );

        similarity.ifPresentOrElse(
                oldSimilarity -> {
                    if (Double.compare(oldSimilarity.getScore(), eventSimilarity.getScore()) != 0) {
                        oldSimilarity.setScore(eventSimilarity.getScore());
                    }
                    eventSimilarityRepository.save(oldSimilarity);
                },
                () -> {
                    eventSimilarityRepository.save(eventSimilarity);
                }
        );
    }
}