package com.zilai.zilaibuy.service;

import com.zilai.zilaibuy.dto.warehouse.CreateProductRequest;
import com.zilai.zilaibuy.dto.warehouse.ProductDto;
import com.zilai.zilaibuy.entity.ProductEntity;
import com.zilai.zilaibuy.entity.ProductImageEntity;
import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.exception.AppException;
import com.zilai.zilaibuy.repository.ProductImageRepository;
import com.zilai.zilaibuy.repository.ProductRepository;
import com.zilai.zilaibuy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final UserRepository userRepository;

    @Transactional
    public ProductDto createProduct(CreateProductRequest req, Long creatorId) {
        UserEntity creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "用户不存在"));
        ProductEntity product = new ProductEntity();
        product.setTitle(req.title());
        product.setDescription(req.description());
        product.setPriceCny(req.priceCny());
        product.setStockQuantity(req.stockQuantity());
        product.setPublished(req.isPublished());
        product.setCreatedBy(creator);
        productRepository.save(product);
        saveImages(product, req.imageUrls());
        productRepository.flush();
        return ProductDto.from(productRepository.findById(product.getId()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public Page<ProductDto> listProducts(Pageable pageable) {
        return productRepository.findAll(pageable).map(ProductDto::from);
    }

    @Transactional(readOnly = true)
    public Page<ProductDto> listPublishedProducts(Pageable pageable) {
        return productRepository.findByIsPublishedTrue(pageable).map(ProductDto::from);
    }

    @Transactional
    public ProductDto updateProduct(Long id, CreateProductRequest req) {
        ProductEntity product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "商品不存在"));
        product.setTitle(req.title());
        product.setDescription(req.description());
        product.setPriceCny(req.priceCny());
        product.setStockQuantity(req.stockQuantity());
        product.setPublished(req.isPublished());
        productRepository.save(product);
        productImageRepository.deleteByProductId(id);
        saveImages(product, req.imageUrls());
        productRepository.flush();
        return ProductDto.from(productRepository.findById(id).orElseThrow());
    }

    @Transactional
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new AppException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        productRepository.deleteById(id);
    }

    private void saveImages(ProductEntity product, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) return;
        for (int i = 0; i < imageUrls.size(); i++) {
            String url = imageUrls.get(i);
            if (url == null || url.isBlank()) continue;
            ProductImageEntity img = new ProductImageEntity();
            img.setProduct(product);
            img.setImageUrl(url.trim());
            img.setSortOrder(i);
            productImageRepository.save(img);
        }
    }
}
