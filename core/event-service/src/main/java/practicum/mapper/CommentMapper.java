package practicum.mapper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import practicum.model.Comment;
import practicum.model.dto.comment.CommentDto;
import practicum.model.dto.comment.NewCommentDto;
import practicum.model.dto.user.UserShortDto;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CommentMapper {

    public static CommentDto toCommentDto(Comment comment, UserShortDto authorDto) {
        if (comment == null) {
            return null;
        }

        return CommentDto.builder()
                .id(comment.getId())
                .text(comment.getText())
                .eventId(comment.getEvent() != null ? comment.getEvent().getId() : null)
                .author(authorDto)
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
}