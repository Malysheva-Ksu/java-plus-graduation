package practicum.mapper;

import practicum.dto.category.CategoryDto;
import practicum.dto.category.NewCategoryDto;
import practicum.model.Category;

public final class CategoryMapper {

    private CategoryMapper() {

    }


    public static Category toCategory(NewCategoryDto newCategoryDto) {
        return new Category(null, newCategoryDto.getName());
    }

    public static Category toCategory(CategoryDto categoryDto) {
        return new Category(categoryDto.getId(), categoryDto.getName());
    }

    public static CategoryDto toCategoryDto(Category category) {
        return new CategoryDto(category.getId(), category.getName());
    }
}