package practicum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import practicum.client.RequestClient;
import practicum.client.UserClient;

@SpringBootApplication
@EntityScan(basePackages = "practicum")
@EnableJpaRepositories(basePackages = "practicum")
@EnableFeignClients(clients = {
        UserClient.class,
        RequestClient.class
})
public class EventServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(EventServiceApplication.class, args);
    }
}