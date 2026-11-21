#pragma once

#include <condition_variable>
#include <cstddef>
#include <deque>
#include <functional>
#include <mutex>
#include <thread>
#include <utility>
#include <vector>

namespace pf {

// A fixed set of worker threads with a bounded waiting line (spec section 7, MAX_QUEUE).
//
// When the line is full a job is not dropped silently: it is handed to one extra "shed" thread
// with shed = true, so the caller can answer 503 BUSY quickly. Only when that side line is full
// too does submit() return false and the caller has to close the connection.
class WorkQueue {
public:
    using Job = std::function<void(bool shed)>;

    WorkQueue(std::size_t workers, std::size_t max_waiting, std::size_t max_shed = 64)
        : max_waiting_(max_waiting), max_shed_(max_shed) {
        for (std::size_t i = 0; i < workers; ++i) threads_.emplace_back([this] { run(waiting_, false); });
        threads_.emplace_back([this] { run(shed_, true); });
    }

    WorkQueue(const WorkQueue&) = delete;
    WorkQueue& operator=(const WorkQueue&) = delete;

    ~WorkQueue() { shutdown(); }

    // Returns false only when the job could not be accepted at all.
    bool submit(Job job) {
        {
            const std::lock_guard<std::mutex> lock(mutex_);
            if (stopping_) return false;
            if (waiting_.size() < max_waiting_) {
                waiting_.push_back(std::move(job));
            } else if (shed_.size() < max_shed_) {
                shed_.push_back(std::move(job));
            } else {
                return false;
            }
        }
        wake_.notify_all();
        return true;
    }

    // Stops taking jobs, lets every accepted job finish, then joins the threads.
    void shutdown() {
        {
            const std::lock_guard<std::mutex> lock(mutex_);
            stopping_ = true;
        }
        wake_.notify_all();
        for (std::thread& thread : threads_) {
            if (thread.joinable() && thread.get_id() != std::this_thread::get_id()) thread.join();
        }
    }

private:
    void run(std::deque<Job>& line, bool shed) {
        for (;;) {
            Job job;
            {
                std::unique_lock<std::mutex> lock(mutex_);
                wake_.wait(lock, [&] { return stopping_ || !line.empty(); });
                if (line.empty()) return;  // stopping, and nothing left to do
                job = std::move(line.front());
                line.pop_front();
            }
            job(shed);
        }
    }

    const std::size_t max_waiting_;
    const std::size_t max_shed_;
    std::mutex mutex_;
    std::condition_variable wake_;
    std::deque<Job> waiting_;
    std::deque<Job> shed_;
    bool stopping_ = false;
    std::vector<std::thread> threads_;
};

}  // namespace pf
