#include "ledgerbull/grpc/matching_engine_service.hpp"

#include "ledgerbull/matching_engine.hpp"

#include <map>
#include <optional>
#include <stdexcept>

namespace ledgerbull::grpc_server {
namespace {

using ProtoSide = ::ledgerbull::api::Side;
using ProtoOrderType = ::ledgerbull::api::OrderType;

std::optional<OrderId> parse_order_id(const std::string& raw, std::string* err) {
    if (raw.empty()) {
        *err = "order_id is required";
        return std::nullopt;
    }
    try {
        const unsigned long long v = std::stoull(raw);
        if (v == 0) {
            *err = "order_id must be non-zero";
            return std::nullopt;
        }
        return static_cast<OrderId>(v);
    } catch (const std::exception&) {
        *err = "order_id must be a numeric string";
        return std::nullopt;
    }
}

std::optional<Side> to_engine_side(ProtoSide side, std::string* err) {
    switch (side) {
        case ProtoSide::BUY:
            return Side::BUY;
        case ProtoSide::SELL:
            return Side::SELL;
        default:
            *err = "side must be BUY or SELL";
            return std::nullopt;
    }
}

std::optional<OrderType> to_engine_type(ProtoOrderType type, std::string* err) {
    switch (type) {
        case ProtoOrderType::LIMIT:
            return OrderType::LIMIT;
        case ProtoOrderType::MARKET:
            return OrderType::MARKET;
        default:
            *err = "type must be LIMIT or MARKET";
            return std::nullopt;
    }
}

std::optional<Quantity> find_resting_order(const ledgerbull::MatchingEngine& engine, OrderId id) {
    return engine.resting_quantity(id);
}

void aggregate_levels(const std::vector<Order>& orders,
                      google::protobuf::RepeatedPtrField<::ledgerbull::api::BookLevel>* out) {
    std::map<Price, Quantity> levels;
    for (const Order& o : orders) {
        levels[o.price] += o.quantity;
    }
    for (const auto& [price, qty] : levels) {
        ::ledgerbull::api::BookLevel* level = out->Add();
        level->set_price(price);
        level->set_quantity(qty);
    }
}

}  // namespace

MatchingEngineServiceImpl::MatchingEngineServiceImpl(ledgerbull::MatchingEngine* engine)
    : engine_(engine) {}

::grpc::Status MatchingEngineServiceImpl::SubmitOrder(
        ::grpc::ServerContext* /*context*/,
        const ::ledgerbull::api::SubmitOrderRequest* request,
        ::ledgerbull::api::SubmitOrderResponse* response) {
    std::string err;
    const auto order_id = parse_order_id(request->order_id(), &err);
    if (!order_id) {
        response->set_accepted(false);
        response->set_reject_reason(err);
        return ::grpc::Status::OK;
    }

    if (request->symbol().empty()) {
        response->set_accepted(false);
        response->set_reject_reason("symbol is required");
        return ::grpc::Status::OK;
    }

    if (find_resting_order(*engine_, *order_id)) {
        response->set_accepted(false);
        response->set_reject_reason("duplicate order_id already resting in book");
        return ::grpc::Status::OK;
    }

    const auto side = to_engine_side(request->side(), &err);
    if (!side) {
        response->set_accepted(false);
        response->set_reject_reason(err);
        return ::grpc::Status::OK;
    }

    const auto type = to_engine_type(request->type(), &err);
    if (!type) {
        response->set_accepted(false);
        response->set_reject_reason(err);
        return ::grpc::Status::OK;
    }

    if (request->quantity() <= 0) {
        response->set_accepted(false);
        response->set_reject_reason("quantity must be positive");
        return ::grpc::Status::OK;
    }

    if (*type == OrderType::LIMIT && request->price() <= 0) {
        response->set_accepted(false);
        response->set_reject_reason("LIMIT orders require a positive price (ticks)");
        return ::grpc::Status::OK;
    }

    Order order(*order_id, request->symbol(), *side, request->price(), request->quantity(),
                *type);
    const std::vector<Fill> fills = engine_->submit_order(order);

    response->set_accepted(true);
    for (const Fill& fill : fills) {
        ::ledgerbull::api::Fill* out = response->add_fills();
        out->set_taker_order_id(std::to_string(fill.taker_order_id));
        out->set_maker_order_id(std::to_string(fill.maker_order_id));
        out->set_price(fill.price);
        out->set_quantity(fill.quantity);
        out->set_symbol(fill.symbol);
    }

    if (const auto resting = find_resting_order(*engine_, *order_id)) {
        response->set_resting_quantity(*resting);
    } else {
        response->set_resting_quantity(0);
    }
    return ::grpc::Status::OK;
}

::grpc::Status MatchingEngineServiceImpl::CancelOrder(
        ::grpc::ServerContext* /*context*/,
        const ::ledgerbull::api::CancelOrderRequest* request,
        ::ledgerbull::api::CancelOrderResponse* response) {
    std::string err;
    const auto order_id = parse_order_id(request->order_id(), &err);
    if (!order_id) {
        response->set_cancelled(false);
        return ::grpc::Status(::grpc::StatusCode::INVALID_ARGUMENT, err);
    }

    response->set_cancelled(engine_->cancel_order(*order_id));
    return ::grpc::Status::OK;
}

::grpc::Status MatchingEngineServiceImpl::QueryBook(
        ::grpc::ServerContext* /*context*/,
        const ::ledgerbull::api::BookQueryRequest* request,
        ::ledgerbull::api::BookQueryResponse* response) {
    const std::string& symbol =
            request->symbol().empty() ? engine_->book().symbol() : request->symbol();
    const OrderBook& book = engine_->book_for(symbol);

    aggregate_levels(book.get_bids(), response->mutable_bids());
    aggregate_levels(book.get_asks(), response->mutable_asks());
    return ::grpc::Status::OK;
}

}  // namespace ledgerbull::grpc_server
