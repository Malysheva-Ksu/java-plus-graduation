package practicum.controller.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import practicum.model.dto.user.UserDto;
import practicum.service.UserService;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class InternalUserController {
    private final UserService userService;

    @GetMapping("/list")
    public List<UserDto> getUsers(@RequestParam List<Long> ids) {
        log.info("INTERNAL-API: Получение списка пользователей. IDs: {}", ids);
        return userService.getUsers(ids);
    }
}