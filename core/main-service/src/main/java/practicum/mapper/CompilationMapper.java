package practicum.mapper;

import practicum.dto.compilation.CompilationDto;
import practicum.dto.event.EventShortDto;
import practicum.model.Compilation;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CompilationMapper {
    public static CompilationDto toCompilationDto(Compilation compilation, Map<Long, Long> confirmedRequestsCounts) {
        if (compilation == null) {
            return null;
        }
        Set<EventShortDto> eventShortDtos = Collections.emptySet();
        if (compilation.getEvents() != null && !compilation.getEvents().isEmpty()) {
            eventShortDtos = compilation.getEvents().stream()
                    .map(event -> EventMapper.toEventShortDto(event))
                    .collect(Collectors.toSet());
        }

        return new CompilationDto(
                compilation.getId(),
                compilation.getPinned(),
                compilation.getTitle(),
                eventShortDtos
        );
    }
}