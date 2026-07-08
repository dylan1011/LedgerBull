#include "ledgerbull/event_log.hpp"
#include "ledgerbull/matching_engine.hpp"
#include "ledgerbull/grpc/matching_engine_service.hpp"

#include <grpcpp/ext/proto_server_reflection_plugin.h>
#include <grpcpp/grpcpp.h>
#include <grpcpp/health_check_service_interface.h>

#include <cstdlib>
#include <iostream>
#include <memory>
#include <string>

namespace {

std::string grpc_port() {
    if (const char* env = std::getenv("LEDGERBULL_ENGINE_GRPC_PORT")) {
        if (*env != '\0') {
            return env;
        }
    }
    return "50051";
}

}  // namespace

int main() {
    const std::string port = grpc_port();
    const std::string log_path = ledgerbull::EventLog::default_log_path();
    const std::string listen_addr = "0.0.0.0:" + port;

    std::cout << "LedgerBull matching engine gRPC server\n";
    std::cout << "  listen: " << listen_addr << "\n";
    std::cout << "  log:    " << log_path << "\n";
    std::cout << "  port env: LEDGERBULL_ENGINE_GRPC_PORT (default 50051)\n";

    ledgerbull::MatchingEngine engine("BTC-USD", log_path, true);
    if (!engine.replay_warnings().empty()) {
        std::cout << "Replay warnings:\n";
        for (const auto& w : engine.replay_warnings()) {
            std::cout << "  - " << w << '\n';
        }
    }

    ledgerbull::grpc_server::MatchingEngineServiceImpl service(&engine);

    grpc::EnableDefaultHealthCheckService(true);
    grpc::reflection::InitProtoReflectionServerBuilderPlugin();

    grpc::ServerBuilder builder;
    builder.AddListeningPort(listen_addr, grpc::InsecureServerCredentials());
    builder.RegisterService(&service);
    std::unique_ptr<grpc::Server> server(builder.BuildAndStart());
    if (!server) {
        std::cerr << "Failed to start gRPC server on " << listen_addr << '\n';
        return 1;
    }

    std::cout << "Server ready.\n";
    server->Wait();
    return 0;
}
