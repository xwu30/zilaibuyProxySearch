package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.entity.FedExShipmentEntity;
import com.zilai.zilaibuy.repository.FedExShipmentRepository;
import com.zilai.zilaibuy.security.AuthenticatedUser;
import com.zilai.zilaibuy.service.FedExService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fedex")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class FedExController {

    private final FedExService fedExService;
    private final FedExShipmentRepository shipmentRepository;

    @PostMapping("/shipments")
    public ResponseEntity<FedExService.ShipResult> createShipment(
            @RequestBody FedExService.ShipRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        FedExService.ShipResult result = fedExService.createShipment(req, currentUser.id());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/shipments")
    public ResponseEntity<Page<FedExShipmentEntity>> listShipments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                shipmentRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
        );
    }
}
