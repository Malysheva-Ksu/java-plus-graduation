package practicum.service.compilation;

import practicum.dto.compilation.CompilationDto;
import practicum.dto.compilation.NewCompilationDto;
import practicum.dto.compilation.UpdateCompilationRequest;

import java.util.List;

public interface CompilationService {
    CompilationDto createCompilation(NewCompilationDto newCompilationDto);

    void deleteCompilation(Long compId);

    CompilationDto updateCompilation(Long compId, UpdateCompilationRequest updateRequest);

    List<CompilationDto> getAllCompilations(Boolean pinned, int from, int size);

    CompilationDto getCompilationById(Long compId);
}