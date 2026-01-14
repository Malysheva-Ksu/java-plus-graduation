package practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.stereotype.Component;
import practicum.kafka.KafkaClient;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class AggregatorProcessor {
    private final Map<TopicPartition, OffsetAndMetadata> currentOffsets = new HashMap<>();

    private final KafkaClient kafkaClient;
    private final SimilarityCalculator calculator;

    public void start() {
        Consumer<Long, SpecificRecordBase> consumer = kafkaClient.getConsumer();
        Producer<Long, SpecificRecordBase> producer = kafkaClient.getProducer();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Closing consumer...");
            consumer.wakeup();
        }));

        try {
            String topicName = kafkaClient.getTopicsProperties().getStatsUserActionV1();
            consumer.subscribe(Collections.singletonList(topicName));

            while (true) {
                ConsumerRecords<Long, SpecificRecordBase> records = consumer.poll(kafkaClient.getPollTimeout());

                if (records.isEmpty()) continue;

                AtomicInteger index = new AtomicInteger(0);
                for (ConsumerRecord<Long, SpecificRecordBase> record : records) {
                    processMessage(record, producer);
                    savePosition(record, index.getAndIncrement(), consumer);
                }

                consumer.commitAsync();
            }
        } catch (WakeupException ignored) {
        } catch (Exception e) {
            log.error("Sensor processing failed due to error", e);
        } finally {
            closeResources(consumer, producer);
        }
    }

    private void savePosition(ConsumerRecord<Long, SpecificRecordBase> record, int count, Consumer<Long, SpecificRecordBase> consumer) {
        currentOffsets.put(
                new TopicPartition(record.topic(), record.partition()),
                new OffsetAndMetadata(record.offset() + 1)
        );

        if (count % 10 == 0) {
            consumer.commitAsync(currentOffsets, (offsets, exception) -> {
                if (Objects.nonNull(exception)) {
                    log.warn("Offset fixing error for: {}", offsets, exception);
                }
            });
        }
    }

    private void processMessage(ConsumerRecord<Long, SpecificRecordBase> record, Producer<Long, SpecificRecordBase> producer) {
        Object value = record.value();

        if (value instanceof UserActionAvro action) {
            List<EventSimilarityAvro> similarities = calculator.calculateSimilarity(action);

            if (similarities.isEmpty()) return;

            for (EventSimilarityAvro similarity : similarities) {
                long timestamp = similarity.getTimestamp().toEpochMilli();

                ProducerRecord<Long, SpecificRecordBase> similarityRecord = new ProducerRecord<>(
                        kafkaClient.getTopicsProperties().getStatsEventsSimilarityV1(),
                        null,
                        timestamp,
                        similarity.getEventA(),
                        similarity
                );

                producer.send(similarityRecord, (metadata, ex) -> {
                    if (Objects.nonNull(ex)) {
                        log.error("Failed to send similarity (EventA={}, EventB={}): {}",
                                similarity.getEventA(), similarity.getEventB(), ex.getMessage(), ex);
                    } else {
                        log.info("Successfully dispatched similarity: partition={}, offset={}",
                                metadata.partition(), metadata.offset());
                    }
                });
            }
        } else {
            log.warn("Unexpected record type detected: {}", value);
        }
    }

    private void closeResources(Consumer<Long, SpecificRecordBase> consumer, Producer<Long, SpecificRecordBase> producer) {
        try {
            producer.flush();
            consumer.commitSync(currentOffsets);
        } catch (Exception e) {
            log.error("Cleanup commit failed", e);
        } finally {
            log.info("Close consumer");
            consumer.close();
            log.info("Close producer");
            producer.close();
        }
    }
}