package com.ledgerbull.execution.web;

import com.ledgerbull.execution.service.ExecutionService;
import com.ledgerbull.execution.web.dto.BookResponse;
import com.ledgerbull.execution.web.dto.CancelOrderResponse;
import com.ledgerbull.execution.web.dto.OrderRequest;
import com.ledgerbull.execution.web.dto.SubmitOrderResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @PostMapping("/orders/{orderId}/cancel")
    public CancelOrderResponse cancelOrder(@PathVariable String orderId) {
        return executionService.cancelOrder(orderId);
    }

    @GetMapping("/book/{symbol}")
    public BookResponse queryBook(@PathVariable String symbol) {
        return executionService.queryBook(symbol);
    }
}
