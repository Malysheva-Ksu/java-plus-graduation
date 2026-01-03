package practicum;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HitDto {
    private Long id;

    @NotBlank
    private String app;

    @NotBlank
    private String uri;
    private String ip;

    @JsonProperty(value = "timestamp")
    private LocalDateTime timeStamp;
}