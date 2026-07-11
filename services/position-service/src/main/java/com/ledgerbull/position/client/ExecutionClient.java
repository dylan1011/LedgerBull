package com.ledgerbull.position.client;

import com.ledgerbull.position.client.dto.ExecutionOrderDetail;
import com.ledgerbull.position.client.dto.ExecutionOrderFill;
import com.ledgerbull.position.client.dto.ExecutionOrderPage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ExecutionClient {

    private static final Logger log = LoggerFactory.getLogger(ExecutionClient.class);
    private static final int PAGE_SIZE = 50;
    private static final List<String> ORDER_STATUSES_WITH_FILLS = List.of("FILLED", "PARTIALLY_FILLED");

    private final RestClient executionRestClient;

    public ExecutionClient(RestClient executionRestClient) {
        this.executionRestClient = executionRestClient;
    }

    /**
     * Fetches all fills from execution via its REST API. Marks execution unreachable on connection errors.
     */
    public ExecutionFillsFetchResult fetchAllFills() {
        try {
            Set<String> orderIds = new HashSet<>();
            for (String status : ORDER_STATUSES_WITH_FILLS) {
                orderIds.addAll(listOrderIds(status));
            }

            List<ExecutionOrderFill> fills = new ArrayList<>();
            for (String orderId : orderIds) {
                ExecutionOrderDetail detail = fetchOrder(orderId);
                if (detail != null && detail.fills() != null) {
                    fills.addAll(detail.fills());
                }
            }
            return ExecutionFillsFetchResult.success(fills);
        } catch (RestClientException ex) {
            log.warn("Execution service unreachable while fetching fills: {}", ex.getMessage());
            return ExecutionFillsFetchResult.unreachable();
        }
    }

    private Set<String> listOrderIds(String status) {
        Set<String> orderIds = new HashSet<>();
        int page = 0;
        int totalPages = 1;
        while (page < totalPages) {
            final int pageNum = page;
            ExecutionOrderPage response = executionRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/execution/orders")
                            .queryParam("status", status)
                            .queryParam("page", pageNum)
                            .queryParam("size", PAGE_SIZE)
                            .build())
                    .retrieve()
                    .body(ExecutionOrderPage.class);
            if (response == null || response.content() == null) {
                break;
            }
            response.content().stream()
                    .map(ExecutionOrderDetail::order_id)
                    .forEach(orderIds::add);
            totalPages = Math.max(response.total_pages(), 1);
            page++;
        }
        return orderIds;
    }

    private ExecutionOrderDetail fetchOrder(String orderId) {
        return executionRestClient.get()
                .uri("/api/execution/orders/{orderId}", orderId)
                .retrieve()
                .body(ExecutionOrderDetail.class);
    }
}
