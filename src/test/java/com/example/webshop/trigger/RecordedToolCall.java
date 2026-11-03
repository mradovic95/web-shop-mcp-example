package com.example.webshop.trigger;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record RecordedToolCall(String name, Map<String, Object> arguments) {

    public Object argument(Object... path) {
        Object current = arguments;
        for (Object key : path) {
            if (current instanceof Map<?, ?> map && key instanceof String property) {
                current = map.get(property);
            } else if (current instanceof List<?> list && key instanceof Integer index && index < list.size()) {
                current = list.get(index);
            } else {
                return null;
            }
        }
        return current;
    }

    public String text(Object... path) {
        Object value = argument(path);
        return value == null ? null : String.valueOf(value);
    }

    public BigDecimal decimal(Object... path) {
        String value = text(path);
        return value == null ? null : new BigDecimal(value);
    }

    public Long id(Object... path) {
        BigDecimal value = decimal(path);
        return value == null ? null : value.longValueExact();
    }
}
