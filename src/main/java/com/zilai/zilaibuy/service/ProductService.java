package com.zilai.zilaibuy.service;

import com.zilai.zilaibuy.dto.warehouse.CreateProductRequest;
import com.zilai.zilaibuy.dto.warehouse.ProductDto;
import com.zilai.zilaibuy.entity.ProductEntity;
import com.zilai.zilaibuy.entity.UserEntity;
import com.zilai.zilaibuy.exception.AppException;
import com.zilai.zilaibuy.repository.ProductRepository;
import com.zilai.zilaibuy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
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
        return ProductDto.from(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductDto> listProducts(Pageable pageable) {
        return productRepository.findAll(pageable).map(ProductDto::from);
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
        return ProductDto.from(product);
    }

    @Transactional
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new AppException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        productRepository.deleteById(id);
    }
}
