package practicum.model.dto.location;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationDto {
    @JsonIgnore
    private Long id;

    @NotNull
    @DecimalMin(value = "-90", inclusive = true, message = "Latitude must be at least -90.0")
    @DecimalMax(value = "90", inclusive = true, message = "Latitude must be at most 90.0")
    private BigDecimal lat;

    @NotNull
    @DecimalMin(value = "-180", inclusive = true, message = "Longitude must be at least -180.0")
    @DecimalMax(value = "180", inclusive = true, message = "Longitude must be at most 180.0")
    private BigDecimal lon;
}