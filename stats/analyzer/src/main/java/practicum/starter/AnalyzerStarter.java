package practicum.starter;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import practicum.processor.SimilarityProcessor;
import practicum.processor.UserActionProcessor;

@Component
@RequiredArgsConstructor
public class AnalyzerStarter implements CommandLineRunner {
    private final UserActionProcessor userActionProcessor;
    private final SimilarityProcessor similarityProcessor;

    @Override
    public void run(String[] args) {
        Thread userActionThread = new Thread(userActionProcessor);

        userActionThread.setName("UserActionHandlerThread");
        userActionThread.start();

        similarityProcessor.start();
    }
}