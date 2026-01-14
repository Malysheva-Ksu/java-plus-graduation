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
import practicum.handler.UserActionHandler;
import practicum.kafka.KafkaClient;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserActionProcessor implements Runnable {
    private final Map<TopicPartition, OffsetAndMetadata> currentOffsets = new HashMap<>();

    private final KafkaClient kafkaClient;
    private final UserActionHandler userActionHandler;

    @Override
    public void run() {
        final Consumer<Long, SpecificRecordBase> consumer = kafkaClient.getConsumerAction();

        Runtime.getRuntime().addShutdownHook(new Thread(consumer::wakeup));

        try {
            String topicToSubscribe = kafkaClient.getTopicsProperties().getStatsUserActionV1();
            consumer.subscribe(Collections.singletonList(topicToSubscribe));

            while (!Thread.currentThread().isInterrupted()) {
                ConsumerRecords<Long, SpecificRecordBase> records = consumer.poll(kafkaClient.getPollTimeout());

                if (records.isEmpty()) {
                    continue;
                }

                AtomicInteger index = new AtomicInteger(0);
                for (ConsumerRecord<Long, SpecificRecordBase> record : records) {
                    processMessage(record);
                    registerOffset(record, index.getAndIncrement(), consumer);
                }

                consumer.commitAsync();
            }
        } catch (WakeupException ignored) {
        } catch (Exception e) {
            log.error("Sensor event processing encountered a critical error", e);
        } finally {
            finalizeSession(consumer);
        }
    }

    private void registerOffset(
            ConsumerRecord<Long, SpecificRecordBase> record,
            int count,
            Consumer<Long, SpecificRecordBase> consumer
    ) {
        TopicPartition partition = new TopicPartition(record.topic(), record.partition());
        OffsetAndMetadata nextOffset = new OffsetAndMetadata(record.offset() + 1);
        currentOffsets.put(partition, nextOffset);

        if (count % 10 == 0) {
            consumer.commitAsync(currentOffsets, (offsets, ex) -> {
                if (ex != null) {
                    log.warn("Failed to commit offsets asynchronously: {}", offsets, ex);
                }
            });
        }
    }

    private void processMessage(ConsumerRecord<Long, SpecificRecordBase> record) {
        if (log.isDebugEnabled()) {
            String valueType = Optional.ofNullable(record.value())
                    .map(v -> v.getClass().getName())
                    .orElse("null");

            log.debug("New Kafka message: topic={}, partition={}, offset={}, valClass={}",
                    record.topic(), record.partition(), record.offset(), valueType);
        }

        if (record.value() instanceof UserActionAvro userActionAvro) {
            log.info("Processing user activity: User={}, Event={}, Type={}",
                    userActionAvro.getUserId(),
                    userActionAvro.getEventId(),
                    userActionAvro.getActionType());

            userActionHandler.handle(userActionAvro);
        }
    }

    private void finalizeSession(Consumer<Long, SpecificRecordBase> consumer) {
        try {
            consumer.commitSync(currentOffsets);
        } catch (Exception e) {
            log.error("Could not sync offsets during shutdown", e);
        } finally {
            log.info("Close consumer");
            consumer.close();
        }
    }
}