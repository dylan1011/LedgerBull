cmake_minimum_required(VERSION 3.15)
project(ledgerbull_matching_engine LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release CACHE STRING "Build type" FORCE)
endif()

# --- Order book library (2A: data structure, 2B: matching) ---
add_library(order_book src/order_book.cpp)
target_include_directories(order_book PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include)
target_compile_options(order_book PRIVATE -Wall -Wextra -Wpedantic)

# --- Demo executable ---
add_executable(order_book_demo demo/demo.cpp)
target_link_libraries(order_book_demo PRIVATE order_book)

# --- Tests (2A book tests + 2B matching tests) ---
enable_testing()
add_executable(order_book_tests tests/test_order_book.cpp tests/test_matching.cpp)
target_link_libraries(order_book_tests PRIVATE order_book)
add_test(NAME order_book_tests COMMAND order_book_tests)
