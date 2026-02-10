#pragma once
#include <atomic>
#include <cstdint>

/*
 * WritePolicy
 *
 * 定义 producer 在“没有明显空闲 slot”时的行为策略
 *
 * - OverwriteLatest:
 *     只避开 pinned slot（consumer 正在读的）
 *     published 只是“最新指针”，允许被覆盖
 *
 * - DropIfBusy:
 *     pinned + published 都视为“忙”
 *     如果没有完全空闲的 slot，直接丢帧
 */
enum class WritePolicy {
    OverwriteLatest,
    DropIfBusy
};

/*
 * PinnedSnapshot
 *
 * 一个 lock-free / wait-free（producer 侧） 的 snapshot buffer
 *
 * 典型用途：
 * - 音频 / 图形 / UI 状态可视化
 * - 传感器 / 实时状态共享
 *
 * 角色语义：
 *
 * producer：
 * - 高频调用
 * - 永不阻塞
 * - beginWrite() 失败就直接丢
 *
 * consumer：
 * - 低频或定时读取
 * - beginRead() 后占有一个 slot
 * - endRead() 前数据绝不会被覆盖
 *
 * 核心状态变量：
 *
 * - pinned:
 *     表示 consumer 正在使用该 slot
 *     == true  → producer 绝对不能写
 *
 * - writing:
 *     表示 producer 正在写该 slot
 *     只用于 producer 侧的并发保护
 *
 * - published:
 *     表示“最近一次完整写入完成”的 slot index
 *     只是一个可见性指针，不是资源锁
 */
template<typename T, WritePolicy Policy = WritePolicy::OverwriteLatest>
class PinnedSnapshot {
public:
    PinnedSnapshot() {
        // 初始化所有 slot 状态
        for (int i = 0; i < 3; ++i) {
            slots[i].writing.store(false, std::memory_order_relaxed);
            slots[i].pinned.store(false, std::memory_order_relaxed);
        }

        // -1 表示“尚未有任何成功发布的 snapshot”
        published.store(-1, std::memory_order_relaxed);
    }

    /* ============================================================
     * Producer API
     * ============================================================ */

    /*
     * beginWrite()
     *
     * 尝试获取一个可写 slot
     *
     * 行为总结：
     * - 只要 slot 没被 pinned，就“可能”可写
     * - writing 用 CAS 保护，防止多个 producer 同时写
     *
     * 策略差异：
     *
     * OverwriteLatest:
     *   - pinned == true → 跳过
     *   - published      → 允许覆盖
     *
     * DropIfBusy:
     *   - pinned == true → 跳过
     *   - published      → 也跳过
     *
     * 返回：
     * - 成功：指向可写 object 的指针
     * - 失败：nullptr（调用者应直接丢帧）
     */
    T* beginWrite() {
        // 读取当前 published，用于 DropIfBusy 策略判断
        int pub = published.load(std::memory_order_acquire);

        for (int i = 0; i < 3; ++i) {

            // 1️⃣ consumer 正在使用，绝对不能写
            if (slots[i].pinned.load(std::memory_order_acquire))
                continue;

            // 2️⃣ DropIfBusy 策略下，published 也视为“忙”
            if constexpr (Policy == WritePolicy::DropIfBusy) {
                if (i == pub)
                    continue;
            }

            // 3️⃣ CAS 抢占写入权
            //
            // expected = false：
            //   表示“当前没人写”
            //
            // CAS 成功：
            //   - 本线程获得写入权
            //   - writing 被置为 true
            bool expected = false;
            if (slots[i].writing.compare_exchange_strong(
                    expected,
                    true,
                    std::memory_order_acquire)) {

                // 成功获得一个可写 slot
                return &slots[i].object;
            }
        }

        // 没有任何 slot 可写
        // producer 必须接受丢帧
        return nullptr;
    }

    /*
     * endWrite()
     *
     * 写入完成后调用
     *
     * 必须遵守顺序：
     * 1. 先结束 writing（release）
     * 2. 再更新 published（release）
     *
     * 这样 consumer 在 acquire published 后，
     * 一定能看到完整、已写完的 object
     */
    void endWrite(T* obj) {
        int idx = indexOf(obj);
        if (idx < 0)
            return;

        // 1️⃣ 释放 writing
        //
        // memory_order_release 保证：
        // - 对 object 的所有写入
        // - 在 consumer 看到 published 前全部可见
        slots[idx].writing.store(false, std::memory_order_release);

        // 2️⃣ 发布为最新 snapshot
        //
        // published 只是“最新完成写入”的索引
        // 不是保护对象
        published.store(idx, std::memory_order_release);
    }

    /* ============================================================
     * Consumer API
     * ============================================================ */

    /*
     * beginRead()
     *
     * 行为：
     * - 读取当前 published
     * - pin 住该 slot
     * - 返回稳定可读的 snapshot
     *
     * 注意：
     * - 若尚未有任何发布，published == -1
     * - 返回 nullptr 表示“当前无有效数据”
     */
    const T* beginRead() {
        // acquire 保证：
        // - 如果看到 published = idx
        // - 那么对应 object 的写入已完成
        int idx = published.load(std::memory_order_acquire);
        if (idx < 0)
            return nullptr;

        // pin 住该 slot
        //
        // 一旦 pinned == true：
        // - producer 永远不会再写这个 slot
        slots[idx].pinned.store(true, std::memory_order_acquire);

        return &slots[idx].object;
    }

    /*
     * endRead()
     *
     * consumer 使用完 snapshot 后调用
     *
     * 行为：
     * - 解除 pinned
     * - 不影响 published
     */
    void endRead(const T* obj) {
        int idx = indexOf(obj);
        if (idx >= 0) {
            slots[idx].pinned.store(false, std::memory_order_release);
        }
    }

private:
    /*
     * Slot
     *
     * 每个 slot 表示一个独立的 snapshot 存储单元
     *
     * alignas(64):
     * - 避免 false sharing
     * - writing / pinned 的 cache line 不互相干扰
     */
    struct Slot {
        alignas(64) T object;

        // producer 是否正在写该 slot
        std::atomic<bool> writing;

        // consumer 是否正在读该 slot
        std::atomic<bool> pinned;
    };

    // 三槽设计：
    // - 1 个可能被 pinned
    // - 1 个 published / 刚写完
    // - 1 个 备用
    Slot slots[3];

    // 最近一次“完整写入完成”的 slot index
    std::atomic<int> published;

    /*
     * indexOf()
     *
     * 根据 object 指针反推出 slot index
     *
     * 使用前提：
     * - obj 必须来自 beginWrite / beginRead
     */
    int indexOf(const void* ptr) const {
        for (int i = 0; i < 3; ++i) {
            if (ptr == &slots[i].object)
                return i;
        }
        return -1;
    }
};
