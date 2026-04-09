package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.entity.ContactLeadEntity;
import com.zilai.zilaibuy.repository.ContactLeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class LeadController {

    private final ContactLeadRepository leadRepo;

    record CreateLeadRequest(String email, String wechat, String phone, String note) {}

    @PostMapping("/api/leads")
    public ResponseEntity<Void> createLead(@RequestBody CreateLeadRequest req) {
        if ((req.email() == null || req.email().isBlank())
                && (req.wechat() == null || req.wechat().isBlank())
                && (req.phone() == null || req.phone().isBlank())) {
            return ResponseEntity.badRequest().build();
        }
        ContactLeadEntity lead = new ContactLeadEntity();
        lead.setEmail(req.email());
        lead.setWechat(req.wechat());
        lead.setPhone(req.phone());
        lead.setNote(req.note());
        leadRepo.save(lead);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/leads")
    public Page<ContactLeadEntity> listLeads(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return leadRepo.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }
}
