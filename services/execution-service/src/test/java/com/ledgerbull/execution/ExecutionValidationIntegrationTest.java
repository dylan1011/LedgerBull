package com.ledgerbull.execution;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ExecutionServiceTestConfiguration.class)
@TestPropertySource(properties = {
        "matching-engine.host=127.0.0.1",
        "matching-engine.port=59999",
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false"
})
class ExecutionValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void invalidOrderReturns400BeforeEngineCall() throws Exception {
        mockMvc.perform(post("/api/execution/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"order_id":"abc","symbol":"BTC-USD","side":"BUY","type":"LIMIT","price":105,"quantity":-5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("order_id must be numeric"));
    }
}
