package practicum.controller;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import practicum.service.RecommendationService;
import ru.practicum.ewm.stats.proto.*;

import java.util.stream.Stream;

@GrpcService
@RequiredArgsConstructor
public class RecommendationsController extends RecommendationsControllerGrpc.RecommendationsControllerImplBase {
    private final RecommendationService recommendationService;

    @Override
    public void getRecommendationsForUser(
            UserPredictionsRequestProto request,
            StreamObserver<RecommendedEventProto> responseObserver
    ) {
        pipeResultsToClient(recommendationService.getRecommendationsForUser(request), responseObserver);
    }

    @Override
    public void getSimilarEvents(
            SimilarEventsRequestProto request,
            StreamObserver<RecommendedEventProto> responseObserver
    ) {
        pipeResultsToClient(recommendationService.getSimilarEvents(request), responseObserver);
    }

    @Override
    public void getInteractionsCount(
            InteractionsCountRequestProto request,
            StreamObserver<RecommendedEventProto> responseObserver
    ) {
        pipeResultsToClient(recommendationService.getInteractionsCount(request), responseObserver);
    }

    private void pipeResultsToClient(
            Stream<RecommendedEventProto> stream,
            StreamObserver<RecommendedEventProto> responseObserver
    ) {
        try {
            stream.forEach(item -> responseObserver.onNext(item));

            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
    }
}