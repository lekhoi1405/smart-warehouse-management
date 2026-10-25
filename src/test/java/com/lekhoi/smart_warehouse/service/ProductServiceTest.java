package com.lekhoi.smart_warehouse.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import com.lekhoi.smart_warehouse.domain.dto.ProductDTO;
import com.lekhoi.smart_warehouse.domain.entity.Product;
import com.lekhoi.smart_warehouse.repository.ProductRepository;
import com.lekhoi.smart_warehouse.service.mapper.ProductMapper;
import com.lekhoi.smart_warehouse.util.constant.ProductStatusEnum;
import com.lekhoi.smart_warehouse.util.error.ExceptionCustom.AlreadyExistsException;
import com.lekhoi.smart_warehouse.util.error.ExceptionCustom.IdInvalidException;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductMapper productMapper;
    @Mock private InventoryService inventoryService;
    @Mock private LowStockAlertService lowStockAlertService;
    @InjectMocks private ProductService productService;

    private Product product;
    private ProductDTO.Response response;

    @BeforeEach
    void setUp() {
        product = Product.builder()
            .productCode("P-01").productName("Rice").category("Food").unit("bag")
            .description("Rice product").status(ProductStatusEnum.ACTIVE).minStockThreshold(5).build();
        product.setId(1L);
        response = new ProductDTO.Response(1L, "P-01", "Rice", "Food", "bag", "Rice product", ProductStatusEnum.ACTIVE, 5);
    }

    // FUNCTION 5: handleCreateProduct
    @Test
    void createProduct_savesInitializesInventoryAndSynchronizesAlert() {
        ProductDTO.CreateRequest request = new ProductDTO.CreateRequest(" P-01 ", "Rice", "Food", "bag", "Rice product", 5);
        when(productMapper.toEntity(request)).thenReturn(product);
        when(productRepository.save(product)).thenReturn(product);
        when(productMapper.toResponse(product)).thenReturn(response);

        assertEquals(response, productService.handleCreateProduct(request));
        assertEquals("P-01", product.getProductCode());
        assertEquals(ProductStatusEnum.ACTIVE, product.getStatus());
        verify(inventoryService).initializeForProduct(product);
        verify(lowStockAlertService).synchronizeForProduct(1L);
    }

    @Test
    void createProduct_duplicateCodeThrows() {
        ProductDTO.CreateRequest request = new ProductDTO.CreateRequest("P-01", "Rice", "Food", "bag", null, 5);
        when(productRepository.existsByProductCode("P-01")).thenReturn(true);
        assertThrows(AlreadyExistsException.class, () -> productService.handleCreateProduct(request));
        verify(productRepository, never()).save(any());
    }

    // FUNCTION 6: handleGetProductById
    @Test
    void getProductById_returnsMappedProduct() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productMapper.toResponse(product)).thenReturn(response);
        assertEquals(response, productService.handleGetProductById(1L));
    }

    @Test
    void getProductById_missingProductThrows() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IdInvalidException.class, () -> productService.handleGetProductById(99L));
    }

    // FUNCTION 7: handleGetAllProducts
    @Test
    void getAllProducts_rejectsInvalidPagination() {
        assertThrows(IllegalArgumentException.class, () -> productService.handleGetAllProducts(0, 10, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> productService.handleGetAllProducts(1, 0, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> productService.handleGetAllProducts(1, 101, null, null, null));
    }

    @Test
    void getAllProducts_trimsFiltersAndDefaultsStatus() {
        when(productRepository.searchProducts(eq("rice"), eq("Food"), eq(ProductStatusEnum.ACTIVE), any()))
            .thenReturn(new PageImpl<>(List.of(product)));
        when(productMapper.toResponse(product)).thenReturn(response);

        var result = productService.handleGetAllProducts(1, 10, " rice ", " Food ", null);

        assertEquals(1, result.getMeta().getTotal());
        assertEquals(response, result.getResult().get(0));
    }

    // FUNCTION 8: handleUpdateProduct
    @Test
    void updateProduct_updatesAndSynchronizesAlert() {
        ProductDTO.UpdateRequest request = new ProductDTO.UpdateRequest(1L, " P-02 ", "Rice 2", "Food", "bag", null, ProductStatusEnum.ACTIVE, 6);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.existsByProductCodeAndIdNot("P-02", 1L)).thenReturn(false);
        when(productMapper.toResponse(product)).thenReturn(response);

        assertEquals(response, productService.handleUpdateProduct(request));
        verify(productMapper).update(request, product);
        assertEquals("P-02", product.getProductCode());
        verify(lowStockAlertService).synchronizeForProduct(1L);
    }

    @Test
    void updateProduct_duplicateCodeThrows() {
        ProductDTO.UpdateRequest request = new ProductDTO.UpdateRequest(1L, "P-02", "Rice", "Food", "bag", null, ProductStatusEnum.ACTIVE, 5);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.existsByProductCodeAndIdNot("P-02", 1L)).thenReturn(true);
        assertThrows(AlreadyExistsException.class, () -> productService.handleUpdateProduct(request));
        verify(productMapper, never()).update(any(), any());
    }

    @Test
    void updateProduct_missingProductThrows() {
        ProductDTO.UpdateRequest request = new ProductDTO.UpdateRequest(99L, "P-02", "Rice", "Food", "bag", null, ProductStatusEnum.ACTIVE, 5);
        when(productRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IdInvalidException.class, () -> productService.handleUpdateProduct(request));
    }

    // FUNCTION 9: handleDeleteProduct
    @Test
    void deleteProduct_marksProductInactive() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        productService.handleDeleteProduct(1L);
        assertEquals(ProductStatusEnum.INACTIVE, product.getStatus());
    }

    @Test
    void deleteProduct_missingProductThrows() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IdInvalidException.class, () -> productService.handleDeleteProduct(99L));
    }
}
