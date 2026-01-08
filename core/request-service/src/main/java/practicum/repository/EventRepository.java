package practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import practicum.model.Event;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
}