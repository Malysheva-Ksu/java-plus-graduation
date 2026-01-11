package practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import practicum.exception.ConflictException;
import practicum.exception.NotFoundException;
import practicum.mapper.UserMapper;
import practicum.model.User;
import practicum.model.dto.user.NewUserRequest;
import practicum.model.dto.user.UserDto;
import practicum.repository.UserRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public List<UserDto> getUsers(List<Long> ids) {
        log.info("Получение списка пользователей по IDs: {}", ids);
        List<User> users = userRepository.findAllById(ids);
        log.debug("Найдено {} пользователей", users.size());
        return toUserDtoList(users);
    }

    @Override
    @Transactional
    public UserDto createUser(NewUserRequest newUserRequest) {
        log.info("Создание пользователя с email: {}", newUserRequest.getEmail());

        if (userRepository.existsByEmail(newUserRequest.getEmail())) {
            log.warn("Email '{}' уже занят", newUserRequest.getEmail());
            throw new ConflictException("Email '" + newUserRequest.getEmail() + "' уже существует.");
        }

        User user = UserMapper.toUser(newUserRequest);
        User savedUser = userRepository.save(user);

        log.info("Пользователь с ID={} успешно создан", savedUser.getId());
        return UserMapper.toUserDto(savedUser);
    }

    @Override
    public List<UserDto> getUsers(List<Long> ids, Integer from, Integer size) {
        Pageable pageable = PageRequest.of(from / size, size);
        List<User> users;

        if (ids == null || ids.isEmpty()) {
            log.info("Получение списка всех пользователей. Страница: {}, размер: {}", from / size, size);
            users = userRepository.findAll(pageable).getContent();
        } else {
            log.info("Получение списка пользователей по IDs: {}. Страница: {}, размер: {}", ids, from / size, size);
            users = userRepository.findAllByIdIn(ids, pageable);
        }

        log.debug("Найдено {} пользователей", users.size());
        return toUserDtoList(users);
    }

    @Override
    public UserDto getUser(Long userId) {
        User user = getUserEntity(userId);
        return UserMapper.toUserDto(user);
    }

    @Override
    public User getUserEntity(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с ID=" + userId + " не найден."));
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        log.info("Удаление пользователя с ID={}", userId);
        userRepository.deleteById(userId);
        log.info("Пользователь с ID={} успешно удален", userId);
    }

    private List<UserDto> toUserDtoList(List<User> users) {
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        return users.stream()
                .map(UserMapper::toUserDto)
                .collect(Collectors.toList());
    }
}