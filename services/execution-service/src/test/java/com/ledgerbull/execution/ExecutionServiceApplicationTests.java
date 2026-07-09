package com.ledgerbull.execution;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "matching-engine.host=127.0.0.1",
        "matching-engine.port=59999",
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false"
})
class ExecutionServiceApplicationTests {

	@Test
	void contextLoads() {
	}
}
