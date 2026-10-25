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
        verify(productRepository).existsByProductCode("P-01");
        verify(productRepository).save(product);
        verify(inventoryService).initializeForProduct(product);
        verify(lowStockAlertService).synchronizeForProduct(1L);
    }

    @Test
    void createProduct_duplicateCodeThrows() {
        ProductDTO.CreateRequest request = new ProductDTO.CreateRequest("P-01", "Rice", "Food", "bag", null, 5);
        when(productRepository.existsByProductCode("P-01")).thenReturn(true);
        assertThrows(AlreadyExistsException.class, () -> productService.handleCreateProduct(request));
        verify(productRepository, never()).save(any());
        verifyNoInteractions(productMapper, inventoryService, lowStockAlertService);
    }

    @Test
    void createProduct_trimsCodeBeforeCheckingUniqueness() {
        ProductDTO.CreateRequest request = new ProductDTO.CreateRequest(" SKU-A ", "Rice", "Food", "bag", null, 5);
        Product mappedProduct = Product.builder().productCode(" SKU-A ").productName("Rice")
            .category("Food").unit("bag").description(null).minStockThreshold(5).build();
        mappedProduct.setId(11L);
        when(productRepository.existsByProductCode("SKU-A")).thenReturn(false);
        when(productMapper.toEntity(request)).thenReturn(mappedProduct);
        when(productRepository.save(mappedProduct)).thenReturn(mappedProduct);
        when(productMapper.toResponse(mappedProduct)).thenReturn(response);

        productService.handleCreateProduct(request);

        verify(productRepository).existsByProductCode("SKU-A");
        verify(productRepository).save(mappedProduct);
        assertEquals("SKU-A", mappedProduct.getProductCode());
    }

    @Test
    void createProduct_acceptsZeroStockThreshold() {
        ProductDTO.CreateRequest request = new ProductDTO.CreateRequest("P-02", "Rice", "Food", "bag", null, 0);
        Product mappedProduct = Product.builder().productCode("P-02").productName("Rice")
            .category("Food").unit("bag").minStockThreshold(0).build();
        mappedProduct.setId(11L);
        when(productMapper.toEntity(request)).thenReturn(mappedProduct);
        when(productRepository.save(mappedProduct)).thenReturn(mappedProduct);
        when(productMapper.toResponse(mappedProduct)).thenReturn(response);

        productService.handleCreateProduct(request);

        verify(productRepository).save(mappedProduct);
        assertEquals(0, mappedProduct.getMinStockThreshold());
        verify(inventoryService).initializeForProduct(mappedProduct);
        verify(lowStockAlertService).synchronizeForProduct(mappedProduct.getId());
    }

    @Test
    void createProduct_mapsRequestAndReturnsMappedSavedProduct() {
        ProductDTO.CreateRequest request = new ProductDTO.CreateRequest("P-01", "Rice", "Food", "bag", "Rice product", 5);
        Product mappedProduct = Product.builder().productCode("P-01").productName("Rice")
            .category("Food").unit("bag").description("Rice product").minStockThreshold(5).build();
        Product savedProduct = Product.builder().productCode("P-01").productName("Rice")
            .category("Food").unit("bag").description("Rice product").minStockThreshold(5).build();
        savedProduct.setId(12L);
        ProductDTO.Response mappedResponse = new ProductDTO.Response(
            12L, "P-01", "Rice", "Food", "bag", "Rice product", ProductStatusEnum.ACTIVE, 5);
        when(productMapper.toEntity(request)).thenReturn(mappedProduct);
        when(productRepository.save(mappedProduct)).thenReturn(savedProduct);
        when(productMapper.toResponse(savedProduct)).thenReturn(mappedResponse);

        assertEquals(mappedResponse, productService.handleCreateProduct(request));

        verify(productMapper).toEntity(request);
        verify(productRepository).save(mappedProduct);
        verify(productMapper).toResponse(savedProduct);
    }

    @Test
    void createProduct_initializesInventoryAndSynchronizesAlertOnceForSavedProduct() {
        ProductDTO.CreateRequest request = new ProductDTO.CreateRequest("P-02", "Rice", "Food", "bag", null, 5);
        Product savedProduct = Product.builder().productCode("P-02").productName("Rice")
            .category("Food").unit("bag").minStockThreshold(5).build();
        savedProduct.setId(12L);
        when(productMapper.toEntity(request)).thenReturn(savedProduct);
        when(productRepository.save(savedProduct)).thenReturn(savedProduct);
        when(productMapper.toResponse(savedProduct)).thenReturn(response);

        productService.handleCreateProduct(request);

        verify(inventoryService, times(1)).initializeForProduct(savedProduct);
        verify(lowStockAlertService, times(1)).synchronizeForProduct(12L);
    }

    // FUNCTION 6: handleGetProductById
    @Test
    void getProductById_returnsMappedProduct() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productMapper.toResponse(product)).thenReturn(response);

        assertEquals(response, productService.handleGetProductById(1L));

        verify(productRepository).findById(1L);
        verify(productMapper).toResponse(product);
    }

    @Test
    void getProductById_missingProductThrows() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        IdInvalidException exception = assertThrows(
            IdInvalidException.class,
            () -> productService.handleGetProductById(99L)
        );

        assertEquals("Product not found: 99", exception.getMessage());
        verify(productRepository).findById(99L);
        verifyNoInteractions(productMapper);
    }

    @Test
    void getProductById_nullIdIsRejectedByRepository() {
        when(productRepository.findById(null))
            .thenThrow(new IllegalArgumentException("The given id must not be null"));

        assertThrows(IllegalArgumentException.class, () -> productService.handleGetProductById(null));

        verify(productRepository).findById(null);
        verifyNoInteractions(productMapper);
    }

    // FUNCTION 7: handleGetAllProducts
    @Test
    void getAllProducts_trimsKeywordAndCategoryAndUsesActiveStatus() {
        product.setProductName("Gạo");
        product.setCategory("Thực phẩm");
        ProductDTO.Response mappedResponse = new ProductDTO.Response(
            1L, "P-01", "Gạo", "Thực phẩm", "bag", "Rice product", ProductStatusEnum.ACTIVE, 5);
        when(productRepository.searchProducts(eq("Gạo"), eq("Thực phẩm"), eq(ProductStatusEnum.ACTIVE), any()))
            .thenReturn(new PageImpl<>(List.of(product)));
        when(productMapper.toResponse(product)).thenReturn(mappedResponse);

        var result = productService.handleGetAllProducts(1, 20, " Gạo ", " Thực phẩm ", ProductStatusEnum.ACTIVE);

        assertEquals(1, result.getMeta().getTotal());
        assertEquals(mappedResponse, result.getResult().get(0));
        verify(productRepository).searchProducts(eq("Gạo"), eq("Thực phẩm"), eq(ProductStatusEnum.ACTIVE), any());
    }

    @Test
    void getAllProducts_nullKeywordAndBlankCategoryDefaultCorrectly() {
        when(productRepository.searchProducts(eq(""), isNull(), eq(ProductStatusEnum.ACTIVE), any()))
            .thenReturn(new PageImpl<>(List.of(product)));
        when(productMapper.toResponse(product)).thenReturn(response);

        var result = productService.handleGetAllProducts(1, 20, null, "   ", null);

        assertEquals(1, result.getMeta().getTotal());
        assertEquals(response, result.getResult().get(0));
        verify(productRepository).searchProducts(eq(""), isNull(), eq(ProductStatusEnum.ACTIVE), any());
    }

    @Test
    void getAllProducts_blankKeywordAndTrimmedCategoryArePassedToRepository() {
        when(productRepository.searchProducts(eq(""), eq("Thực phẩm"), eq(ProductStatusEnum.ACTIVE), any()))
            .thenReturn(new PageImpl<>(List.of(product)));
        when(productMapper.toResponse(product)).thenReturn(response);

        var result = productService.handleGetAllProducts(1, 20, "   ", " Thực phẩm ", ProductStatusEnum.ACTIVE);

        assertEquals(1, result.getMeta().getTotal());
        verify(productRepository).searchProducts(eq(""), eq("Thực phẩm"), eq(ProductStatusEnum.ACTIVE), any());
    }

    @Test
    void getAllProducts_noKeywordMatchReturnsEmptyPage() {
        when(productRepository.searchProducts(eq("KHONG_TON_TAI"), isNull(), eq(ProductStatusEnum.ACTIVE), any()))
            .thenReturn(new PageImpl<>(List.of()));

        var result = productService.handleGetAllProducts(1, 20, "KHONG_TON_TAI", null, ProductStatusEnum.ACTIVE);

        assertTrue(result.getResult().isEmpty());
        assertEquals(0, result.getMeta().getTotal());
        verify(productRepository).searchProducts(eq("KHONG_TON_TAI"), isNull(), eq(ProductStatusEnum.ACTIVE), any());
        verifyNoInteractions(productMapper);
    }

    @Test
    void getAllProducts_filtersByInactiveStatus() {
        product.setStatus(ProductStatusEnum.INACTIVE);
        ProductDTO.Response inactiveResponse = new ProductDTO.Response(
            1L, "P-01", "Rice", "Food", "bag", "Rice product", ProductStatusEnum.INACTIVE, 5);
        when(productRepository.searchProducts(eq(""), isNull(), eq(ProductStatusEnum.INACTIVE), any()))
            .thenReturn(new PageImpl<>(List.of(product)));
        when(productMapper.toResponse(product)).thenReturn(inactiveResponse);

        var result = productService.handleGetAllProducts(1, 20, null, null, ProductStatusEnum.INACTIVE);

        assertEquals(1, result.getMeta().getTotal());
        assertEquals(ProductStatusEnum.INACTIVE, result.getResult().get(0).status());
        verify(productRepository).searchProducts(eq(""), isNull(), eq(ProductStatusEnum.INACTIVE), any());
    }

    @Test
    void getAllProducts_categoryWithoutMatchReturnsEmptyPage() {
        when(productRepository.searchProducts(eq(""), eq("No such category"), eq(ProductStatusEnum.ACTIVE), any()))
            .thenReturn(new PageImpl<>(List.of()));

        var result = productService.handleGetAllProducts(1, 20, null, "No such category", ProductStatusEnum.ACTIVE);

        assertTrue(result.getResult().isEmpty());
        assertEquals(0, result.getMeta().getTotal());
        verify(productRepository).searchProducts(eq(""), eq("No such category"), eq(ProductStatusEnum.ACTIVE), any());
        verifyNoInteractions(productMapper);
    }

    // FUNCTION 8: handleUpdateProduct
    @Test
    void updateProduct_updatesAndSynchronizesAlert() {
        ProductDTO.UpdateRequest request = new ProductDTO.UpdateRequest(1L, " P-02 ", "Rice 2", "Food", "bag", null, ProductStatusEnum.ACTIVE, 6);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.existsByProductCodeAndIdNot("P-02", 1L)).thenReturn(false);
        when(productMapper.toResponse(product)).thenReturn(response);

        assertEquals(response, productService.handleUpdateProduct(request));

        verify(productRepository).findById(1L);
        verify(productRepository).existsByProductCodeAndIdNot("P-02", 1L);
        verify(productMapper).update(request, product);
        assertEquals("P-02", product.getProductCode());
    }

    @Test
    void updateProduct_trimsCodeBeforeCheckingUniquenessAndUpdating() {
        ProductDTO.UpdateRequest request = new ProductDTO.UpdateRequest(
            1L, " SKU-A ", "Rice updated", "Food", "bag", null, ProductStatusEnum.ACTIVE, 6);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.existsByProductCodeAndIdNot("SKU-A", 1L)).thenReturn(false);

        productService.handleUpdateProduct(request);

        verify(productRepository).existsByProductCodeAndIdNot("SKU-A", 1L);
        verify(productMapper).update(request, product);
        assertEquals("SKU-A", product.getProductCode());
    }

    @Test
    void updateProduct_synchronizesAlertExactlyOnceAfterSuccessfulUpdate() {
        ProductDTO.UpdateRequest request = new ProductDTO.UpdateRequest(
            1L, "P-02", "Rice updated", "Food", "bag", null, ProductStatusEnum.ACTIVE, 6);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.existsByProductCodeAndIdNot("P-02", 1L)).thenReturn(false);

        productService.handleUpdateProduct(request);

        verify(productMapper).update(request, product);
        verify(lowStockAlertService, times(1)).synchronizeForProduct(1L);
    }

    @Test
    void updateProduct_unchangedCodeSkipsDuplicateCheck() {
        ProductDTO.UpdateRequest request = new ProductDTO.UpdateRequest(
            1L, " P-01 ", "Rice updated", "Food", "bag", null, ProductStatusEnum.ACTIVE, 6);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productMapper.toResponse(product)).thenReturn(response);

        productService.handleUpdateProduct(request);

        verify(productRepository).findById(1L);
        verify(productRepository, never()).existsByProductCodeAndIdNot(anyString(), anyLong());
        verify(productMapper).update(request, product);
        assertEquals("P-01", product.getProductCode());
        verify(lowStockAlertService, times(1)).synchronizeForProduct(1L);
    }

    @Test
    void updateProduct_duplicateCodeThrows() {
        ProductDTO.UpdateRequest request = new ProductDTO.UpdateRequest(1L, "P-02", "Rice", "Food", "bag", null, ProductStatusEnum.ACTIVE, 5);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.existsByProductCodeAndIdNot("P-02", 1L)).thenReturn(true);

        assertThrows(AlreadyExistsException.class, () -> productService.handleUpdateProduct(request));

        verify(productRepository).existsByProductCodeAndIdNot("P-02", 1L);
        verify(productMapper, never()).update(any(), any());
        verifyNoInteractions(lowStockAlertService);
    }

    @Test
    void updateProduct_missingProductThrows() {
        ProductDTO.UpdateRequest request = new ProductDTO.UpdateRequest(99L, "P-02", "Rice", "Food", "bag", null, ProductStatusEnum.ACTIVE, 5);
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IdInvalidException.class, () -> productService.handleUpdateProduct(request));

        verify(productRepository).findById(99L);
        verify(productRepository, never()).existsByProductCodeAndIdNot(anyString(), anyLong());
        verifyNoInteractions(productMapper, lowStockAlertService);
    }

    // FUNCTION 9: handleDeleteProduct
    @Test
    void deleteProduct_marksProductInactive() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productService.handleDeleteProduct(1L);

        assertEquals(ProductStatusEnum.INACTIVE, product.getStatus());
        verify(productRepository).findById(1L);
        verify(productRepository, never()).delete(any(Product.class));
        verify(productRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteProduct_keepsAlreadyInactiveProductInactive() {
        product.setStatus(ProductStatusEnum.INACTIVE);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productService.handleDeleteProduct(1L);

        assertEquals(ProductStatusEnum.INACTIVE, product.getStatus());
        verify(productRepository).findById(1L);
        verify(productRepository, never()).delete(any(Product.class));
        verify(productRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteProduct_missingProductThrows() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        IdInvalidException exception = assertThrows(
            IdInvalidException.class,
            () -> productService.handleDeleteProduct(99L)
        );

        assertEquals("Product not found: 99", exception.getMessage());
        verify(productRepository).findById(99L);
        verify(productRepository, never()).delete(any(Product.class));
        verify(productRepository, never()).deleteById(anyLong());
    }
}
