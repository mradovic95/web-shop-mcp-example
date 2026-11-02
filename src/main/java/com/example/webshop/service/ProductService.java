package com.example.webshop.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

import com.example.webshop.domain.Product;
import com.example.webshop.dto.CreateProductRequest;
import com.example.webshop.dto.PageResponse;
import com.example.webshop.dto.ProductFilter;
import com.example.webshop.dto.ProductResponse;
import com.example.webshop.dto.UpdateProductRequest;
import com.example.webshop.exception.ProductErrorCode;
import com.example.webshop.exception.ProductException;
import com.example.webshop.mapper.ProductMapper;
import com.example.webshop.repository.ProductRepository;
import com.example.webshop.repository.ProductSpecifications;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Validated
@Transactional
@RequiredArgsConstructor
public class ProductService {

	private final ProductRepository productRepository;
	private final ProductMapper productMapper;

	@Transactional(readOnly = true)
	public PageResponse<ProductResponse> findAll(@Valid ProductFilter filter, Pageable pageable) {
		log.info("Finding products with filter {} and pageable {}", filter, pageable);
		Page<ProductResponse> page = productRepository
			.findAll(ProductSpecifications.from(filter), pageable)
			.map(productMapper::toResponse);
		return PageResponse.of(page);
	}

	@Transactional(readOnly = true)
	public ProductResponse findById(Long id) {
		log.info("Finding product with id {}", id);
		return productMapper.toResponse(getProduct(id));
	}

	public ProductResponse create(@Valid CreateProductRequest request) {
		log.info("Creating product {}", request);
		Product saved = productRepository.save(productMapper.toEntity(request));
		return productMapper.toResponse(saved);
	}

	public ProductResponse update(Long id, @Valid UpdateProductRequest request) {
		log.info("Updating product with id {}: {}", id, request);
		Product updated = productMapper.toUpdatedEntity(getProduct(id), request);
		return productMapper.toResponse(productRepository.saveAndFlush(updated));
	}

	public void delete(Long id) {
		log.info("Deleting product with id {}", id);
		productRepository.delete(getProduct(id));
	}

	private Product getProduct(Long id) {
		return productRepository.findById(id)
			.orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND,
				Map.of("productId", id),
				"Product not found: " + id));
	}
}
