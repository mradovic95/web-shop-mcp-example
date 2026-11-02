package com.example.webshop.config;

import com.example.webshop.domain.User;
import com.example.webshop.domain.Product;
import com.example.webshop.repository.UserRepository;
import com.example.webshop.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

	private final ProductRepository productRepository;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	public void run(ApplicationArguments args) {
		if (userRepository.count() == 0) {
			userRepository.save(User.builder()
				.username("user")
				.password(passwordEncoder.encode("password"))
				.role("USER")
				.build());
			log.info("Seeded demo user");
		}
		if (productRepository.count() > 0) {
			return;
		}
		productRepository.saveAll(List.of(
			product("Classic T-Shirt", "100% cotton, red, size M", "19.99"),
			product("Running Shoes", "Lightweight running shoes, size 42", "89.90"),
			product("Coffee Mug", "Ceramic mug, 350 ml", "9.50")
		));
		log.info("Seeded sample products");
	}

	private static Product product(String name, String description, String price) {
		return Product.builder()
			.name(name)
			.description(description)
			.price(new BigDecimal(price))
			.active(true)
			.build();
	}
}
