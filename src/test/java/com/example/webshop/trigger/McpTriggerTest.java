package com.example.webshop.trigger;

import com.example.webshop.TestcontainersConfiguration;
import com.example.webshop.domain.Product;
import com.example.webshop.helper.McpTestHelper;
import com.example.webshop.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.example.webshop.inventory.ProductTestInventory.product;
import static org.assertj.core.api.Assertions.fail;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, McpTestHelper.class, ToolCallRecorder.class, TriggerChatHelper.class})
public abstract class McpTriggerTest {

    public static final int RUNS = 3;

    public static final int REQUIRED_PASSES = 2;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TriggerChatHelper triggerChatHelper;

    public void assertTriggered(Function<Map<String, Long>, String> prompt, Consumer<TriggerRun> check) {
        List<String> report = new ArrayList<>();
        int passes = 0;
        for (int run = 1; run <= RUNS; run++) {
            Map<String, Long> ids = seedCatalog();
            String text = prompt.apply(ids);
            TriggerRun triggerRun = new TriggerRun(ids, triggerChatHelper.run(text));
            try {
                check.accept(triggerRun);
                passes++;
                report.add("run " + run + " PASS: " + triggerRun.calls());
            } catch (AssertionError e) {
                report.add("run " + run + " FAIL: " + triggerRun.calls() + "\n    " + e.getMessage().strip());
            }
        }
        if (passes < REQUIRED_PASSES) {
            fail("%d of %d runs passed, %d required (model %s)%n%s".formatted(
                    passes, RUNS, REQUIRED_PASSES, triggerChatHelper.model(), String.join("\n", report)));
        }
    }

    public Map<String, Long> seedCatalog() {
        productRepository.deleteAll();
        Map<String, Long> ids = new LinkedHashMap<>();
        for (Product product : List.of(
                product("Sticker", "5.00", true),
                product("Mug", "20.00", true),
                product("Mechanical keyboard", "89.90", true),
                product("Hoodie", "45.00", true),
                product("Jacket", "40.00", false),
                product("Poster", "12.50", false),
                product("Shoes", "120.00", true))) {
            Product saved = productRepository.save(product);
            ids.put(saved.getName(), saved.getId());
        }
        return ids;
    }
}
