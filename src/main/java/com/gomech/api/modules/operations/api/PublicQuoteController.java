package com.gomech.api.modules.operations.api;

import com.gomech.api.modules.operations.api.dto.PublicCustomerDecisionRequest;
import com.gomech.api.modules.operations.api.dto.PublicQuoteResponse;
import com.gomech.api.modules.operations.application.PublicQuoteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/quotes")
public class PublicQuoteController {

    private final PublicQuoteService publicQuoteService;

    public PublicQuoteController(PublicQuoteService publicQuoteService) {
        this.publicQuoteService = publicQuoteService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PublicQuoteResponse> getPublicQuote(@PathVariable UUID id) {
        PublicQuoteResponse response = publicQuoteService.getPublicQuote(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/decision")
    public ResponseEntity<PublicQuoteResponse> processPublicDecision(
            @PathVariable UUID id,
            @Valid @RequestBody PublicCustomerDecisionRequest request
    ) {
        PublicQuoteResponse response = publicQuoteService.processPublicDecision(id, request);
        return ResponseEntity.ok(response);
    }
}
