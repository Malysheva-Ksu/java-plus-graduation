package practicum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import practicum.client.EventClient;
import practicum.client.UserClient;

@SpringBootApplication
@EnableFeignClients(
        clients = {
                UserClient.class,
                EventClient.class
        }
)
public class RequestServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RequestServiceApplication.class, args);
    }
}