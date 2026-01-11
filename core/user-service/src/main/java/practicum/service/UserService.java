package practicum.service;

import practicum.model.User;
import practicum.model.dto.user.NewUserRequest;
import practicum.model.dto.user.UserDto;

import java.util.List;

public interface UserService {

    List<UserDto> getUsers(List<Long> ids);

    UserDto createUser(NewUserRequest requestDto);

    List<UserDto> getUsers(List<Long> ids, Integer from, Integer size);

    UserDto getUser(Long userId);

    User getUserEntity(Long userId);

    void deleteUser(Long userId);
}