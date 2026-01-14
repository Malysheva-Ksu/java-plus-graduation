package practicum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import practicum.client.RequestClient;
import practicum.client.UserClient;

import java.util.stream.Stream;

@SpringBootApplication
@EnableFeignClients(clients = {
        UserClient.class,
        RequestClient.class
})
public class EventServiceApplication {
    public static void main(String[] args) {
        var context = SpringApplication.run(EventServiceApplication.class, args);
        System.out.println("СПИСОК ENTITY:");
        Stream.of(context.getBeanDefinitionNames())
                .filter(name -> name.toLowerCase().contains("repository"))
                .forEach(System.out::println);
    }
}