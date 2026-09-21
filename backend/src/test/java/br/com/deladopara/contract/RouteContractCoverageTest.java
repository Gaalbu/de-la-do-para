package br.com.deladopara.contract;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

/**
 * Harness de contrato (C11b): toda rota HTTP implementada precisa existir no
 * contrato canônico ou na allowlist de infraestrutura. Direção
 * implementação→contrato; a direção contrato→implementação é provada no PR
 * de cada endpoint (contract-first).
 */
@SpringBootTest
class RouteContractCoverageTest {

    private static final Pattern TEMPLATE = Pattern.compile("\\{[^}]+\\}");

    private final ObjectProvider<RequestMappingHandlerMapping> mappings;

    @Autowired
    RouteContractCoverageTest(ObjectProvider<RequestMappingHandlerMapping> mappings) {
        this.mappings = mappings;
    }

    @Test
    void everyImplementedRouteIsDocumented() throws Exception {
        var repoRoot = Path.of(System.getProperty("user.dir")).getParent();
        var specPaths = loadSpecPaths(repoRoot.resolve("contracts/openapi/v1.yaml"));
        var allowlist = loadAllowlist(repoRoot.resolve("contracts/openapi/route-allowlist.properties"));
        Assertions.assertFalse(specPaths.isEmpty(), "contrato canônico precisa declarar paths");

        var undocumented = new HashSet<String>();
        mappings.orderedStream()
                .forEach(handler -> handler.getHandlerMethods().forEach((info, method) -> {
                    var patterns = info.getPathPatternsCondition();
                    if (patterns == null) {
                        return;
                    }
                    patterns.getPatterns().forEach(pattern -> {
                        var route = normalize(pattern.getPatternString());
                        if (!specPaths.contains(route) && !allowlist.contains(route)) {
                            undocumented.add(route + " <- " + method.getShortLogMessage());
                        }
                    });
                }));
        Assertions.assertTrue(undocumented.isEmpty(), () -> "rotas sem contrato: " + undocumented);
    }

    private static Set<String> loadSpecPaths(Path spec) throws Exception {
        var yaml = new Yaml().loadAs(Files.readString(spec), Map.class);
        var paths = (Map<?, ?>) yaml.get("paths");
        Assertions.assertNotNull(paths, "spec sem bloco paths: " + spec);
        var normalized = new HashSet<String>();
        paths.keySet().forEach(key -> normalized.add(normalize(String.valueOf(key))));
        return normalized;
    }

    private static Set<String> loadAllowlist(Path allowlist) throws Exception {
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(allowlist)) {
            properties.load(reader);
        }
        var normalized = new HashSet<String>();
        for (var entry : String.valueOf(properties.getOrDefault("undocumentedAllowlist", ""))
                .split(",")) {
            var route = entry.trim();
            if (!route.isEmpty()) {
                normalized.add(normalize(route));
            }
        }
        return normalized;
    }

    private static String normalize(String route) {
        return TEMPLATE.matcher(route).replaceAll("{}");
    }
}
