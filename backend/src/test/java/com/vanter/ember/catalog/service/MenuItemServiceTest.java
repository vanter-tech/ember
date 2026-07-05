package com.vanter.ember.catalog.service;

import com.vanter.ember.catalog.model.Category;
import com.vanter.ember.catalog.model.MenuItem;
import com.vanter.ember.catalog.model.dto.MenuItemRequest;
import com.vanter.ember.catalog.model.dto.MenuItemResponse;
import com.vanter.ember.catalog.repository.CategoryRepository;
import com.vanter.ember.catalog.repository.MenuItemRepository;
import com.vanter.ember.config.MinioProperties;
import com.vanter.ember.config.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuItemServiceTest {

    @Mock MenuItemRepository menuItemRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock ImageUploadService imageUploadService;
    @Mock MinioProperties minioProperties;
    @InjectMocks MenuItemService menuItemService;

    private MenuItemRequest burgerRequest() {
        MenuItemRequest req = new MenuItemRequest();
        req.setName("Classic Burger");
        req.setDescription("Beef patty with lettuce");
        req.setPrice(new BigDecimal("9.99"));
        req.setCategoryId(1L);
        return req;
    }

    private Category burgersCategory() {
        return Category.builder().id(1L).name("Burgers").build();
    }

    @Test
    void create_savesItemWithoutImage() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(burgersCategory()));
        when(menuItemRepository.save(any())).thenAnswer(inv -> {
            MenuItem item = inv.getArgument(0);
            item.setId(1L);
            return item;
        });

        MenuItemResponse result = menuItemService.create(burgerRequest(), null);

        assertThat(result.getName()).isEqualTo("Classic Burger");
        assertThat(result.getImageUrl()).isNull();
        verify(imageUploadService, never()).uploadImage(any(), any());
    }

    @Test
    void create_uploadsImageWhenFileProvided() {
        MockMultipartFile image = new MockMultipartFile(
                "image", "photo.jpg", "image/jpeg", new byte[100]);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(burgersCategory()));
        when(minioProperties.getBucket()).thenReturn("ember-media");
        when(imageUploadService.uploadImage(any(), eq("ember-media")))
                .thenReturn("http://localhost:9000/ember-media/uuid.jpg");
        when(menuItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MenuItemResponse result = menuItemService.create(burgerRequest(), image);

        assertThat(result.getImageUrl()).isEqualTo("http://localhost:9000/ember-media/uuid.jpg");
    }

    @Test
    void create_throwsWhenCategoryNotFound() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuItemService.create(burgerRequest(), null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findAll_returnsAllItems() {
        when(menuItemRepository.findAll()).thenReturn(
                List.of(MenuItem.builder().id(1L).name("Burger").build()));

        assertThat(menuItemService.findAll(null)).hasSize(1);
    }

    @Test
    void findById_returnsItem() {
        when(menuItemRepository.findById(1L)).thenReturn(
                Optional.of(MenuItem.builder().id(1L).name("Burger").build()));

        assertThat(menuItemService.findById(1L).getName()).isEqualTo("Burger");
    }

    @Test
    void findById_throwsWhenNotFound() {
        when(menuItemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuItemService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_updatesFieldsWithoutNewImage() {
        MenuItem existing = MenuItem.builder().id(1L).name("Old Name")
                .price(new BigDecimal("5.00")).category(burgersCategory()).build();
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(burgersCategory()));
        when(menuItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MenuItemResponse result = menuItemService.update(1L, burgerRequest());

        assertThat(result.getName()).isEqualTo("Classic Burger");
        assertThat(result.getPrice()).isEqualByComparingTo("9.99");
        verify(imageUploadService, never()).uploadImage(any(), any());
    }

    @Test
    void update_replacesImageWhenNewFileProvided() {
        MenuItem existing = MenuItem.builder().id(1L).name("Burger")
                .price(new BigDecimal("9.99")).category(burgersCategory())
                .imageUrl("http://localhost:9000/ember-media/old.jpg").build();
        MockMultipartFile newImage = new MockMultipartFile(
                "image", "new.jpg", "image/jpeg", new byte[100]);

        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(burgersCategory()));
        when(imageUploadService.uploadImage(any(), eq("ember-media")))
                .thenReturn("http://localhost:9000/ember-media/new.jpg");
        when(menuItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        MenuItemRequest req = burgerRequest();
        req.setImageUrl(newImage);
        MenuItemResponse result = menuItemService.update(1L, req);

        verify(imageUploadService).deleteImage("http://localhost:9000/ember-media/old.jpg");
        assertThat(result.getImageUrl()).isEqualTo("http://localhost:9000/ember-media/new.jpg");
    }

    @Test
    void toggleAvailability_flipsFlag() {
        MenuItem item = MenuItem.builder().id(1L).name("Burger").available(true).build();
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(menuItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(menuItemService.toggleAvailability(1L).isAvailable()).isFalse();
    }

    @Test
    void delete_removesItemAndImage() {
        MenuItem item = MenuItem.builder().id(1L).name("Burger")
                .imageUrl("http://localhost:9000/ember-media/uuid.jpg").build();
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(item));

        menuItemService.delete(1L);

        verify(imageUploadService).deleteImage("http://localhost:9000/ember-media/uuid.jpg");
        verify(menuItemRepository).deleteById(1L);
    }

    @Test
    void delete_skipsImageDeletionWhenNoImage() {
        MenuItem item = MenuItem.builder().id(1L).name("Burger").build();
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(item));

        menuItemService.delete(1L);

        verify(imageUploadService, never()).deleteImage(any());
        verify(menuItemRepository).deleteById(1L);
    }
}
