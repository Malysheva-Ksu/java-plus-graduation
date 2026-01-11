package practicum.controller.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import practicum.model.dto.compilation.CompilationDto;
import practicum.model.dto.compilation.NewCompilationDto;
import practicum.model.dto.compilation.UpdateCompilationRequest;
import practicum.service.compilation.CompilationService;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/admin/compilations")
public class AdminCompilationController {

    private final CompilationService compilationService;

    @PostMapping
    public ResponseEntity<CompilationDto> createCompilation(@Valid @RequestBody NewCompilationDto newCompilationDto) {
        CompilationDto createdCompilation = compilationService.createCompilation(newCompilationDto);
        return new ResponseEntity<>(createdCompilation, HttpStatus.CREATED);
    }

    @DeleteMapping("/{compId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCompilation(@PathVariable Long compId) {
        compilationService.deleteCompilation(compId);
    }

    @PatchMapping("/{compId}")
    public ResponseEntity<CompilationDto> updateCompilation(@PathVariable Long compId,
                                                            @Valid @RequestBody UpdateCompilationRequest updateCompilationRequest) {
        CompilationDto updatedCompilation = compilationService.updateCompilation(compId, updateCompilationRequest);
        return ResponseEntity.ok(updatedCompilation);
    }
}