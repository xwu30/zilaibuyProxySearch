package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.entity.BannerEntity;
import com.zilai.zilaibuy.repository.BannerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class BannerController {

    private final BannerRepository bannerRepository;

    public BannerController(BannerRepository bannerRepository) {
        this.bannerRepository = bannerRepository;
    }

    record BannerDto(Long id, String imageUrl, String linkUrl, int sortOrder, boolean enabled) {
        static BannerDto from(BannerEntity e) {
            return new BannerDto(e.getId(), e.getImageUrl(), e.getLinkUrl(), e.getSortOrder(), e.isEnabled());
        }
    }

    record BannerRequest(String imageUrl, String linkUrl, Boolean enabled) {}

    // ── Public ────────────────────────────────────────────────────────────────

    @GetMapping("/api/banners")
    public ResponseEntity<List<BannerDto>> listPublic() {
        return ResponseEntity.ok(bannerRepository.findByEnabledTrueOrderBySortOrderAsc()
                .stream().map(BannerDto::from).toList());
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT')")
    @GetMapping("/api/admin/banners")
    public ResponseEntity<List<BannerDto>> listAll() {
        return ResponseEntity.ok(bannerRepository.findAllByOrderBySortOrderAsc()
                .stream().map(BannerDto::from).toList());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT')")
    @PostMapping("/api/admin/banners")
    public ResponseEntity<BannerDto> create(@RequestBody BannerRequest req) {
        List<BannerEntity> existing = bannerRepository.findAllByOrderBySortOrderAsc();
        int nextSort = existing.isEmpty() ? 0 : existing.get(existing.size() - 1).getSortOrder() + 1;
        BannerEntity b = new BannerEntity();
        b.setImageUrl(req.imageUrl());
        b.setLinkUrl(req.linkUrl() != null && !req.linkUrl().isBlank() ? req.linkUrl().trim() : null);
        b.setSortOrder(nextSort);
        b.setEnabled(req.enabled() != null ? req.enabled() : true);
        return ResponseEntity.ok(BannerDto.from(bannerRepository.save(b)));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT')")
    @PutMapping("/api/admin/banners/{id}")
    public ResponseEntity<BannerDto> update(@PathVariable Long id, @RequestBody BannerRequest req) {
        BannerEntity b = bannerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Banner not found"));
        if (req.imageUrl() != null) b.setImageUrl(req.imageUrl());
        if (req.linkUrl() != null) b.setLinkUrl(req.linkUrl().isBlank() ? null : req.linkUrl().trim());
        if (req.enabled() != null) b.setEnabled(req.enabled());
        return ResponseEntity.ok(BannerDto.from(bannerRepository.save(b)));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT')")
    @DeleteMapping("/api/admin/banners/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bannerRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    record MoveRequest(String direction) {}

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT')")
    @PutMapping("/api/admin/banners/{id}/move")
    public ResponseEntity<List<BannerDto>> move(@PathVariable Long id, @RequestBody MoveRequest req) {
        List<BannerEntity> all = bannerRepository.findAllByOrderBySortOrderAsc();
        int idx = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(id)) { idx = i; break; }
        }
        if (idx < 0) return ResponseEntity.notFound().build();
        int swapIdx = "up".equals(req.direction()) ? idx - 1 : idx + 1;
        if (swapIdx < 0 || swapIdx >= all.size()) return ResponseEntity.ok(all.stream().map(BannerDto::from).toList());

        BannerEntity a = all.get(idx);
        BannerEntity b = all.get(swapIdx);
        int tmp = a.getSortOrder();
        a.setSortOrder(b.getSortOrder());
        b.setSortOrder(tmp);
        bannerRepository.save(a);
        bannerRepository.save(b);
        return ResponseEntity.ok(bannerRepository.findAllByOrderBySortOrderAsc().stream().map(BannerDto::from).toList());
    }
}
