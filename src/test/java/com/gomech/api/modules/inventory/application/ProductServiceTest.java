package com.gomech.api.modules.inventory.application;

import com.gomech.api.modules.inventory.api.dto.CreateProductRequest;
import com.gomech.api.modules.inventory.api.dto.ProductResponse;
import com.gomech.api.modules.inventory.api.dto.UpdateProductRequest;
import com.gomech.api.modules.inventory.domain.ProductNotFoundException;
import com.gomech.api.modules.inventory.domain.UnitOfMeasure;
import com.gomech.api.modules.inventory.infrastructure.persistence.entity.Product;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.InventoryMovementRepository;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.ProductRepository;
import com.gomech.api.modules.inventory.infrastructure.persistence.repository.UnitStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UnitStockRepository unitStockRepository;

    @Mock
    private InventoryMovementRepository movementRepository;

    private ProductService productService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, unitStockRepository, movementRepository);
    }

    @Test
    @DisplayName("Deve criar produto com sucesso sem estoque inicial")
    void shouldCreateProductWithoutInitialStock() {
        CreateProductRequest request = new CreateProductRequest(
            unitId,
            null,
            "OLEO-5W30",
            "Óleo Sintético 5W30",
            "Lubrificantes",
            "7891234567890",
            "Mobil",
            UnitOfMeasure.L,
            BigDecimal.valueOf(35.00),
            BigDecimal.valueOf(65.00),
            10,
            "Prateleira A-1",
            null,
            null
        );

        when(productRepository.existsByTenantIdAndSkuCodeAndDeletedAtIsNull(tenantId, "OLEO-5W30")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        ProductResponse response = productService.createProduct(request, tenantId, userId);

        assertThat(response).isNotNull();
        assertThat(response.skuCode()).isEqualTo("OLEO-5W30");
        assertThat(response.name()).isEqualTo("Óleo Sintético 5W30");
        assertThat(response.costPrice()).isEqualByComparingTo("35.00");
        assertThat(response.sellingPrice()).isEqualByComparingTo("65.00");
        assertThat(response.active()).isTrue();
    }

    @Test
    @DisplayName("Deve criar produto com saldo inicial e registrar movimentação de estoque")
    void shouldCreateProductWithInitialStock() {
        CreateProductRequest request = new CreateProductRequest(
            unitId,
            null,
            "FILTRO-AR-01",
            "Filtro de Ar Motor",
            "Filtros",
            null,
            "Tecfil",
            UnitOfMeasure.UN,
            BigDecimal.valueOf(20.00),
            BigDecimal.valueOf(45.00),
            5,
            "Prateleira B-2",
            BigDecimal.valueOf(15),
            unitId
        );

        when(productRepository.existsByTenantIdAndSkuCodeAndDeletedAtIsNull(tenantId, "FILTRO-AR-01")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        ProductResponse response = productService.createProduct(request, tenantId, userId);

        assertThat(response).isNotNull();
        verify(unitStockRepository).save(any());
        verify(movementRepository).save(any());
    }

    @Test
    @DisplayName("Deve rejeitar criação de produto com SKU duplicado no tenant")
    void shouldRejectDuplicateSku() {
        CreateProductRequest request = new CreateProductRequest(
            unitId,
            null,
            "SKU-EXISTENTE",
            "Produto Duplicado",
            null,
            null,
            null,
            UnitOfMeasure.UN,
            BigDecimal.TEN,
            BigDecimal.valueOf(20),
            1,
            null,
            null,
            null
        );

        when(productRepository.existsByTenantIdAndSkuCodeAndDeletedAtIsNull(tenantId, "SKU-EXISTENTE")).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(request, tenantId, userId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Já existe um produto com o SKU");
    }

    @Test
    @DisplayName("Deve atualizar produto existente")
    void shouldUpdateProduct() {
        UUID productId = UUID.randomUUID();
        Product existing = Product.builder()
            .id(productId)
            .tenantId(tenantId)
            .skuCode("VELA-01")
            .name("Jogo de Velas Antigo")
            .costPrice(BigDecimal.valueOf(50))
            .sellingPrice(BigDecimal.valueOf(100))
            .unitOfMeasure(UnitOfMeasure.JOGO)
            .build();

        UpdateProductRequest updateReq = new UpdateProductRequest(
            unitId,
            null,
            "VELA-01",
            "Jogo de Velas Iridium NGK",
            "Ignição",
            "7890001112223",
            "NGK",
            UnitOfMeasure.JOGO,
            BigDecimal.valueOf(80),
            BigDecimal.valueOf(160),
            4,
            "Gaveta 3",
            true
        );

        when(productRepository.findByIdAndTenantIdAndDeletedAtIsNull(productId, tenantId)).thenReturn(Optional.of(existing));
        when(productRepository.existsByTenantIdAndSkuCodeAndIdNotAndDeletedAtIsNull(tenantId, "VELA-01", productId)).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = productService.updateProduct(productId, updateReq, tenantId);

        assertThat(response.name()).isEqualTo("Jogo de Velas Iridium NGK");
        assertThat(response.costPrice()).isEqualByComparingTo("80");
        assertThat(response.sellingPrice()).isEqualByComparingTo("160");
    }

    @Test
    @DisplayName("Deve lançar ProductNotFoundException para produto inexistente")
    void shouldThrowWhenProductNotFound() {
        UUID nonExistentId = UUID.randomUUID();
        when(productRepository.findByIdAndTenantIdAndDeletedAtIsNull(nonExistentId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(nonExistentId, tenantId))
            .isInstanceOf(ProductNotFoundException.class);
    }
}
