#pragma once

// Tiny, dependency-free test harness: register named test cases, run them all,
// print per-test PASS/FAIL with assertion detail, and return non-zero if any fail.
// Kept intentionally minimal so sub-phase 2A needs no external test dependency.

#include <functional>
#include <iostream>
#include <string>
#include <vector>

namespace tf {

struct TestCase {
    std::string name;
    std::function<void()> fn;
};

inline std::vector<TestCase>& registry() {
    static std::vector<TestCase> r;
    return r;
}

inline int& current_failures() {
    static int f = 0;
    return f;
}

struct Registrar {
    Registrar(const std::string& name, std::function<void()> fn) {
        registry().push_back({name, std::move(fn)});
    }
};

inline int run_all() {
    int failed_tests = 0;
    for (const auto& tc : registry()) {
        current_failures() = 0;
        std::cout << "[ RUN      ] " << tc.name << "\n";
        tc.fn();
        if (current_failures() == 0) {
            std::cout << "[     PASS ] " << tc.name << "\n";
        } else {
            std::cout << "[     FAIL ] " << tc.name << " (" << current_failures()
                      << " assertion(s) failed)\n";
            ++failed_tests;
        }
    }
    std::cout << "\n========================================\n";
    std::cout << registry().size() << " tests run, " << failed_tests << " failed.\n";
    if (failed_tests == 0) {
        std::cout << "ALL TESTS PASSED\n";
    }
    return failed_tests == 0 ? 0 : 1;
}

}  // namespace tf

#define TEST_CASE(test_name)                                                    \
    static void test_name();                                                    \
    static ::tf::Registrar tf_reg_##test_name(#test_name, test_name);           \
    static void test_name()

#define CHECK(cond)                                                             \
    do {                                                                        \
        if (!(cond)) {                                                          \
            ++::tf::current_failures();                                         \
            std::cerr << "    CHECK failed: " #cond "  (" << __FILE__ << ":"     \
                      << __LINE__ << ")\n";                                      \
        }                                                                       \
    } while (0)

#define CHECK_EQ(a, b)                                                          \
    do {                                                                        \
        auto _va = (a);                                                         \
        auto _vb = (b);                                                         \
        if (!(_va == _vb)) {                                                    \
            ++::tf::current_failures();                                         \
            std::cerr << "    CHECK_EQ failed: " #a " == " #b "  (got " << _va   \
                      << " vs " << _vb << ")  (" << __FILE__ << ":" << __LINE__  \
                      << ")\n";                                                  \
        }                                                                       \
    } while (0)
