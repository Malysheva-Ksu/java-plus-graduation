package practicum;

import com.google.protobuf.Timestamp;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.stats.proto.ActionTypeProto;
import ru.practicum.ewm.stats.proto.UserActionControllerGrpc;
import ru.practicum.ewm.stats.proto.UserActionProto;

import java.time.Instant;

@Service
@Slf4j
public class CollectorGrpcClient {
    @GrpcClient("collector")
    private UserActionControllerGrpc.UserActionControllerBlockingStub client;

    public void sendUserActivity(Long userId, Long eventId, ActionType type) {
        Instant now = Instant.now();

        Timestamp ts = Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();

        ActionTypeProto actionTypeProto = mapToProtoType(type);

        UserActionProto userActionProto = UserActionProto.newBuilder()
                .setUserId(userId)
                .setEventId(eventId)
                .setActionType(actionTypeProto)
                .setTimestamp(ts)
                .build();

        try {
            client.collectUserAction(userActionProto);
            log.debug("Successfully sent action for user {} on event {}", userId, eventId);
        } catch (Exception e) {
            log.error("Failed to transmit user action to collector: {}", e.getLocalizedMessage());
        }
    }

    private ActionTypeProto mapToProtoType(ActionType type) {
        return switch (type) {
            case ACTION_LIKE -> ActionTypeProto.ACTION_LIKE;
            case ACTION_VIEW -> ActionTypeProto.ACTION_VIEW;
            case ACTION_REGISTER -> ActionTypeProto.ACTION_REGISTER;
        };
    }

}