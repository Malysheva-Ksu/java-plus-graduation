package practicum.kafka;

import lombok.RequiredArgsConstructor;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import practicum.kafka.serializer.GeneralAvroSerializer;
import practicum.kafka.serializer.UserActionDeserializer;

import java.time.Duration;
import java.util.Objects;
import java.util.Properties;

@Configuration
@RequiredArgsConstructor
public class KafkaConfig {
    @Value("${kafka.bootstrap.servers}")
    private String bootstrapServers;

    @Value("${kafka.consumer.group}")
    private String group;

    @Value("${kafka.consumer.poll.timeout}")
    private long pollTimeout;
    private final KafkaTopicsProperties topicsProperties;

    @Bean
    @Scope("prototype")
    KafkaClient getClient() {
        return new KafkaClient() {
            private Consumer<Long, SpecificRecordBase> consumer;
            private Producer<Long, SpecificRecordBase> producer;

            @Override
            public Consumer<Long, SpecificRecordBase> getConsumer() {
                return Objects.isNull(consumer) ? kafkaConsumer() : consumer;
            }

            @Override
            public Producer<Long, SpecificRecordBase> getProducer() {
                return Objects.isNull(producer) ? kafkaProducer() : producer;
            }

            @Override
            public Duration getPollTimeout() {
                return Duration.ofMillis(pollTimeout);
            }

            @Override
            public KafkaTopicsProperties getTopicsProperties() {
                return topicsProperties;
            }
        };
    }

    private Producer<Long, SpecificRecordBase> kafkaProducer() {
        Properties config = new Properties();

        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, LongSerializer.class.getCanonicalName());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, GeneralAvroSerializer.class.getCanonicalName());

        return new KafkaProducer<>(config);
    }

    private KafkaConsumer<Long, SpecificRecordBase> kafkaConsumer() {
        Properties config = new Properties();

        config.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, LongDeserializer.class.getCanonicalName());
        config.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, UserActionDeserializer.class.getCanonicalName());
        config.setProperty(ConsumerConfig.GROUP_ID_CONFIG, group);

        return new KafkaConsumer<>(config);
    }
}