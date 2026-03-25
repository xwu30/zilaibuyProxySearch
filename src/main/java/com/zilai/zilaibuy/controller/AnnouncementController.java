package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.entity.AnnouncementEntity;
import com.zilai.zilaibuy.repository.AnnouncementRepository;
import com.zilai.zilaibuy.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementRepository repo;

    record AnnouncementDto(Long id, String title, String tag, boolean isPinned, String publishDate) {
        static AnnouncementDto from(AnnouncementEntity e) {
            return new AnnouncementDto(e.getId(), e.getTitle(), e.getTag(), e.isPinned(),
                    e.getPublishDate().toString());
        }
    }

    record SaveRequest(String title, String tag, boolean isPinned, String publishDate) {}

    // Public: list all (ordered pinned first, then by date desc)
    @GetMapping
    public ResponseEntity<Page<AnnouncementDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AnnouncementDto> result = repo.findAllByOrderByPinnedDescPublishDateDesc(
                PageRequest.of(page, size)).map(AnnouncementDto::from);
        return ResponseEntity.ok(result);
    }

    // SUPPORT or ADMIN: create
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPPORT','ADMIN')")
    public ResponseEntity<AnnouncementDto> create(
            @RequestBody SaveRequest req,
            @AuthenticationPrincipal AuthenticatedUser user) {
        AnnouncementEntity e = new AnnouncementEntity();
        e.setTitle(req.title());
        e.setTag(req.tag() != null && !req.tag().isBlank() ? req.tag() : null);
        e.setPinned(req.isPinned());
        e.setPublishDate(LocalDate.parse(req.publishDate()));
        e.setCreatedBy(user.phone());
        return ResponseEntity.ok(AnnouncementDto.from(repo.save(e)));
    }

    // SUPPORT or ADMIN: update
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPPORT','ADMIN')")
    public ResponseEntity<AnnouncementDto> update(
            @PathVariable Long id,
            @RequestBody SaveRequest req) {
        AnnouncementEntity e = repo.findById(id).orElseThrow();
        e.setTitle(req.title());
        e.setTag(req.tag() != null && !req.tag().isBlank() ? req.tag() : null);
        e.setPinned(req.isPinned());
        e.setPublishDate(LocalDate.parse(req.publishDate()));
        return ResponseEntity.ok(AnnouncementDto.from(repo.save(e)));
    }

    // SUPPORT or ADMIN: delete
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPPORT','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
