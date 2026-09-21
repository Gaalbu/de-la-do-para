package br.com.deladopara;

import br.com.deladopara.support.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresTestContainer.class)
class DeLaDoParaApplicationTests {

    @Test
    void contextLoads() {}
}
