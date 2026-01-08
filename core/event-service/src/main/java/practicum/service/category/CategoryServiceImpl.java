package practicum.service.category;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import practicum.exception.ConflictException;
import practicum.exception.NotFoundException;
import practicum.mapper.CategoryMapper;
import practicum.model.Category;
import practicum.model.dto.category.CategoryDto;
import practicum.model.dto.category.NewCategoryDto;
import practicum.repository.CategoryRepository;
import practicum.repository.EventRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final EventRepository eventRepository;

    @Override
    @Transactional
    public CategoryDto createCategory(NewCategoryDto newCategoryDto) {
        log.info("Создание категории: {}", newCategoryDto.getName());

        String categoryName = newCategoryDto.getName();

        if (categoryRepository.existsByName(categoryName)) {
            log.warn("Категория с именем '{}' уже существует", categoryName);
            throw new ConflictException("Имя категории '" + categoryName + "' уже занято.");
        }

        Category category = CategoryMapper.toCategory(newCategoryDto);
        Category savedCategory = categoryRepository.save(category);

        log.info("Категория с ID={} успешно создана", savedCategory.getId());
        return CategoryMapper.toCategoryDto(savedCategory);
    }

    @Override
    @Transactional
    public void deleteCategory(Long catId) {
        log.info("Удаление категории с ID={}", catId);

        if (eventRepository.existsByCategoryId(catId)) {
            log.warn("Попытка удалить категорию ID={} с привязанными событиями", catId);
            throw new ConflictException("Нельзя удалить категорию, с которой связаны события.");
        }

        categoryRepository.deleteById(catId);
        log.info("Категория с ID={} успешно удалена", catId);
    }

    @Override
    @Transactional
    public CategoryDto updateCategory(Long catId, NewCategoryDto categoryDto) {
        log.info("Обновление категории с ID={}", catId);

        Category categoryToUpdate = categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Категория с ID=" + catId + " не найдена."));

        String newName = categoryDto.getName();

        if (newName.equals(categoryToUpdate.getName())) {
            log.debug("Новое имя совпадает со старым, обновление не требуется");
            return CategoryMapper.toCategoryDto(categoryToUpdate);
        }

        if (categoryRepository.existsByName(newName)) {
            log.warn("Имя категории '{}' уже существует", newName);
            throw new ConflictException("Имя категории '" + newName + "' уже занято.");
        }

        categoryToUpdate.setName(newName);
        Category savedCategory = categoryRepository.save(categoryToUpdate);

        log.info("Категория с ID={} успешно обновлена", savedCategory.getId());
        return CategoryMapper.toCategoryDto(savedCategory);
    }

    @Override
    public List<CategoryDto> getAllCategories(int from, int size) {
        log.info("Получение всех категорий. Страница: {}, размер: {}", from / size, size);
        PageRequest page = PageRequest.of(from / size, size);
        return categoryRepository.findAll(page).stream()
                .map(CategoryMapper::toCategoryDto)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryDto getCategoryById(Long catId) {
        log.info("Получение категории с ID={}", catId);
        Category category = categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Категория с ID=" + catId + " не найдена."));
        return CategoryMapper.toCategoryDto(category);
    }
}