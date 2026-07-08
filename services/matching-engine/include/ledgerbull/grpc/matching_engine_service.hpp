#pragma once

#include "matching_engine.grpc.pb.h"

namespace ledgerbull {
class MatchingEngine;
}

namespace ledgerbull::grpc_server {

/// Thin gRPC adapter over the existing MatchingEngine (2A/2B/2C). Translates protobuf
/// requests into engine calls and back — no matching or recovery logic here.
class MatchingEngineServiceImpl final : public api::MatchingEngine::Service {
public:
    explicit MatchingEngineServiceImpl(ledgerbull::MatchingEngine* engine);

    ::grpc::Status SubmitOrder(::grpc::ServerContext* context,
                               const ::ledgerbull::api::SubmitOrderRequest* request,
                               ::ledgerbull::api::SubmitOrderResponse* response) override;

    ::grpc::Status CancelOrder(::grpc::ServerContext* context,
                               const ::ledgerbull::api::CancelOrderRequest* request,
                               ::ledgerbull::api::CancelOrderResponse* response) override;

    ::grpc::Status QueryBook(::grpc::ServerContext* context,
                             const ::ledgerbull::api::BookQueryRequest* request,
                             ::ledgerbull::api::BookQueryResponse* response) override;

private:
    ledgerbull::MatchingEngine* engine_;
};

}  // namespace ledgerbull::grpc_server
