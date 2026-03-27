package com.zilai.zilaibuy.service;

import com.zilai.zilaibuy.dto.warehouse.CreateProductRequest;
import com.zilai.zilaibuy.dto.warehouse.ProductDto;
import com.zilai.zilaibuy.entity.NewProductEntity;
import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.exception.AppException;
import com.zilai.zilaibuy.repository.NewProductRepository;
import com.zilai.zilaibuy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NewProductService {

    private final NewProductRepository newProductRepository;
    private final UserRepository userRepository;

    @Transactional
    public ProductDto createProduct(CreateProductRequest req, Long creatorId) {
        UserEntity creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "用户不存在"));
        NewProductEntity product = new NewProductEntity();
        product.setTitle(req.title());
        product.setDescription(req.description());
        product.setPriceCny(req.priceCny());
        product.setStockQuantity(req.stockQuantity());
        product.setPublished(req.isPublished());
        product.setCreatedBy(creator);
        setImages(product, req.imageUrls());
        newProductRepository.save(product);
        return toDto(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductDto> listProducts(Pageable pageable) {
        return newProductRepository.findAll(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<ProductDto> listPublishedProducts(Pageable pageable) {
        return newProductRepository.findByPublishedTrue(pageable).map(this::toDto);
    }

    @Transactional
    public ProductDto updateProduct(Long id, CreateProductRequest req) {
        NewProductEntity product = newProductRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "商品不存在"));
        product.setTitle(req.title());
        product.setDescription(req.description());
        product.setPriceCny(req.priceCny());
        product.setStockQuantity(req.stockQuantity());
        product.setPublished(req.isPublished());
        product.getImageUrls().clear();
        setImages(product, req.imageUrls());
        newProductRepository.save(product);
        return toDto(product);
    }

    @Transactional
    public void deleteProduct(Long id) {
        if (!newProductRepository.existsById(id)) {
            throw new AppException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        newProductRepository.deleteById(id);
    }

    private void setImages(NewProductEntity product, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) return;
        List<String> filtered = new ArrayList<>();
        for (String url : imageUrls) {
            if (url != null && !url.isBlank()) filtered.add(url.trim());
        }
        product.setImageUrls(filtered);
    }

    private ProductDto toDto(NewProductEntity e) {
        return new ProductDto(e.getId(), e.getTitle(), e.getDescription(),
                e.getPriceCny(), e.getStockQuantity(), e.isPublished(),
                e.getImageUrls(), e.getCreatedAt());
    }
}
