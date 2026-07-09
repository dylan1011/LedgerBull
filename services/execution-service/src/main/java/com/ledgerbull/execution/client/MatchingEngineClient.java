package com.ledgerbull.execution.client;

import com.ledgerbull.execution.web.dto.BookLevelResponse;
import com.ledgerbull.execution.web.dto.BookResponse;
import com.ledgerbull.execution.web.dto.CancelOrderResponse;
import com.ledgerbull.execution.web.dto.FillResponse;
import com.ledgerbull.execution.web.dto.SubmitOrderResponse;
import com.ledgerbull.execution.web.error.EngineUnavailableException;
import com.ledgerbull.execution.service.PriceConverter;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.List;
import ledgerbull.api.MatchingEngineGrpc;
import ledgerbull.api.MatchingEngineOuterClass;
import org.springframework.stereotype.Component;

@Component
public class MatchingEngineClient {

    private final MatchingEngineGrpc.MatchingEngineBlockingStub stub;

    public MatchingEngineClient(MatchingEngineGrpc.MatchingEngineBlockingStub stub) {
        this.stub = stub;
    }

    public SubmitOrderResponse submitOrder(
            String orderId,
            String symbol,
            String side,
            String type,
            long priceTicks,
            long quantity) {
        try {
            MatchingEngineOuterClass.SubmitOrderRequest.Builder builder =
                    MatchingEngineOuterClass.SubmitOrderRequest.newBuilder()
                            .setOrderId(orderId)
                            .setSymbol(symbol)
                            .setSide(parseSide(side))
                            .setType(parseType(type))
                            .setQuantity(quantity);

            if (type.equals("LIMIT")) {
                builder.setPrice(priceTicks);
            }

            MatchingEngineOuterClass.SubmitOrderResponse response = stub.submitOrder(builder.build());
            return mapSubmitResponse(response);
        } catch (StatusRuntimeException ex) {
            if (isUnreachable(ex)) {
                throw new EngineUnavailableException("matching engine unreachable", ex);
            }
            throw ex;
        }
    }

    public CancelOrderResponse cancelOrder(String orderId) {
        try {
            MatchingEngineOuterClass.CancelOrderResponse response = stub.cancelOrder(
                    MatchingEngineOuterClass.CancelOrderRequest.newBuilder()
                            .setOrderId(orderId)
                            .build());
            return new CancelOrderResponse(response.getCancelled());
        } catch (StatusRuntimeException ex) {
            if (isUnreachable(ex)) {
                throw new EngineUnavailableException("matching engine unreachable", ex);
            }
            throw ex;
        }
    }

    public BookResponse queryBook(String symbol) {
        try {
            MatchingEngineOuterClass.BookQueryResponse response = stub.queryBook(
                    MatchingEngineOuterClass.BookQueryRequest.newBuilder()
                            .setSymbol(symbol)
                            .build());
            return new BookResponse(
                    mapLevels(response.getBidsList()),
                    mapLevels(response.getAsksList()));
        } catch (StatusRuntimeException ex) {
            if (isUnreachable(ex)) {
                throw new EngineUnavailableException("matching engine unreachable", ex);
            }
            throw ex;
        }
    }

    private static boolean isUnreachable(StatusRuntimeException ex) {
        Status.Code code = ex.getStatus().getCode();
        return code == Status.Code.UNAVAILABLE
                || code == Status.Code.DEADLINE_EXCEEDED
                || code == Status.Code.UNKNOWN;
    }

    private static MatchingEngineOuterClass.Side parseSide(String side) {
        return side.equals("BUY")
                ? MatchingEngineOuterClass.Side.BUY
                : MatchingEngineOuterClass.Side.SELL;
    }

    private static MatchingEngineOuterClass.OrderType parseType(String type) {
        return type.equals("LIMIT")
                ? MatchingEngineOuterClass.OrderType.LIMIT
                : MatchingEngineOuterClass.OrderType.MARKET;
    }

    private static SubmitOrderResponse mapSubmitResponse(
            MatchingEngineOuterClass.SubmitOrderResponse response) {
        List<FillResponse> fills = response.getFillsList().stream()
                .map(fill -> new FillResponse(
                        fill.getTakerOrderId(),
                        fill.getMakerOrderId(),
                        PriceConverter.fromTicks(fill.getPrice()),
                        fill.getQuantity(),
                        fill.getSymbol()))
                .toList();
        return new SubmitOrderResponse(
                fills,
                response.getRestingQuantity(),
                response.getAccepted(),
                response.getRejectReason());
    }

    private static List<BookLevelResponse> mapLevels(List<MatchingEngineOuterClass.BookLevel> levels) {
        return levels.stream()
                .map(level -> new BookLevelResponse(
                        PriceConverter.fromTicks(level.getPrice()),
                        level.getQuantity()))
                .toList();
    }
}
