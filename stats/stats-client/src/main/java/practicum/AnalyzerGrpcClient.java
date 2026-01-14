package practicum;

import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.stats.proto.*;

import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Service
@Slf4j
public class AnalyzerGrpcClient {
    @GrpcClient("analyzer")
    private RecommendationsControllerGrpc.RecommendationsControllerBlockingStub client;

    public Stream<RecommendedEventProto> getRecommendationsForUser(Long userId, Integer maxResults) {
        UserPredictionsRequestProto request = UserPredictionsRequestProto.newBuilder()
                .setUserId(userId)
                .setMaxResult(maxResults)
                .build();

        try {
            Iterator<RecommendedEventProto> iterator = client.getRecommendationsForUser(request);
            return convertIteratorToStream(iterator);
        } catch (Exception e) {
            log.error("Failed to fetch user recommendations: {}", e.getLocalizedMessage());
        }

        return Stream.empty();
    }

    public Stream<RecommendedEventProto> getSimilarEvents(Long eventId, Long userId, Integer maxResults) {
        SimilarEventsRequestProto request = SimilarEventsRequestProto.newBuilder()
                .setEventId(eventId)
                .setUserId(userId)
                .setMaxResult(maxResults)
                .build();

        try {
            Iterator<RecommendedEventProto> iterator = client.getSimilarEvents(request);
            return convertIteratorToStream(iterator);
        } catch (Exception e) {
            log.error("Failed to fetch similar events for event {}: {}", eventId, e.getLocalizedMessage());
        }

        return Stream.empty();
    }

    public Stream<RecommendedEventProto> getInteractionsCount(List<Long> eventIds) {
        InteractionsCountRequestProto request = InteractionsCountRequestProto.newBuilder()
                .addAllEventId(eventIds)
                .build();

        try {
            Iterator<RecommendedEventProto> iterator = client.getInteractionsCount(request);
            return convertIteratorToStream(iterator);
        } catch (Exception e) {
            log.error("Failed to fetch interaction counts: {}", e.getLocalizedMessage());
        }

        return Stream.empty();
    }

    private Stream<RecommendedEventProto> convertIteratorToStream(Iterator<RecommendedEventProto> iterator) {
        if (iterator == null) {
            return Stream.empty();
        }

        Spliterator<RecommendedEventProto> spliterator = Spliterators.spliteratorUnknownSize(
                iterator,
                Spliterator.ORDERED
        );

        return StreamSupport.stream(spliterator, false);
    }
}