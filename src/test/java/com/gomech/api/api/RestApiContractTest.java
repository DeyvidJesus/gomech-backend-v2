package com.gomech.api.api;

import com.gomech.api.core.api.PageResponse;
import com.gomech.api.core.exceptions.GlobalExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reference contract test for ADR-004: REST API Conventions.
 *
 * <p>The controller below is a sample implementation of the conventions, not a production
 * endpoint. It exists so the baseline wire contract every module must follow is pinned by
 * an executable test rather than by prose alone.
 */
class RestApiContractTest {

    private final SampleResourceController controller = new SampleResourceController();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void resources_are_exposed_under_the_api_v1_base_path() throws Exception {
        mockMvc.perform(get("/api/v1/sample-resources"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void collection_responses_use_the_standard_pagination_envelope() throws Exception {
        mockMvc.perform(get("/api/v1/sample-resources").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.sort").value("createdAt,desc"));
    }

    @Test
    void unsupported_sort_field_is_rejected_instead_of_ignored() throws Exception {
        mockMvc.perform(get("/api/v1/sample-resources").param("sort", "passwordHash,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.type").value("https://gomech.com/docs/errors/bad-request"))
                .andExpect(jsonPath("$.detail").value("Unsupported sort field: passwordHash"));
    }

    @Test
    void creation_returns_201_with_a_location_header() throws Exception {
        mockMvc.perform(post("/api/v1/sample-resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Maria Silva","email":"maria@email.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/sample-resources/")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Maria Silva"));
    }

    @Test
    void validation_failures_return_rfc7807_problem_details_with_invalid_params_array() throws Exception {
        mockMvc.perform(post("/api/v1/sample-resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","email":"not-an-email"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.type").value("https://gomech.com/docs/errors/validation-failed"))
                .andExpect(jsonPath("$.detail").value("Input validation failed for some parameters."))
                .andExpect(jsonPath("$.invalidParams").isArray())
                .andExpect(jsonPath("$.invalidParams[*].name").exists())
                .andExpect(jsonPath("$.invalidParams[*].reason").exists());
    }

    @Test
    void replayed_idempotency_key_returns_the_original_response_without_repeating_the_side_effect() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = """
                {"name":"Maria Silva","email":"maria@email.com"}
                """;

        String first = mockMvc.perform(post("/api/v1/sample-resources")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String replay = mockMvc.perform(post("/api/v1/sample-resources")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(first, replay);
        org.junit.jupiter.api.Assertions.assertEquals(1, controller.sideEffectCount());
    }

    @Test
    void reused_idempotency_key_with_a_different_payload_is_rejected_with_conflict() throws Exception {
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/sample-resources")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Maria Silva","email":"maria@email.com"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/sample-resources")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Outro Cliente","email":"outro@email.com"}
                                """))
                .andExpect(status().isConflict());
    }

    record CreateSampleResourceRequest(
            @NotBlank String name,
            @NotBlank @Email String email
    ) {}

    record SampleResourceResponse(UUID id, String name, String email) {}

    @RestController
    @RequestMapping("/api/v1/sample-resources")
    static final class SampleResourceController {

        private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "name", "id");
        private static final int MAX_PAGE_SIZE = 100;

        private final Map<String, IdempotencyRecord> idempotencyStore = new HashMap<>();
        private final AtomicInteger sideEffects = new AtomicInteger();

        int sideEffectCount() {
            return sideEffects.get();
        }

        @GetMapping
        PageResponse<SampleResourceResponse> list(
                @RequestParam(defaultValue = "0") int page,
                @RequestParam(defaultValue = "20") int size,
                @RequestParam(defaultValue = "createdAt,desc") String sort,
                @RequestParam(required = false) String status
        ) {
            if (page < 0) {
                throw new IllegalArgumentException("Page must be greater than or equal to 0");
            }
            if (size < 1 || size > MAX_PAGE_SIZE) {
                throw new IllegalArgumentException("Size must be between 1 and " + MAX_PAGE_SIZE);
            }

            String[] sortParts = sort.split(",");
            String sortField = sortParts[0];
            if (!SORTABLE_FIELDS.contains(sortField)) {
                throw new IllegalArgumentException("Unsupported sort field: " + sortField);
            }
            Sort.Direction direction = sortParts.length > 1 && "desc".equalsIgnoreCase(sortParts[1])
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;

            List<SampleResourceResponse> rows = List.of(
                    new SampleResourceResponse(UUID.randomUUID(), "Maria Silva", "maria@email.com")
            );
            PageRequest pageRequest = PageRequest.of(page, size, Sort.by(direction, sortField));

            return PageResponse.from(new PageImpl<>(rows, pageRequest, rows.size()));
        }

        @PostMapping
        ResponseEntity<SampleResourceResponse> create(
                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                @Valid @RequestBody CreateSampleResourceRequest request
        ) {
            if (idempotencyKey != null) {
                IdempotencyRecord recorded = idempotencyStore.get(idempotencyKey);
                if (recorded != null) {
                    if (!recorded.fingerprint().equals(request)) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).build();
                    }
                    return created(recorded.response());
                }
            }

            sideEffects.incrementAndGet();
            SampleResourceResponse response = new SampleResourceResponse(
                    UUID.randomUUID(), request.name(), request.email()
            );

            if (idempotencyKey != null) {
                idempotencyStore.put(idempotencyKey, new IdempotencyRecord(request, response));
            }

            return created(response);
        }

        private ResponseEntity<SampleResourceResponse> created(SampleResourceResponse response) {
            return ResponseEntity
                    .created(URI.create("/api/v1/sample-resources/" + response.id()))
                    .body(response);
        }

        record IdempotencyRecord(CreateSampleResourceRequest fingerprint, SampleResourceResponse response) {}
    }
}
