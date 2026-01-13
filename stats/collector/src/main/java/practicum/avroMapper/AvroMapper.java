package practicum.avroMapper;

import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.ewm.stats.proto.ActionTypeProto;
import ru.practicum.ewm.stats.proto.UserActionProto;

import java.time.Instant;

public final class AvroMapper {
    private static final String ACTION_TYPE_PROTO_PREFIX = "ACTION_";

    private AvroMapper() {
    }

    public static UserActionAvro toUserActionAvro(UserActionProto userActionProto) {
        Instant timestamp = Instant.ofEpochSecond(
                userActionProto.getTimestamp().getSeconds(),
                userActionProto.getTimestamp().getNanos()
        );

        return UserActionAvro.newBuilder()
                .setUserId(userActionProto.getUserId())
                .setEventId(userActionProto.getEventId())
                .setActionType(convert(userActionProto.getActionType()))
                .setTimestamp(timestamp)
                .build();
    }

    private static ActionTypeAvro convert(ActionTypeProto actionTypeProto) {
        String actionTypeString = actionTypeProto.name().replace(ACTION_TYPE_PROTO_PREFIX, "");
        return ActionTypeAvro.valueOf(actionTypeString);
    }
}