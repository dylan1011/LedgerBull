package com.ledgerbull.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Live round-trip against a running C++ matching engine.
 * Enable with {@code LEDGERBULL_ENGINE_INTEGRATION=true} and engine on port 50051.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ExecutionServiceTestConfiguration.class)
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "LEDGERBULL_ENGINE_INTEGRATION", matches = "true")
class ExecutionRoundTripIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Order(1)
    void restingSellAccepted() throws Exception {
        mockMvc.perform(post("/api/execution/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"order_id":"9001","symbol":"BTC-USD","side":"SELL","type":"LIMIT","price":105,"quantity":5}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @Order(2)
    void crossingBuyReturnsFill() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/execution/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"order_id":"9002","symbol":"BTC-USD","side":"BUY","type":"LIMIT","price":105,"quantity":5}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(body.get("accepted").asBoolean());
        assertEquals(1, body.get("fills").size());
        JsonNode fill = body.get("fills").get(0);
        assertEquals("9002", fill.get("taker_order_id").asText());
        assertEquals("9001", fill.get("maker_order_id").asText());
        assertEquals(105.0, fill.get("price").asDouble(), 0.0001);
        assertEquals(5, fill.get("quantity").asLong());
        assertEquals(0, body.get("resting_quantity").asLong());
    }
}
