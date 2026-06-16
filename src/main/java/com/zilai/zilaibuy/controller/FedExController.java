package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.entity.FedExShipmentEntity;
import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.repository.FedExShipmentRepository;
import com.zilai.zilaibuy.repository.UserRepository;
import com.zilai.zilaibuy.security.AuthenticatedUser;
import com.zilai.zilaibuy.service.FedExService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/fedex")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class FedExController {

    private final FedExService fedExService;
    private final FedExShipmentRepository shipmentRepository;
    private final UserRepository userRepository;

    public record ShipmentDto(
            Long id, String trackingNo, String recipientName, String recipientCity,
            String recipientCountry, Double weightLbs, String serviceType,
            BigDecimal netCharge, String currency, String status,
            LocalDateTime createdAt, Long createdBy, String createdByName) {}

    private static String displayName(UserEntity u) {
        if (u == null) return null;
        if (u.getDisplayName() != null && !u.getDisplayName().isBlank()) return u.getDisplayName();
        if (u.getUsername() != null && !u.getUsername().isBlank()) return u.getUsername();
        return u.getPhone();
    }

    @PostMapping("/shipments")
    public ResponseEntity<FedExService.ShipResult> createShipment(
            @RequestBody FedExService.ShipRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        FedExService.ShipResult result = fedExService.createShipment(req, currentUser.id());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/shipments")
    public ResponseEntity<Page<ShipmentDto>> listShipments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<FedExShipmentEntity> pageData =
                shipmentRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));

        // Batch-resolve creator names so every admin sees who created each label.
        var ids = pageData.getContent().stream()
                .map(FedExShipmentEntity::getCreatedBy)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> nameById = new HashMap<>();
        if (!ids.isEmpty()) {
            userRepository.findAllById(ids).forEach(u -> nameById.put(u.getId(), displayName(u)));
        }

        return ResponseEntity.ok(pageData.map(e -> new ShipmentDto(
                e.getId(), e.getTrackingNo(), e.getRecipientName(), e.getRecipientCity(),
                e.getRecipientCountry(), e.getWeightLbs(), e.getServiceType(),
                e.getNetCharge(), e.getCurrency(), e.getStatus(),
                e.getCreatedAt(), e.getCreatedBy(),
                e.getCreatedBy() != null ? nameById.getOrDefault(e.getCreatedBy(), "#" + e.getCreatedBy()) : "—"
        )));
    }

    /** Get rate quotes without creating a shipment. */
    @PostMapping("/rate")
    public ResponseEntity<java.util.List<FedExService.RateResult>> getRate(
            @RequestBody FedExService.ShipRequest req) {
        return ResponseEntity.ok(fedExService.getRates(req));
    }

    /** Re-download the stored PDF label (any admin). */
    @GetMapping("/shipments/{id}/label")
    public ResponseEntity<Map<String, String>> getLabel(@PathVariable Long id) {
        FedExShipmentEntity e = shipmentRepository.findById(id).orElse(null);
        if (e == null || e.getLabelBase64() == null || e.getLabelBase64().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        Map<String, String> body = new HashMap<>();
        body.put("trackingNo", e.getTrackingNo());
        body.put("labelBase64", e.getLabelBase64());
        return ResponseEntity.ok(body);
    }

    /** Void / cancel an unused FedEx label (creator only). */
    @PostMapping("/shipments/{id}/cancel")
    public ResponseEntity<Void> cancelShipment(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        fedExService.cancelShipment(id, currentUser.id());
        return ResponseEntity.ok().build();
    }
}
