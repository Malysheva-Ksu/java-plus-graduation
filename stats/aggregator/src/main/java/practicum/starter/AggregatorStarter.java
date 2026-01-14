package practicum.starter;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import practicum.service.AggregatorProcessor;

@Component
@RequiredArgsConstructor
public class AggregatorStarter implements CommandLineRunner {
    private final AggregatorProcessor aggregatorProcessor;

    @Override
    public void run(String[] args) {
        aggregatorProcessor.start();
    }
}