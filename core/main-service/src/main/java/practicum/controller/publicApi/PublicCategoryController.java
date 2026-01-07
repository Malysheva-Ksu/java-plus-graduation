package practicum.controller.publicApi;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import practicum.dto.category.CategoryDto;
import practicum.service.category.CategoryService;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PublicCategoryController {
    private final CategoryService categoryService;


    @GetMapping("/categories")
    public ResponseEntity<List<CategoryDto>> getAllCategories(
            @RequestParam(defaultValue = "0") @PositiveOrZero int from,
            @RequestParam(defaultValue = "10") @Positive int size) {
        List<CategoryDto> categories = categoryService.getAllCategories(from, size);
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/categories/{catId}")
    public ResponseEntity<CategoryDto> getCategoryById(@PathVariable Long catId) {
        CategoryDto category = categoryService.getCategoryById(catId);
        return ResponseEntity.ok(category);
    }
}