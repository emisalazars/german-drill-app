package com.emilio.deutschdrill.domain

/**
 * Session queue that interleaves topics.
 *
 * - Never serves more than [maxSameTypeInRow] items of the same type in a row
 *   (unless nothing else is left).
 * - Prefers an item whose topic differs from the previous one.
 * - Items sharing a contrast group ([groupOf]) are served back-to-back (minimal pairs).
 * - Items failed with [requeue] come back after [requeueGap] other items, or sooner if the
 *   queue would otherwise run dry, so mistakes are retried within the same session.
 */
class InterleavedQueue<T>(
    items: List<T>,
    private val typeOf: (T) -> String,
    private val topicOf: (T) -> String?,
    private val groupOf: (T) -> String? = { null },
    private val maxSameTypeInRow: Int = 3,
    private val requeueGap: Int = 3,
    private val lookahead: Int = 6,
) {
    private data class Entry<T>(val item: T, val notBefore: Int)

    private val pending = ArrayDeque(items.map { Entry(it, 0) })
    private val servedTypes = ArrayList<String>()
    private var lastTopic: String? = null
    private var lastGroup: String? = null
    private var served = 0

    val remaining: Int get() = pending.size

    fun next(): T? {
        if (pending.isEmpty()) return null
        val partner = lastGroup?.let { g ->
            pending.withIndex().firstOrNull { groupOf(it.value.item) == g && it.value.notBefore <= served }
        }
        if (partner != null) return serve(partner.index)
        val ready = pending.withIndex().filter { it.value.notBefore <= served }
        val candidates = (ready.ifEmpty { pending.withIndex().sortedBy { it.value.notBefore } }).take(lookahead)
        val typeOk = candidates.filter { !wouldBreakTypeRule(typeOf(it.value.item)) }
        val pool = typeOk.ifEmpty { candidates }
        val pick = pool.firstOrNull { topicOf(it.value.item) == null || topicOf(it.value.item) != lastTopic } ?: pool.first()
        return serve(pick.index)
    }

    private fun serve(index: Int): T {
        val item = pending.removeAt(index).item
        servedTypes.add(typeOf(item))
        lastTopic = topicOf(item)
        lastGroup = groupOf(item)
        served++
        return item
    }

    fun remove(item: T) {
        pending.removeAll { it.item == item }
    }

    fun requeue(item: T) {
        pending.addLast(Entry(item, served + requeueGap))
    }

    private fun wouldBreakTypeRule(type: String): Boolean {
        if (servedTypes.size < maxSameTypeInRow) return false
        return servedTypes.takeLast(maxSameTypeInRow).all { it == type }
    }
}
