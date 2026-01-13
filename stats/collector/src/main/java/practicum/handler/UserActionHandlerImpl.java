package practicum.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import practicum.avroMapper.AvroMapper;
import practicum.producer.UserActionProducer;
import ru.practicum.ewm.stats.proto.UserActionProto;

@Service
@RequiredArgsConstructor
public class UserActionHandlerImpl implements UserActionHandler {
    private final UserActionProducer producer;

    @Override
    public void handle(UserActionProto userActionProto) {
        producer.publishUserActivity(AvroMapper.toUserActionAvro(userActionProto));
    }
}