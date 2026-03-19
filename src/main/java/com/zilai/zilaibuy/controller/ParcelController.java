package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.dto.parcel.CreateParcelRequest;
import com.zilai.zilaibuy.dto.parcel.ParcelDto;
import com.zilai.zilaibuy.security.AuthenticatedUser;
import com.zilai.zilaibuy.service.ForwardingParcelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parcels")
@RequiredArgsConstructor
public class ParcelController {

    private final ForwardingParcelService parcelService;

    @PostMapping
    public ResponseEntity<ParcelDto> create(
            @Valid @RequestBody CreateParcelRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(parcelService.createParcel(req, currentUser.id()));
    }

    @GetMapping
    public ResponseEntity<List<ParcelDto>> list(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(parcelService.listUserParcels(currentUser.id()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ParcelDto> update(
            @PathVariable Long id,
            @Valid @RequestBody CreateParcelRequest req,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(parcelService.updateParcel(id, req, currentUser.id()));
    }

    @PatchMapping("/{id}/notes")
    public ResponseEntity<ParcelDto> updateNotes(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(parcelService.updateNotes(id, body.get("notes"), currentUser.id()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        parcelService.deleteParcel(id, currentUser.id());
        return ResponseEntity.noContent().build();
    }
}
