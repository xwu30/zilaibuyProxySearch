package com.zilai.zilaibuy.controller;

import com.zilai.zilaibuy.dto.warehouse.ProductDto;
import com.zilai.zilaibuy.service.NewProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/new-products")
@RequiredArgsConstructor
public class NewProductController {

    private final NewProductService newProductService;

    @GetMapping
    public ResponseEntity<Page<ProductDto>> listPublished(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(newProductService.listPublishedProducts(pageable));
    }
}
