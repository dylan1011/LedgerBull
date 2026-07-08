#pragma once

#include "ledgerbull/order.hpp"

#include <cstdint>
#include <string>
#include <vector>

namespace ledgerbull {

/// One append-only event record (JSON-per-line on disk).
enum class EventType { SUBMIT, CANCEL };

struct EngineEvent {
    std::uint64_t index{0};
    EventType type{EventType::SUBMIT};
    OrderId order_id{0};
    std::string symbol;
    Side side{Side::BUY};
    OrderType order_type{OrderType::LIMIT};
    Price price{0};
    Quantity quantity{0};
    Sequence sequence{0};  // SUBMIT only — persisted time-priority sequence
};

/// Append-only write-ahead event log (local file). Each append is flushed before the
/// caller applies the event to the in-memory book.
class EventLog {
public:
    explicit EventLog(std::string path);

    const std::string& path() const { return path_; }
    std::uint64_t next_index() const { return next_index_; }

    /// Append a SUBMIT event and flush (write-ahead). Returns the assigned event index.
    std::uint64_t append_submit(const Order& order, Sequence sequence);

    /// Append a CANCEL event and flush (write-ahead). Returns the assigned event index.
    std::uint64_t append_cancel(OrderId order_id);

    /// Read all valid events in order. Skips a corrupt *trailing* record (crash mid-write).
    /// Warnings (if non-null) describe skipped/corrupt lines.
    std::vector<EngineEvent> load_all(std::vector<std::string>* warnings = nullptr) const;

    /// Default persistent log path (override with LEDGERBULL_ENGINE_LOG_PATH env var).
    static std::string default_log_path();

private:
    std::string path_;
    std::uint64_t next_index_{1};

    void ensure_open();
    void scan_next_index();
    static std::string checksum_for(const std::string& payload);
    static std::string serialize_submit(std::uint64_t index, const Order& order, Sequence seq);
    static std::string serialize_cancel(std::uint64_t index, OrderId order_id);
    static bool parse_line(const std::string& line, EngineEvent* out, std::string* checksum_out);
    static bool verify_checksum(const std::string& payload, const std::string& checksum);
};

}  // namespace ledgerbull
