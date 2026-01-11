package practicum.controller.publicApi;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import practicum.model.dto.comment.CommentDto;
import practicum.service.comment.CommentService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/events/{eventId}/comments")
public class PublicCommentController {

    private final CommentService commentService;

    @GetMapping
    public List<CommentDto> getCommentsByEvent(@PathVariable Long eventId) {
        return commentService.getCommentsByEvent(eventId);
    }
}