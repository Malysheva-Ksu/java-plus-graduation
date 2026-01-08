package practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import practicum.model.Location;

import java.math.BigDecimal;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    Optional<Location> findByLatAndLon(BigDecimal lat, BigDecimal lon);
}