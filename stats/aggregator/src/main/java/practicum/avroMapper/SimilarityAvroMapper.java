package practicum.avroMapper;

import java.time.Instant;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;

public final class SimilarityAvroMapper {

    private SimilarityAvroMapper() {
    }

    public static EventSimilarityAvro convertToAvro(long idA, long idB, double similarityScore) {
        long minId = (idA < idB) ? idA : idB;
        long maxId = (idA < idB) ? idB : idA;

        return EventSimilarityAvro.newBuilder()
                .setEventA(minId)
                .setEventB(maxId)
                .setScore(similarityScore)
                .setTimestamp(Instant.now())
                .build();
    }
}