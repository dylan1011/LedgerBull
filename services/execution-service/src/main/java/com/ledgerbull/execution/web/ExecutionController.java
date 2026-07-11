package com.ledgerbull.execution.web;

import com.ledgerbull.execution.service.ExecutionService;
import com.ledgerbull.execution.web.dto.BookResponse;
import com.ledgerbull.execution.web.dto.OrderDetailResponse;
import com.ledgerbull.execution.web.dto.OrderPageResponse;
import com.ledgerbull.execution.web.dto.OrderRequest;
import com.ledgerbull.execution.web.dto.SubmitOrderResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/execution")
public class ExecutionController {

    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/orders")
    public ResponseEntity<SubmitOrderResponse> submitOrder(@RequestBody OrderRequest request) {
        return ResponseEntity.ok(executionService.submitOrder(request));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderDetailResponse> getOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(executionService.getOrder(orderId));
    }

    @GetMapping("/orders")
    public ResponseEntity<OrderPageResponse> listOrders(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(executionService.listOrders(symbol, status, pageable));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<OrderDetailResponse> cancelOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(executionService.cancelOrder(orderId));
    }

    @GetMapping("/book/{symbol}")
    public BookResponse queryBook(@PathVariable String symbol) {
        return executionService.queryBook(symbol);
    }
}
