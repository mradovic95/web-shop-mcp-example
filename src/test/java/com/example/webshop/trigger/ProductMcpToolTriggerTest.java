package com.example.webshop.trigger;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.example.webshop.trigger.TriggerRun.WRITE_TOOLS;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("trigger")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class ProductMcpToolTriggerTest extends McpTriggerTest {

    @Test
    public void testListProducts() {
        // GIVEN
        Function<Map<String, Long>, String> prompt = ids -> "Show me active products under 50 EUR, cheapest first.";

        // WHEN
        Consumer<TriggerRun> check = run -> {
            assertThat(run.toolNames()).first().isEqualTo("list_products");
            assertThat(run.toolNames()).doesNotContainAnyElementsOf(WRITE_TOOLS);
            RecordedToolCall call = run.calls("list_products").getFirst();
            assertThat(call.argument("filter", "active")).isEqualTo(true);
            assertThat(call.decimal("filter", "maxPrice")).isBetween(new BigDecimal("49.99"), new BigDecimal("50"));
            assertThat(call.text("sort", 0, "property")).isEqualTo("price");
            assertThat(call.text("sort", 0, "direction")).isIn(null, "ASC");
        };

        // THEN
        assertTriggered(prompt, check);
    }

    @Test
    public void testListProductsTextSearch() {
        // GIVEN
        Function<Map<String, Long>, String> prompt = ids -> "Do you sell any keyboards?";

        // WHEN
        Consumer<TriggerRun> check = run -> {
            assertThat(run.toolNames()).first().isEqualTo("list_products");
            assertThat(run.toolNames()).doesNotContainAnyElementsOf(WRITE_TOOLS);
            RecordedToolCall call = run.calls("list_products").getFirst();
            String search = String.valueOf(call.text("filter", "query")) + " " + call.text("filter", "name");
            assertThat(search).containsIgnoringCase("keyboard");
        };

        // THEN
        assertTriggered(prompt, check);
    }

    @Test
    public void testGetProduct() {
        // GIVEN
        Function<Map<String, Long>, String> prompt = ids -> "Show me the details of product " + ids.get("Mug") + ".";

        // WHEN
        Consumer<TriggerRun> check = run -> {
            assertThat(run.toolNames()).containsExactly("get_product");
            assertThat(run.calls("get_product").getFirst().id("id")).isEqualTo(run.id("Mug"));
        };

        // THEN
        assertTriggered(prompt, check);
    }

    @Test
    public void testCreateProduct() {
        // GIVEN
        Function<Map<String, Long>, String> prompt = ids -> "Add a new active product called 'USB-C cable' that costs 9.99 EUR.";

        // WHEN
        Consumer<TriggerRun> check = run -> {
            assertThat(run.toolNames()).doesNotContain("update_product", "delete_product");
            assertThat(run.calls("create_product")).singleElement().satisfies(call -> {
                assertThat(call.text("name")).isEqualToIgnoringCase("USB-C cable");
                assertThat(call.decimal("price")).isEqualByComparingTo("9.99");
                assertThat(call.argument("active")).isEqualTo(true);
            });
        };

        // THEN
        assertTriggered(prompt, check);
    }

    @Test
    public void testUpdateProduct() {
        // GIVEN
        Function<Map<String, Long>, String> prompt = ids -> "Change the price of product " + ids.get("Mug") + " to 19.99 EUR.";

        // WHEN
        Consumer<TriggerRun> check = run -> {
            List<String> toolNames = run.toolNames();
            assertThat(toolNames).doesNotContain("create_product", "delete_product");
            assertThat(toolNames).contains("get_product", "update_product");
            assertThat(toolNames.indexOf("get_product")).isLessThan(toolNames.indexOf("update_product"));
            assertThat(run.calls("update_product")).singleElement().satisfies(call -> {
                assertThat(call.id("id")).isEqualTo(run.id("Mug"));
                assertThat(call.text("name")).isEqualTo("Mug");
                assertThat(call.decimal("price")).isEqualByComparingTo("19.99");
                assertThat(call.argument("active")).isEqualTo(true);
            });
        };

        // THEN
        assertTriggered(prompt, check);
    }

    @Test
    public void testDeleteProduct() {
        // GIVEN
        Function<Map<String, Long>, String> prompt = ids -> "Delete product " + ids.get("Poster") + ".";

        // WHEN
        Consumer<TriggerRun> check = run -> {
            assertThat(run.toolNames()).doesNotContain("create_product", "update_product");
            assertThat(run.calls("delete_product"))
                    .isNotEmpty()
                    .allSatisfy(call -> assertThat(call.id("id")).isEqualTo(run.id("Poster")));
        };

        // THEN
        assertTriggered(prompt, check);
    }

    @Test
    public void testNoToolForUnrelatedQuestion() {
        // GIVEN
        Function<Map<String, Long>, String> prompt = ids -> "What is the capital of France?";

        // WHEN
        Consumer<TriggerRun> check = run -> assertThat(run.calls()).isEmpty();

        // THEN
        assertTriggered(prompt, check);
    }

    @Test
    public void testListProductsDoesNotTriggerWrites() {
        // GIVEN
        Function<Map<String, Long>, String> prompt = ids -> "Which products are currently inactive?";

        // WHEN
        Consumer<TriggerRun> check = run -> {
            assertThat(run.toolNames()).doesNotContainAnyElementsOf(WRITE_TOOLS);
            assertThat(run.calls("list_products"))
                    .anySatisfy(call -> assertThat(call.argument("filter", "active")).isEqualTo(false));
        };

        // THEN
        assertTriggered(prompt, check);
    }
}
