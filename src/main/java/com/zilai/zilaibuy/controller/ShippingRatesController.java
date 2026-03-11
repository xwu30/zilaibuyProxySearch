package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.security.AuthenticatedUser;
import com.zilai.zilaibuy.service.ShippingRatesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shipping-rates")
@RequiredArgsConstructor
public class ShippingRatesController {

    private final ShippingRatesService service;

    /**
     * GET /api/shipping-rates
     * 公开接口——返回当前运费 JSON；无记录时返回 204，前端回退到内置默认值。
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getRates() {
        String json = service.getRatesJson();
        if (json == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(json);
    }

    /**
     * PUT /api/shipping-rates
     * 仅 ADMIN 可调用——body 直接为运费 JSON 字符串。
     */
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> saveRates(
            @RequestBody String ratesJson,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        service.saveRatesJson(ratesJson, principal.phone());
        return ResponseEntity.ok().build();
    }
}
