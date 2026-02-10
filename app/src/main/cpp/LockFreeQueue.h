#ifndef LOCK_FREE_QUEUE_H
#define LOCK_FREE_QUEUE_H

#include <atomic>
#include <cstddef>
#include <array>

template<typename T, size_t Capacity>
class LockFreeQueue {
    static_assert((Capacity & (Capacity - 1)) == 0,
                  "Capacity must be power of 2");

public:
    LockFreeQueue() : head(0), tail(0) {}

    bool push(const T &item) {
        auto currentTail = tail.load(std::memory_order_relaxed);
        auto nextTail = (currentTail + 1) & (Capacity - 1);

        if (nextTail == head.load(std::memory_order_acquire)) {
            return false;
        }

        buffer[currentTail] = item;
        tail.store(nextTail, std::memory_order_release);
        return true;
    }

    bool pop(T &item) {
        auto currentHead = head.load(std::memory_order_relaxed);
        if (currentHead == tail.load(std::memory_order_acquire)) {
            return false;
        }

        item = buffer[currentHead];
        head.store((currentHead + 1) & (Capacity - 1),
                   std::memory_order_release);
        return true;
    }

private:
    std::array<T, Capacity> buffer;
    std::atomic<size_t> head;
    std::atomic<size_t> tail;
};

#endif // LOCK_FREE_QUEUE_H
