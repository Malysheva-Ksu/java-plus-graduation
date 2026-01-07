package practicum.controller.publicApi;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import practicum.dto.comment.CommentDto;
import practicum.service.comment.CommentService;

import java.util.List;

@RestController
@RequestMapping("/events/{eventId}/comments")
@RequiredArgsConstructor
public class PublicCommentController {

    private final CommentService commentService;

    @GetMapping
    public List<CommentDto> getCommentsByEvent(@PathVariable Long eventId) {
        return commentService.getCommentsByEvent(eventId);
    }
}