package practicum.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.stereotype.Component;
import practicum.handler.SimilarityHandler;
import practicum.kafka.KafkaClient;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class SimilarityProcessor {
    private final Map<TopicPartition, OffsetAndMetadata> currentOffsets = new HashMap<>();

    private final KafkaClient kafkaClient;
    private final SimilarityHandler similarityHandler;

    public void start() {
        final Consumer<Long, SpecificRecordBase> consumer = kafkaClient.getConsumerSimilarity();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Interrupting consumer...");
            consumer.wakeup();
        }));

        try {
            String topic = kafkaClient.getTopicsProperties().getStatsEventsSimilarityV1();
            consumer.subscribe(Collections.singletonList(topic));

            while (true) {
                ConsumerRecords<Long, SpecificRecordBase> records = consumer.poll(kafkaClient.getPollTimeout());

                if (!records.isEmpty()) {
                    AtomicInteger index = new AtomicInteger(0);
                    for (ConsumerRecord<Long, SpecificRecordBase> record : records) {
                        processEntry(record);
                        trackOffsets(record, index.getAndIncrement(), consumer);
                    }

                    consumer.commitAsync();
                }
            }
        } catch (WakeupException ignored) {
        } catch (Exception e) {
            log.error("Failed to process similarity events", e);
        } finally {
            closeGracefully(consumer);
        }
    }

    private void trackOffsets(
            ConsumerRecord<Long, SpecificRecordBase> record,
            int count,
            Consumer<Long, SpecificRecordBase> consumer
    ) {
        currentOffsets.put(
                new TopicPartition(record.topic(), record.partition()),
                new OffsetAndMetadata(record.offset() + 1)
        );

        if (count % 10 == 0) {
            consumer.commitAsync(currentOffsets, (offsets, exception) -> {
                if (Objects.nonNull(exception)) {
                    log.warn("Async commit failed for offsets: {}", offsets, exception);
                }
            });
        }
    }

    private void processEntry(ConsumerRecord<Long, SpecificRecordBase> record) {
        if (record.value() instanceof EventSimilarityAvro eventSimilarityAvro) {
            log.info(
                    "Processing similarity: {} <-> {}, score: {}",
                    eventSimilarityAvro.getEventA(),
                    eventSimilarityAvro.getEventB(),
                    eventSimilarityAvro.getScore()
            );
            similarityHandler.handle(eventSimilarityAvro);
        }
    }

    private void closeGracefully(Consumer<Long, SpecificRecordBase> consumer) {
        try {
            consumer.commitSync(currentOffsets);
        } catch (Exception e) {
            log.error("Final offset commit failed", e);
        } finally {
            log.info("Close consumer");
            consumer.close();
        }
    }
}