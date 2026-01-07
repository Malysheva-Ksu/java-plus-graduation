package practicum.service.user;

import practicum.dto.user.NewUserRequest;
import practicum.dto.user.UserDto;
import practicum.model.User;

import java.util.List;

public interface UserService {

    UserDto createUser(NewUserRequest requestDto);

    List<UserDto> getUsers(List<Long> ids, Integer from, Integer size);

    UserDto getUser(Long userId);

    User getUserEntity(Long userId);

    void deleteUser(Long userId);
}