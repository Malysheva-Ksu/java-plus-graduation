import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import practicum.StatsClient;
import practicum.client.RequestClient;
import practicum.client.UserClient;

@SpringBootApplication
@EnableFeignClients(
        clients = {
                StatsClient.class,
                UserClient.class,
                RequestClient.class
        }
)
public class EventServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventServiceApplication.class, args);
    }
}