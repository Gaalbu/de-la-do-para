package br.com.deladopara.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Assertions;

/**
 * Fronteiras estruturais mínimas do monólito (C09). Regras por módulo e
 * proibição de acesso a repository alheio chegam com os módulos (C15+).
 */
@AnalyzeClasses(packages = "br.com.deladopara")
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule noFieldInjection = noFields()
            .should()
            .beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
            .because("injeção deve ser por construtor, com dependências explícitas");

    @ArchTest
    static final ArchRule noJavaUtilLogging = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAPackage("java.util.logging..")
            .because("logs usam SLF4J com correlação (C12)");

    @ArchTest
    void noCyclesBetweenPackages(JavaClasses classes) {
        var slices = com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices()
                .matching("br.com.deladopara.(*)..")
                .should()
                .beFreeOfCycles()
                .because("módulos formam um grafo acíclico verificável");
        slices.check(classes);
        Assertions.assertNotNull(slices);
    }
}
