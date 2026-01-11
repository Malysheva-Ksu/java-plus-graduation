package practicum.service.comment;

import practicum.model.dto.comment.CommentDto;
import practicum.model.dto.comment.NewCommentDto;

import java.util.List;

public interface CommentService {
    CommentDto addComment(Long userId, Long eventId, NewCommentDto newCommentDto);

    List<CommentDto> getCommentsByEvent(Long eventId);

    List<CommentDto> getCommentsByUser(Long userId);

    void deleteComment(Long userId, Long commentId);
}