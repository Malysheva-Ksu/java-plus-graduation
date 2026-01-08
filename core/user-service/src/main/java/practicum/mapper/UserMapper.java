package practicum.mapper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import practicum.model.User;
import practicum.model.dto.user.NewUserRequest;
import practicum.model.dto.user.UserDto;
import practicum.model.dto.user.UserShortDto;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserMapper {

    public static User toUser(NewUserRequest requestDto) {
        if (requestDto == null) {
            return null;
        }

        return User.builder()
                .name(requestDto.getName())
                .email(requestDto.getEmail())
                .build();
    }

    public static UserDto toUserDto(User user) {
        if (user == null) {
            return null;
        }

        return new UserDto(user.getId(), user.getEmail(), user.getName());
    }

    public static UserShortDto toShortDto(User user) {
        if (user == null) {
            return null;
        }

        return new UserShortDto(user.getId(), user.getName());
    }
}