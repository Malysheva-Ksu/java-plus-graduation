package practicum.producer;

import lombok.RequiredArgsConstructor;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import practicum.kafka.KafkaTopicsProperties;

@Component
@RequiredArgsConstructor
public class UserActionProducer {
    private final Producer<Long, SpecificRecordBase> producer;
    private final KafkaTopicsProperties kafkaTopics;

    public void publishUserActivity(SpecificRecordBase userAction) {
        UserActionAvro avroAction = (UserActionAvro) userAction;

        Long userId = avroAction.getUserId();
        long timestamp = avroAction.getTimestamp().toEpochMilli();
        String targetTopic = kafkaTopics.getStatsUserActionV1();

        ProducerRecord<Long, SpecificRecordBase> record = new ProducerRecord<>(
                targetTopic,
                null,
                timestamp,
                userId,
                avroAction
        );

        producer.send(record);
    }
}