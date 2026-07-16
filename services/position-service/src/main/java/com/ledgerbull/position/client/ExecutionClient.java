package com.ledgerbull.position.client;

import com.ledgerbull.position.client.dto.ExecutionIngestFill;
import com.ledgerbull.position.client.dto.ExecutionOrderDetail;
import com.ledgerbull.position.client.dto.ExecutionOrderFill;
import com.ledgerbull.position.client.dto.ExecutionOrderPage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
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
     * Fetches all fills from execution via its REST API, ordered by fill created_at ascending
     * so FIFO replay can follow trade chronology. Marks execution unreachable on connection errors.
     */
    public ExecutionFillsFetchResult fetchAllFills() {
        try {
            Set<String> orderIds = new LinkedHashSet<>();
            for (String status : ORDER_STATUSES_WITH_FILLS) {
                orderIds.addAll(listOrderIds(status));
            }

            List<ExecutionIngestFill> fills = new ArrayList<>();
            for (String orderId : orderIds) {
                ExecutionOrderDetail detail = fetchOrder(orderId);
                if (detail != null && detail.fills() != null) {
                    for (ExecutionOrderFill fill : detail.fills()) {
                        fills.add(new ExecutionIngestFill(
                                fill.taker_order_id(),
                                fill.maker_order_id(),
                                fill.symbol(),
                                fill.price(),
                                fill.quantity(),
                                detail.side(),
                                fill.created_at()));
                    }
                }
            }
            fills.sort(Comparator.comparing(
                    ExecutionIngestFill::created_at,
                    Comparator.nullsLast(String::compareTo)));
            return ExecutionFillsFetchResult.success(fills);
        } catch (RestClientException ex) {
            log.warn("Execution service unreachable while fetching fills: {}", ex.getMessage());
            return ExecutionFillsFetchResult.unreachable();
        }
    }

    private Set<String> listOrderIds(String status) {
        Set<String> orderIds = new LinkedHashSet<>();
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
