package practicum.mapper;

import practicum.model.Comment;
import practicum.model.User;
import practicum.model.dto.comment.CommentDto;
import practicum.model.dto.comment.NewCommentDto;
import practicum.model.dto.user.UserShortDto;

public final class CommentMapper {

    private CommentMapper() {
    }

    public static CommentDto toCommentDto(Comment comment) {
        if (comment == null) {
            return null;
        }

        return CommentDto.builder()
                .id(comment.getId())
                .text(comment.getText())
                .eventId(comment.getEvent().getId())
                .author(toUserShortDto(comment.getAuthor()))
                .createdOn(comment.getCreatedOn())
                .build();
    }

    public static Comment toComment(NewCommentDto newCommentDto) {
        if (newCommentDto == null) {
            return null;
        }

        return Comment.builder()
                .text(newCommentDto.getText())
                .build();
    }

    private static UserShortDto toUserShortDto(User user) {
        if (user == null) {
            return null;
        }
        return new UserShortDto(user.getId(), user.getName());
    }
}