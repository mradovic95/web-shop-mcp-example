package com.example.webshop.endpoint.mcp;

import com.example.webshop.exception.ProductException;
import com.example.webshop.exception.ValidationErrors;
import jakarta.validation.ConstraintViolationException;
import org.springframework.data.core.PropertyReferenceException;

import java.util.function.Supplier;

final class McpToolErrors {

    private McpToolErrors() {
    }

    static <T> T translating(Supplier<T> call) {
        try {
            return call.get();
        } catch (ProductException e) {
            throw translate(e);
        } catch (ConstraintViolationException e) {
            throw translate(ValidationErrors.from(e));
        } catch (PropertyReferenceException e) {
            throw translate(ValidationErrors.from(e));
        }
    }

    static IllegalArgumentException translate(ProductException e) {
        String params = e.getParams().isEmpty() ? "" : " " + e.getParams();
        return new IllegalArgumentException(e.getErrorCode() + ": " + e.getMessage() + params);
    }
}
