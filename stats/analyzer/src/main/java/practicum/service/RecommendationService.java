package practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import practicum.model.EventSimilarity;
import practicum.model.UserAction;
import practicum.protoMapper.RecommendationProtoMapper;
import practicum.repository.EventSimilarityRepository;
import practicum.repository.UserActionRepository;
import ru.practicum.ewm.stats.proto.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class RecommendationService {
    private final UserActionRepository userActionRepository;
    private final EventSimilarityRepository eventSimilarityRepository;

    public Stream<RecommendedEventProto> getRecommendationsForUser(UserPredictionsRequestProto request) {
        long userId = request.getUserId();

        List<Long> userEventIds = userActionRepository.findByUserId(userId, PageRequest.of(
                0,
                request.getMaxResult(),
                Sort.by(Sort.Direction.DESC, "timestamp")
        ));

        if (userEventIds.isEmpty()) {
            return Stream.empty();
        }

        PageRequest pageRequest = PageRequest.of(
                0, request.getMaxResult(),
                Sort.by(Sort.Direction.DESC, "score")
        );

        List<EventSimilarity> similarities = eventSimilarityRepository.findNewSimilar(userEventIds, pageRequest);
        Map<Long, Double> predictedScores = calculateUserPredictions(similarities, userEventIds);

        return RecommendationProtoMapper.toProtoStream(predictedScores);
    }

    public Stream<RecommendedEventProto> getSimilarEvents(SimilarEventsRequestProto request) {
        long eventId = request.getEventId();
        List<EventSimilarity> similarities = eventSimilarityRepository.findAllByEventId(eventId);
        Set<Long> userEventIds = userActionRepository.findByUserIdExcludeEventId(request.getUserId(), eventId);

        List<EventSimilarity> filtered = similarities.stream()
                .filter(s -> !userEventIds.contains(s.getEventA()) && !userEventIds.contains(s.getEventB()))
                .sorted((o1, o2) -> Double.compare(o2.getScore(), o1.getScore()))
                .limit(request.getMaxResult())
                .collect(Collectors.toList());

        return RecommendationProtoMapper.toProtoStream(filtered, eventId);
    }

    public Stream<RecommendedEventProto> getInteractionsCount(InteractionsCountRequestProto request) {
        Map<Long, Double> sumOfWeightsByEvent = new HashMap<>();
        Set<Long> filterIds = new HashSet<>(request.getEventIdList());
        List<UserAction> userActions = userActionRepository.findByEventIdIn(filterIds);

        for (UserAction action : userActions) {
            long currentId = action.getEventId();
            sumOfWeightsByEvent.compute(currentId, (id, currentSum) ->
                    (currentSum == null) ? action.getWeight() : currentSum + action.getWeight());
        }

        return RecommendationProtoMapper.toProtoStream(sumOfWeightsByEvent);
    }

    private Map<Long, Double> calculateUserPredictions(List<EventSimilarity> similarities, List<Long> userEvents) {
        Map<Long, Double> scoreByEvent = new LinkedHashMap<>();

        for (EventSimilarity s : similarities) {
            long candidate = userEvents.contains(s.getEventA()) ? s.getEventB() : s.getEventA();

            PageRequest pageRequest = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "score"));
            List<EventSimilarity> neighbours = eventSimilarityRepository.findNeighbours(candidate, pageRequest);

            Set<Long> neighboursIds = neighbours.stream()
                    .map(n -> Objects.equals(n.getEventA(), candidate) ? n.getEventB() : n.getEventA())
                    .collect(Collectors.toSet());

            List<UserAction> actions = userActionRepository.findByEventIdIn(neighboursIds);
            double score = estimateEventWeight(neighbours, actions, candidate);

            scoreByEvent.put(candidate, score);
        }

        return scoreByEvent;
    }

    private double estimateEventWeight(
            List<EventSimilarity> neighbours,
            List<UserAction> userActions,
            long candidateId
    ) {
        Map<Long, Double> weightMap = new HashMap<>();
        for (UserAction ua : userActions) {
            weightMap.put(ua.getEventId(), ua.getWeight());
        }

        double totalWeightedScore = 0.0;
        double totalSimilarity = 0.0;

        for (EventSimilarity entry : neighbours) {
            long neighbourId = (entry.getEventA() == candidateId) ? entry.getEventB() : entry.getEventA();

            double currentWeight = weightMap.getOrDefault(neighbourId, 0.0);
            double currentSim = entry.getScore();

            totalWeightedScore += (currentWeight * currentSim);
            totalSimilarity += currentSim;
        }

        return (totalSimilarity == 0) ? 0.0 : totalWeightedScore / totalSimilarity;
    }
}