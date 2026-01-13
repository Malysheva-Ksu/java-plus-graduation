package practicum.service;

import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import practicum.avroMapper.SimilarityAvroMapper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SimilarityCalculator {
    private final static double VIEW_WEIGHT = 0.4;
    private final static double REGISTER_WEIGHT = 0.8;
    private final static double LIKE_WEIGHT = 1.0;

    private static final Map<ActionTypeAvro, Double> WEIGHTS = Map.of(
            ActionTypeAvro.VIEW, VIEW_WEIGHT,
            ActionTypeAvro.REGISTER, REGISTER_WEIGHT,
            ActionTypeAvro.LIKE, LIKE_WEIGHT
    );

    private final Map<Long, Map<Long, Double>> weightMatrix = new ConcurrentHashMap<>();
    private final Map<Long, Double> weightSumByEvent = new ConcurrentHashMap<>();
    private final Map<Long, Map<Long, Double>> minWeightsSums = new ConcurrentHashMap<>();
    private final Map<Long, Set<Long>> eventsByUser = new ConcurrentHashMap<>();
    private final Map<Long, Object> eventLocks = new ConcurrentHashMap<>();

    private Object obtainLock(long eventId) {
        return eventLocks.computeIfAbsent(eventId, key -> new Object());
    }

    public List<EventSimilarityAvro> calculateSimilarity(UserActionAvro userActionAvro) {
        long eventId = userActionAvro.getEventId();
        long userId = userActionAvro.getUserId();
        double newWeight = WEIGHTS.get(userActionAvro.getActionType());

        synchronized (obtainLock(eventId)) {
            Map<Long, Double> userWeights = weightMatrix.computeIfAbsent(eventId, k -> new ConcurrentHashMap<>());
            double oldWeight = userWeights.getOrDefault(userId, 0.0);

            if (!(newWeight > oldWeight)) {
                return Collections.emptyList();
            }

            userWeights.put(userId, newWeight);

            double totalWeight = weightSumByEvent.getOrDefault(eventId, 0.0);
            weightSumByEvent.put(eventId, totalWeight - oldWeight + newWeight);

            Set<Long> userEvents = eventsByUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet());
            List<EventSimilarityAvro> results = new ArrayList<>();

            for (long otherEventId : userEvents) {
                if (otherEventId == eventId) continue;

                long firstId = (eventId < otherEventId) ? eventId : otherEventId;
                long secondId = (eventId < otherEventId) ? otherEventId : eventId;

                synchronized (obtainLock(firstId)) {
                    synchronized (obtainLock(secondId)) {
                        double similarity = computePairMetrics(eventId, otherEventId, userId, oldWeight, newWeight);
                        results.add(
                                SimilarityAvroMapper.convertToAvro(eventId, otherEventId, similarity)
                        );
                    }
                }
            }

            userEvents.add(eventId);
            return results;
        }
    }

    private double computePairMetrics(long eventA, long eventB, long userId, double oldWeightA, double newWeightA) {
        Map<Long, Double> userWeightsB = weightMatrix.getOrDefault(eventB, Map.of());
        double weightB = userWeightsB.getOrDefault(userId, 0.0);

        if (Double.compare(weightB, 0.0) == 0) {
            double currentSum = fetchMinSum(eventA, eventB);
            double norm = Math.sqrt(weightSumByEvent.getOrDefault(eventA, 0.0)) * Math.sqrt(weightSumByEvent.getOrDefault(eventB, 0.0));
            return norm == 0 ? 0.0 : currentSum / norm;
        }

        double diff = Math.min(newWeightA, weightB) - Math.min(oldWeightA, weightB);
        double updatedSum = fetchMinSum(eventA, eventB) + diff;

        saveMinSum(eventA, eventB, updatedSum);double totalA = weightSumByEvent.getOrDefault(eventA, 0.0);

        double totalB = weightSumByEvent.getOrDefault(eventB, 0.0);

        return (totalA == 0 || totalB == 0) ? 0.0 : updatedSum / (Math.sqrt(totalA) * Math.sqrt(totalB));
    }

    private void saveMinSum(long eventA, long eventB, double sum) {
        long min = (eventA < eventB) ? eventA : eventB;
        long max = (eventA < eventB) ? eventB : eventA;
        minWeightsSums.computeIfAbsent(min, x -> new ConcurrentHashMap<>()).put(max, sum);
    }

    private double fetchMinSum(long eventA, long eventB) {
        long min = (eventA < eventB) ? eventA : eventB;
        long max = (eventA < eventB) ? eventB : eventA;
        return minWeightsSums.getOrDefault(min, Map.of()).getOrDefault(max, 0.0);
    }
}