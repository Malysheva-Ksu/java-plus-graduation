package practicum.model.dto.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import practicum.model.dto.category.CategoryDto;
import practicum.model.dto.user.UserShortDto;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventShortDto {
    private Long id;
    private String annotation;
    private CategoryDto category;

    @JsonFormat(
            shape = JsonFormat.Shape.STRING,
            pattern = "yyyy-MM-dd HH:mm:ss"
    )
    private LocalDateTime eventDate;
    private Long initiatorId;
    private UserShortDto initiator;
    private Boolean paid;
    private String title;
    private Long views;
    private Long confirmedRequests;
    private Long participantLimit;
    private Double rating;
}