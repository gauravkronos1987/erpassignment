package com.erp.ar.controller;

import com.erp.ar.dto.AgingResponse;
import com.erp.ar.service.AgingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final AgingService agingService;

    public CustomerController(AgingService agingService) {
        this.agingService = agingService;
    }

    @GetMapping("/{id}/aging")
    public ResponseEntity<AgingResponse> getAging(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable("id") UUID customerId,
            @RequestParam(value = "asOfDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {

        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();
        return ResponseEntity.ok(agingService.computeAging(tenantId, customerId, effectiveDate));
    }
}
