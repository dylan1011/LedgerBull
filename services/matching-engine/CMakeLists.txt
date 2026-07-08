cmake_minimum_required(VERSION 3.15)
project(ledgerbull_matching_engine LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release CACHE STRING "Build type" FORCE)
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
