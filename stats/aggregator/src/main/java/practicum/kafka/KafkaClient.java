package practicum.kafka;

import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;

import java.time.Duration;

public interface KafkaClient {
    Consumer<Long, SpecificRecordBase> getConsumer();

    Producer<Long, SpecificRecordBase> getProducer();

    Duration getPollTimeout();

    KafkaTopicsProperties getTopicsProperties();
}