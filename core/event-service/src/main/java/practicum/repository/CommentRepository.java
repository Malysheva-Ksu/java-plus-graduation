package practicum.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import practicum.model.Comment;

import java.util.Arrays;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findAllByEvent_Id(Long eventId);

    List<Comment> findAllByAuthorId(Long userId);
}