package practicum.service.compilation;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import practicum.client.RequestClient;
import practicum.exception.NotFoundException;
import practicum.mapper.CompilationMapper;
import practicum.model.Compilation;
import practicum.model.Event;
import practicum.model.dto.compilation.CompilationDto;
import practicum.model.dto.compilation.NewCompilationDto;
import practicum.model.dto.compilation.UpdateCompilationRequest;
import practicum.repository.CompilationRepository;
import practicum.repository.EventRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompilationServiceImpl implements CompilationService {

    private final CompilationRepository compilationRepository;
    private final EventRepository eventRepository;
    private final RequestClient requestClient;

    @Override
    public List<CompilationDto> getAllCompilations(Boolean pinned, int from, int size) {
        PageRequest pageRequest = PageRequest.of(from / size, size);

        List<Compilation> compilations = (pinned != null)
                ? compilationRepository.findAllByPinned(pinned, pageRequest).getContent()
                : compilationRepository.findAll(pageRequest).getContent();

        Set<Long> eventIds = compilations.stream()
                .flatMap(c -> c.getEvents().stream())
                .map(Event::getId)
                .collect(Collectors.toSet());

        Map<Long, Long> confirmedCounts = getConfirmedRequestsCounts(eventIds);

        return compilations.stream()
                .map(c -> CompilationMapper.toCompilationDto(c, confirmedCounts))
                .collect(Collectors.toList());
    }

    @Override
    public CompilationDto getCompilationById(Long compId) {
        Compilation compilation = findCompilationOrThrow(compId);

        Set<Long> eventIds = compilation.getEvents().stream()
                .map(Event::getId)
                .collect(Collectors.toSet());

        Map<Long, Long> confirmedCounts = getConfirmedRequestsCounts(eventIds);

        return CompilationMapper.toCompilationDto(compilation, confirmedCounts);
    }

    @Override
    @Transactional
    public CompilationDto createCompilation(NewCompilationDto newDto) {
        Set<Event> events = resolveEventsForNewCompilation(newDto.getEvents());

        Compilation compilation = new Compilation(
                null,
                events,
                newDto.getPinned(),
                newDto.getTitle()
        );

        Compilation saved = compilationRepository.save(compilation);

        Set<Long> savedEventIds = saved.getEvents().stream()
                .map(Event::getId)
                .collect(Collectors.toSet());

        Map<Long, Long> confirmedCounts = getConfirmedRequestsCounts(savedEventIds);

        return CompilationMapper.toCompilationDto(saved, confirmedCounts);
    }

    @Override
    @Transactional
    public void deleteCompilation(Long compId) {
        compilationRepository.deleteById(compId);
    }

    @Override
    @Transactional
    public CompilationDto updateCompilation(Long compId, UpdateCompilationRequest updateRequest) {
        Compilation compilation = findCompilationOrThrow(compId);

        if (updateRequest.getEvents() != null) {
            Set<Event> events = eventRepository.findAllByIdIn(updateRequest.getEvents());
            compilation.setEvents(events);
        }

        if (updateRequest.getPinned() != null) {
            compilation.setPinned(updateRequest.getPinned());
        }

        String newTitle = updateRequest.getTitle();
        if (newTitle != null && !newTitle.isBlank()) {
            compilation.setTitle(newTitle);
        }

        Compilation updated = compilationRepository.save(compilation);

        Set<Long> updatedEventIds = updated.getEvents().stream()
                .map(Event::getId)
                .collect(Collectors.toSet());

        Map<Long, Long> confirmedCounts = getConfirmedRequestsCounts(updatedEventIds);

        return CompilationMapper.toCompilationDto(updated, confirmedCounts);
    }

    private Compilation findCompilationOrThrow(Long compId) {
        return compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Подборка с ID=" + compId + " не найдена."));
    }

    private Set<Event> resolveEventsForNewCompilation(Set<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return new HashSet<>();
        }

        Set<Event> events = new HashSet<>(eventRepository.findAllById(eventIds));

        if (events.size() != eventIds.size()) {
            throw new NotFoundException("Одно или несколько событий из списка не найдены.");
        }

        return events;
    }

    private Map<Long, Long> getConfirmedRequestsCounts(Set<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return requestClient.countConfirmedRequestsForEvents(eventIds);
    }
}