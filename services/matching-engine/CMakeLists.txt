cmake_minimum_required(VERSION 3.15)
project(ledgerbull_matching_engine LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release CACHE STRING "Build type" FORCE)
endif()

# Homebrew gRPC/protobuf (macOS). Override with -DCMAKE_PREFIX_PATH=... if needed.
if(NOT DEFINED CMAKE_PREFIX_PATH OR CMAKE_PREFIX_PATH STREQUAL "")
  if(EXISTS "/opt/homebrew/opt/grpc")
    list(APPEND CMAKE_PREFIX_PATH "/opt/homebrew/opt/grpc" "/opt/homebrew/opt/protobuf")
  endif()
endif()

# --- Order book + event log library (2A/2B/2C) ---
add_library(order_book
  src/order_book.cpp
  src/event_log.cpp
  src/matching_engine.cpp)
target_include_directories(order_book PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include)
target_compile_options(order_book PRIVATE -Wall -Wextra -Wpedantic)

# --- Demos ---
add_executable(order_book_demo demo/demo.cpp)
target_link_libraries(order_book_demo PRIVATE order_book)

add_executable(crash_recovery_demo demo/crash_recovery_demo.cpp)
target_link_libraries(crash_recovery_demo PRIVATE order_book)

# --- Tests (2A + 2B + 2C) ---
enable_testing()
add_executable(order_book_tests
  tests/test_order_book.cpp
  tests/test_matching.cpp
  tests/test_event_log.cpp)
target_link_libraries(order_book_tests PRIVATE order_book)
add_test(NAME order_book_tests COMMAND order_book_tests)
set_tests_properties(order_book_tests PROPERTIES
  WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR})

# --- gRPC server (2D) — optional when gRPC is installed ---
option(LEDGERBULL_BUILD_GRPC "Build the gRPC matching engine server" ON)
if(LEDGERBULL_BUILD_GRPC)
  find_package(Protobuf CONFIG REQUIRED)
  find_package(gRPC CONFIG REQUIRED)

  set(ME_PROTO ${CMAKE_CURRENT_SOURCE_DIR}/proto/matching_engine.proto)
  set(ME_PROTO_GEN_DIR ${CMAKE_CURRENT_BINARY_DIR}/generated)
  file(MAKE_DIRECTORY ${ME_PROTO_GEN_DIR})

  set(ME_PROTO_SRCS ${ME_PROTO_GEN_DIR}/matching_engine.pb.cc)
  set(ME_PROTO_HDRS ${ME_PROTO_GEN_DIR}/matching_engine.pb.h)
  set(ME_GRPC_SRCS ${ME_PROTO_GEN_DIR}/matching_engine.grpc.pb.cc)
  set(ME_GRPC_HDRS ${ME_PROTO_GEN_DIR}/matching_engine.grpc.pb.h)

  add_custom_command(
    OUTPUT ${ME_PROTO_SRCS} ${ME_PROTO_HDRS} ${ME_GRPC_SRCS} ${ME_GRPC_HDRS}
    COMMAND protobuf::protoc
    ARGS --proto_path=${CMAKE_CURRENT_SOURCE_DIR}/proto
         --cpp_out=${ME_PROTO_GEN_DIR}
         --grpc_out=${ME_PROTO_GEN_DIR}
         --plugin=protoc-gen-grpc=$<TARGET_FILE:gRPC::grpc_cpp_plugin>
         ${ME_PROTO}
    DEPENDS ${ME_PROTO}
    COMMENT "Generating gRPC/protobuf sources from matching_engine.proto")

  add_library(matching_engine_proto STATIC
    ${ME_PROTO_SRCS}
    ${ME_GRPC_SRCS})
  target_include_directories(matching_engine_proto PUBLIC ${ME_PROTO_GEN_DIR})
  target_link_libraries(matching_engine_proto PUBLIC protobuf::libprotobuf gRPC::grpc++)

  add_executable(matching_engine_server
    src/grpc/matching_engine_service.cpp
    src/grpc/server_main.cpp)
  target_include_directories(matching_engine_server PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${ME_PROTO_GEN_DIR})
  target_link_libraries(matching_engine_server PRIVATE
    order_book
    matching_engine_proto
    gRPC::grpc++_reflection)  # required for grpcurl service discovery
  target_compile_options(matching_engine_server PRIVATE -Wall -Wextra -Wpedantic)
endif()
