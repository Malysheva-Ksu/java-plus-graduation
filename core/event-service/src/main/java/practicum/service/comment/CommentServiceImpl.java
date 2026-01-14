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
import practicum.model.dto.comment.CommentDto;
import practicum.model.dto.comment.NewCommentDto;
import practicum.model.dto.user.UserDto;
import practicum.model.dto.user.UserShortDto;
import practicum.repository.CommentRepository;
import practicum.repository.EventRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
        UserDto authorDto = fetchUserDto(userId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        Comment comment = CommentMapper.toComment(newCommentDto);
        comment.setAuthorId(userId);
        comment.setEvent(event);
        comment.setCreatedOn(LocalDateTime.now());

        Comment savedComment = commentRepository.save(comment);

        UserShortDto shortDto = new UserShortDto(authorDto.getId(), authorDto.getName());
        return CommentMapper.toCommentDto(savedComment, shortDto);
    }

    @Override
    public List<CommentDto> getCommentsByEvent(Long eventId) {
        List<Comment> comments = commentRepository.findAllByEvent_Id(eventId);
        return enrichCommentsWithAuthors(comments);
    }

    @Override
    public List<CommentDto> getCommentsByUser(Long userId) {
        UserDto authorDto = fetchUserDto(userId);
        UserShortDto shortDto = new UserShortDto(authorDto.getId(), authorDto.getName());

        return commentRepository.findAllByAuthorId(userId).stream()
                .map(c -> CommentMapper.toCommentDto(c, shortDto))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Комментарий с id=" + commentId + " не найден"));

        if (!comment.getAuthorId().equals(userId)) {
            throw new ValidationException("Пользователь с id=" + userId + " не является автором комментария");
        }
        commentRepository.delete(comment);
    }

    private List<CommentDto> enrichCommentsWithAuthors(List<Comment> comments) {
        List<Long> authorIds = comments.stream()
                .map(Comment::getAuthorId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, UserShortDto> authors = userClient.getUsers(authorIds).stream()
                .collect(Collectors.toMap(
                        UserDto::getId,
                        dto -> new UserShortDto(dto.getId(), dto.getName())
                ));

        return comments.stream()
                .map(c -> CommentMapper.toCommentDto(c, authors.get(c.getAuthorId())))
                .collect(Collectors.toList());
    }

    private UserDto fetchUserDto(Long userId) {
        return userClient.getUsers(List.of(userId)).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден"));
    }
}