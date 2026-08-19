package com.gomech.api.modules.crm.api;

import com.gomech.api.core.api.PageResponse;
import com.gomech.api.modules.crm.api.dto.CreateCustomerRequest;
import com.gomech.api.modules.crm.api.dto.CustomerResponse;
import com.gomech.api.modules.crm.api.dto.CustomerSummaryResponse;
import com.gomech.api.modules.crm.api.dto.UpdateCustomerRequest;
import com.gomech.api.modules.crm.application.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@Tag(name = "CRM Clientes", description = "Gestão de clientes, histórico e veículos associados")
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class CustomerController {

    private final CustomerService customerService;

    @Operation(summary = "Cadastrar novo cliente", description = "Cria um novo cliente no escopo do tenant com validação de CPF/CNPJ.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cliente cadastrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados da requisição inválidos"),
            @ApiResponse(responseCode = "409", description = "Documento já cadastrado para outro cliente ativo nesta oficina"),
            @ApiResponse(responseCode = "422", description = "CPF ou CNPJ com formato/dígito verificador inválido")
    })
    @PreAuthorize("hasAuthority('CRM_CUSTOMER_WRITE') or hasRole('Proprietário')")
    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse response = customerService.createCustomer(request, null);
        return ResponseEntity.created(URI.create("/api/v1/customers/" + response.id())).body(response);
    }

    @Operation(summary = "Buscar e filtrar clientes de forma paginada", description = "Retorna lista paginada de clientes com suporte a busca textual livre ('q') ou filtros pontuais.")
    @ApiResponse(responseCode = "200", description = "Página de clientes retornada com sucesso")
    @PreAuthorize("hasAuthority('CRM_CUSTOMER_READ') or hasRole('Proprietário')")
    @GetMapping
    public ResponseEntity<PageResponse<CustomerSummaryResponse>> searchCustomers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String document,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        if (size > 100) {
            throw new IllegalArgumentException("O tamanho da página (size) não pode exceder 100 elementos");
        }
        Pageable pageable = createPageable(page, size, sort);
        Page<CustomerSummaryResponse> result = customerService.searchCustomers(q, name, document, phone, email, pageable, null);
        return ResponseEntity.ok(PageResponse.from(result));
    }

    @Operation(summary = "Obter detalhes do cliente", description = "Retorna os detalhes completos do cliente, incluindo todos os veículos vinculados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cliente encontrado"),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado ou pertencente a outra oficina")
    })
    @PreAuthorize("hasAuthority('CRM_CUSTOMER_READ') or hasRole('Proprietário')")
    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getCustomerById(@PathVariable UUID id) {
        return ResponseEntity.ok(customerService.getCustomerById(id, null));
    }

    @Operation(summary = "Atualizar dados do cliente", description = "Atualiza nome, documento, contato ou endereço do cliente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cliente atualizado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado"),
            @ApiResponse(responseCode = "409", description = "Documento já em uso por outro cliente ativo"),
            @ApiResponse(responseCode = "422", description = "CPF ou CNPJ com formato inválido")
    })
    @PreAuthorize("hasAuthority('CRM_CUSTOMER_WRITE') or hasRole('Proprietário')")
    @PutMapping("/{id}")
    public ResponseEntity<CustomerResponse> updateCustomer(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCustomerRequest request
    ) {
        return ResponseEntity.ok(customerService.updateCustomer(id, request, null));
    }

    @Operation(summary = "Desativar cliente (Soft delete)", description = "Marca o cliente e todos os seus veículos associados como desativados.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cliente desativado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado")
    })
    @PreAuthorize("hasAuthority('CRM_CUSTOMER_DELETE') or hasRole('Proprietário')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable UUID id) {
        customerService.deleteCustomer(id, null);
        return ResponseEntity.noContent().build();
    }

    private Pageable createPageable(int page, int size, String sort) {
        if (sort == null || sort.isBlank()) {
            return PageRequest.of(page, size, Sort.by("createdAt").descending());
        }
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        Sort.Direction direction = (parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc"))
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(direction, property));
    }
}
