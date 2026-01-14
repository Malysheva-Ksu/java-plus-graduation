package practicum.kafka;

import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.Consumer;

import java.time.Duration;

public interface KafkaClient {
    Consumer<Long, SpecificRecordBase> getConsumerAction();

    Consumer<Long, SpecificRecordBase> getConsumerSimilarity();

    Duration getPollTimeout();

    KafkaTopicsProperties getTopicsProperties();
}