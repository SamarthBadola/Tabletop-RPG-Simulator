#include <atomic>
#include <chrono>
#include <condition_variable>
#include <iostream>
#include <mutex>
#include <thread>

#include "pathfinder/work_queue.hpp"

using namespace pf;

static int failures = 0;
#define CHECK(...) \
    do { \
        if (!(__VA_ARGS__)) { \
            std::cerr << __FILE__ << ":" << __LINE__ << "  CHECK failed: " #__VA_ARGS__ "\n"; \
            ++failures; \
        } \
    } while (0)

// A door that jobs wait at until the test opens it.
class Gate {
public:
    void open() {
        {
            const std::lock_guard<std::mutex> lock(mutex_);
            open_ = true;
        }
        cv_.notify_all();
    }
    void wait() {
        std::unique_lock<std::mutex> lock(mutex_);
        cv_.wait(lock, [&] { return open_; });
    }

private:
    std::mutex mutex_;
    std::condition_variable cv_;
    bool open_ = false;
};

// Waits (up to 5 s) until the counter reaches the wanted value.
static bool wait_for(const std::atomic<int>& counter, int wanted) {
    for (int i = 0; i < 500; ++i) {
        if (counter >= wanted) return true;
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }
    return counter >= wanted;
}

static void test_runs_everything_when_there_is_room() {
    std::atomic<int> normal{0};
    std::atomic<int> shed{0};
    {
        WorkQueue queue(2, 100);
        for (int i = 0; i < 50; ++i) {
            CHECK(queue.submit([&](bool is_shed) { (is_shed ? shed : normal)++; }));
        }
    }  // the destructor finishes accepted jobs
    CHECK(normal == 50 && shed == 0);
}

static void test_full_line_sheds() {
    Gate gate;
    std::atomic<int> running{0};
    std::atomic<int> normal{0};
    std::atomic<int> shed{0};
    const auto job = [&](bool is_shed) {
        if (is_shed) {
            ++shed;
            return;
        }
        ++running;
        gate.wait();
        ++normal;
    };

    {
        WorkQueue queue(1, 2);                  // one worker, two may wait
        CHECK(queue.submit(job));               // taken by the worker
        CHECK(wait_for(running, 1));
        CHECK(queue.submit(job));               // waits (1 of 2)
        CHECK(queue.submit(job));               // waits (2 of 2)
        CHECK(queue.submit(job));               // line full: goes to the shed thread
        CHECK(queue.submit(job));
        CHECK(wait_for(shed, 2));               // answered at once, while the worker is still busy
        CHECK(normal == 0);
        gate.open();
    }
    CHECK(normal == 3 && shed == 2);  // the three accepted jobs all ran normally
}

static void test_no_queue_with_one_worker() {  // case C-21: one worker, no queue, concurrent requests
    Gate gate;
    std::atomic<int> running{0};
    std::atomic<int> normal{0};
    std::atomic<int> shed{0};
    const auto job = [&](bool is_shed) {
        if (is_shed) {
            ++shed;
            return;
        }
        ++running;
        gate.wait();
        ++normal;
    };
    {
        WorkQueue queue(1, 0);
        CHECK(queue.submit(job));  // a free worker takes it, even though there is no queue
        CHECK(wait_for(running, 1));
        for (int i = 0; i < 5; ++i) CHECK(queue.submit(job));  // the worker is busy and nothing may wait
        CHECK(wait_for(shed, 5));
        CHECK(normal == 0 && running == 1);
        gate.open();
    }
    CHECK(normal == 1 && shed == 5);
}

static void test_idle_workers_count_as_room() {
    // Three idle workers and no queue: three requests arriving at once are all accepted, the fourth is shed.
    Gate gate;
    std::atomic<int> running{0};
    std::atomic<int> shed{0};
    {
        WorkQueue queue(3, 0);
        const auto job = [&](bool is_shed) {
            if (is_shed) {
                ++shed;
                return;
            }
            ++running;
            gate.wait();
        };
        for (int i = 0; i < 4; ++i) CHECK(queue.submit(job));
        CHECK(wait_for(shed, 1));
        CHECK(wait_for(running, 3));
        CHECK(shed == 1);
        gate.open();
    }
}

static void test_shed_line_is_bounded() {
    Gate gate;
    std::atomic<int> blocked_shed{0};
    std::atomic<int> normal{0};
    int refused = 0;
    {
        WorkQueue queue(1, 1, 2);  // one worker, one waiting, two in the side line
        const auto job = [&](bool is_shed) {
            if (is_shed) ++blocked_shed;
            gate.wait();
            if (!is_shed) ++normal;
        };
        for (int i = 0; i < 10; ++i) {
            if (!queue.submit(job)) ++refused;
        }
        // 1 running + 1 waiting + the shed thread holds 1 and 2 wait behind it = 5 accepted at most.
        CHECK(refused >= 5);
        gate.open();
    }
    CHECK(refused + normal + blocked_shed == 10);
}

static void test_shutdown_finishes_accepted_jobs_and_refuses_new_ones() {
    std::atomic<int> done{0};
    WorkQueue queue(1, 10);
    for (int i = 0; i < 5; ++i) {
        CHECK(queue.submit([&](bool) {
            std::this_thread::sleep_for(std::chrono::milliseconds(5));
            ++done;
        }));
    }
    queue.shutdown();
    CHECK(done == 5);
    CHECK(!queue.submit([](bool) {}));
    queue.shutdown();  // calling it again is harmless
}

int main() {
    test_runs_everything_when_there_is_room();
    test_full_line_sheds();
    test_no_queue_with_one_worker();
    test_idle_workers_count_as_room();
    test_shed_line_is_bounded();
    test_shutdown_finishes_accepted_jobs_and_refuses_new_ones();

    if (failures == 0) {
        std::cout << "All work queue tests passed\n";
        return 0;
    }
    std::cerr << failures << " check(s) failed\n";
    return 1;
}
