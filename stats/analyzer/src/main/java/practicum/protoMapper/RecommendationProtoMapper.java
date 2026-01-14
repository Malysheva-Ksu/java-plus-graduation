package practicum.protoMapper;

import practicum.model.EventSimilarity;
import ru.practicum.ewm.stats.proto.RecommendedEventProto;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class RecommendationProtoMapper {

    private RecommendationProtoMapper() {
    }

    public static Stream<RecommendedEventProto> toProtoStream(
            List<EventSimilarity> eventSimilarities,
            long currentEventId
    ) {
        return eventSimilarities.stream()
                .map(similarity -> {
                    long recommended;
                    if (similarity.getEventA() == currentEventId) {
                        recommended = similarity.getEventB();
                    } else {
                        recommended = similarity.getEventA();
                    }

                    return RecommendedEventProto.newBuilder()
                            .setEventId(recommended)
                            .setScore(similarity.getScore())
                            .build();
                });
    }

    public static Stream<RecommendedEventProto> toProtoStream(Map<Long, Double> scoreByEvent) {
        return scoreByEvent.entrySet().stream()
                .map(entry -> RecommendedEventProto.newBuilder()
                        .setEventId(entry.getKey())
                        .setScore(entry.getValue())
                        .build());
    }
}