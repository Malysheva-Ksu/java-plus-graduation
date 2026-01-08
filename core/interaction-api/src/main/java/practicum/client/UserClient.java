package practicum.client;

import feign.FeignException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import practicum.model.dto.user.UserDto;

import java.util.List;

@FeignClient(
        name = "user-service",
        path = "/api/v1/users"
)
public interface UserClient {

    @GetMapping("/list")
    List<UserDto> getUsers(@RequestParam("ids") List<Long> userIds) throws FeignException;
}