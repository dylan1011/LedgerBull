#include "ledgerbull/event_log.hpp"

#include <algorithm>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <functional>
#include <sstream>
#include <stdexcept>

namespace ledgerbull {
namespace {

Side parse_side(const std::string& s) {
    if (s == "BUY") {
        return Side::BUY;
    }
    if (s == "SELL") {
        return Side::SELL;
    }
    throw std::runtime_error("invalid side: " + s);
}

OrderType parse_order_type(const std::string& s) {
    if (s == "LIMIT") {
        return OrderType::LIMIT;
    }
    if (s == "MARKET") {
        return OrderType::MARKET;
    }
    throw std::runtime_error("invalid order type: " + s);
}

std::vector<std::string> split(const std::string& line, char delim) {
    std::vector<std::string> parts;
    std::string part;
    std::istringstream ss(line);
    while (std::getline(ss, part, delim)) {
        parts.push_back(part);
    }
    return parts;
}

}  // namespace

std::string EventLog::default_log_path() {
    if (const char* env = std::getenv("LEDGERBULL_ENGINE_LOG_PATH")) {
        if (*env != '\0') {
            return env;
        }
    }
    return "./data/engine-events.log";
}

EventLog::EventLog(std::string path) : path_(std::move(path)) {
    scan_next_index();
}

void EventLog::scan_next_index() {
    next_index_ = 1;
    std::ifstream in(path_);
    if (!in) {
        return;
    }
    std::string line;
    while (std::getline(in, line)) {
        if (line.empty()) {
            continue;
        }
        EngineEvent ev;
        std::string checksum;
        if (parse_line(line, &ev, &checksum)) {
            next_index_ = std::max(next_index_, ev.index + 1);
        }
    }
}

std::string EventLog::checksum_for(const std::string& payload) {
    return std::to_string(std::hash<std::string>{}(payload));
}

void EventLog::append_line(const std::string& line) {
    std::ofstream out(path_, std::ios::app);
    if (!out) {
        throw std::runtime_error("failed to open event log for append: " + path_);
    }
    out << line << '\n';
    // Write-ahead durability: flush before the book is mutated (see append_submit).
    out.flush();
    if (!out) {
        throw std::runtime_error("failed to flush event log: " + path_);
    }
}

std::string EventLog::serialize_submit(std::uint64_t index, const Order& order, Sequence seq) {
    std::ostringstream payload;
    payload << index << "|SUBMIT|" << order.order_id << '|' << order.symbol << '|'
            << to_string(order.side) << '|' << to_string(order.type) << '|' << order.price
            << '|' << order.quantity << '|' << seq;
    const std::string cs = checksum_for(payload.str());
    return payload.str() + '|' + cs;
}

std::string EventLog::serialize_cancel(std::uint64_t index, OrderId order_id) {
    std::ostringstream payload;
    payload << index << "|CANCEL|" << order_id;
    const std::string cs = checksum_for(payload.str());
    return payload.str() + '|' + cs;
}

void EventLog::ensure_open() {
    const auto parent = std::filesystem::path(path_).parent_path();
    if (!parent.empty()) {
        std::filesystem::create_directories(parent);
    }
}

std::uint64_t EventLog::append_submit(const Order& order, Sequence sequence) {
    ensure_open();
    const std::uint64_t index = next_index_++;
    append_line(serialize_submit(index, order, sequence));
    return index;
}

std::uint64_t EventLog::append_cancel(OrderId order_id) {
    ensure_open();
    const std::uint64_t index = next_index_++;
    append_line(serialize_cancel(index, order_id));
    return index;
}

bool EventLog::parse_line(const std::string& line, EngineEvent* out, std::string* checksum_out) {
    const auto parts = split(line, '|');
    if (parts.size() < 3) {
        return false;
    }

    try {
        out->index = static_cast<std::uint64_t>(std::stoull(parts[0]));
        const std::string& type = parts[1];
        if (type == "SUBMIT") {
            if (parts.size() != 10) {
                return false;
            }
            out->type = EventType::SUBMIT;
            out->order_id = static_cast<OrderId>(std::stoull(parts[2]));
            out->symbol = parts[3];
            out->side = parse_side(parts[4]);
            out->order_type = parse_order_type(parts[5]);
            out->price = static_cast<Price>(std::stoll(parts[6]));
            out->quantity = static_cast<Quantity>(std::stoll(parts[7]));
            out->sequence = static_cast<Sequence>(std::stoull(parts[8]));
            *checksum_out = parts[9];

            std::ostringstream payload;
            payload << parts[0] << '|' << parts[1] << '|' << parts[2] << '|' << parts[3]
                    << '|' << parts[4] << '|' << parts[5] << '|' << parts[6] << '|'
                    << parts[7] << '|' << parts[8];
            return checksum_for(payload.str()) == *checksum_out;
        }
        if (type == "CANCEL") {
            if (parts.size() != 4) {
                return false;
            }
            out->type = EventType::CANCEL;
            out->order_id = static_cast<OrderId>(std::stoull(parts[2]));
            *checksum_out = parts[3];

            std::ostringstream payload;
            payload << parts[0] << '|' << parts[1] << '|' << parts[2];
            return checksum_for(payload.str()) == *checksum_out;
        }
    } catch (const std::exception&) {
        return false;
    }
    return false;
}

std::vector<EngineEvent> EventLog::load_all(std::vector<std::string>* warnings) const {
    std::vector<EngineEvent> events;
    std::ifstream in(path_);
    if (!in) {
        return events;
    }

    std::vector<std::string> lines;
    std::string line;
    while (std::getline(in, line)) {
        if (!line.empty()) {
            lines.push_back(line);
        }
    }

    for (std::size_t i = 0; i < lines.size(); ++i) {
        EngineEvent ev;
        std::string checksum;
        if (!parse_line(lines[i], &ev, &checksum)) {
            if (i + 1 == lines.size()) {
                if (warnings) {
                    warnings->push_back("skipped corrupt trailing log record (line " +
                                         std::to_string(i + 1) + ")");
                }
                break;
            }
            throw std::runtime_error("corrupt event log record at line " +
                                     std::to_string(i + 1));
        }
        events.push_back(ev);
    }
    return events;
}

}  // namespace ledgerbull
