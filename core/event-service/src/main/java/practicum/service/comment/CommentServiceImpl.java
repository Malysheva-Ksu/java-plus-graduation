package practicum.service.comment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import practicum.client.UserClient;
import practicum.exception.NotFoundException;
import practicum.exception.ValidationException;
import practicum.mapper.CommentMapper;
import practicum.model.Comment;
import practicum.model.Event;
import practicum.model.User;
import practicum.model.dto.comment.CommentDto;
import practicum.model.dto.comment.NewCommentDto;
import practicum.model.dto.user.UserDto;
import practicum.repository.CommentRepository;
import practicum.repository.EventRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final UserClient userClient;
    private final EventRepository eventRepository;

    @Override
    @Transactional
    public CommentDto addComment(Long userId, Long eventId, NewCommentDto newCommentDto) {
        User author = loadUserEntity(userId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        Comment comment = CommentMapper.toComment(newCommentDto);
        comment.setAuthor(author);
        comment.setEvent(event);
        comment.setCreatedOn(LocalDateTime.now());

        return CommentMapper.toCommentDto(commentRepository.save(comment));
    }

    @Override
    public List<CommentDto> getCommentsByEvent(Long eventId) {
        return commentRepository.findAllByEvent_Id(eventId).stream()
                .map(CommentMapper::toCommentDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<CommentDto> getCommentsByUser(Long userId) {
        return commentRepository.findAllByAuthor_Id(userId).stream()
                .map(CommentMapper::toCommentDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        Comment comment = commentRepository.getReferenceById(commentId);

        if (!comment.getAuthor().getId().equals(userId)) {
            throw new ValidationException("Пользователь с id=" + userId + " не является автором комментария с id=" + commentId);
        }
        commentRepository.delete(comment);
    }

    private User loadUserEntity(Long userId) {
        UserDto dto = userClient.getUsers(List.of(userId)).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));

        return User.builder()
                .id(dto.getId())
                .email(dto.getEmail())
                .name(dto.getName())
                .build();
    }
}