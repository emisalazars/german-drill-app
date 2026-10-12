package com.emilio.deutschdrill.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InterleavedQueueTest {
    data class Item(val id: Int, val type: String, val topic: String, val group: String? = null)

    private fun queue(items: List<Item>) = InterleavedQueue(items, { it.type }, { it.topic }, { it.group })

    private fun drain(q: InterleavedQueue<Item>): List<Item> = generateSequence { q.next() }.toList()

    @Test
    fun `never more than three of the same type in a row when avoidable`() {
        val items = (1..8).map { Item(it, "fill", "t$it") } + (9..11).map { Item(it, "vocab", "v$it") }
        val order = drain(queue(items))
        assertEquals(11, order.size)
        val firstNine = order.take(9).map { it.type }
        for (i in 0..firstNine.size - 4) {
            assertTrue("run too long at $i: $firstNine", firstNine.subList(i, i + 4).toSet().size > 1)
        }
    }

    @Test
    fun `consecutive topics are avoided`() {
        val items = listOf(Item(1, "a", "x"), Item(2, "b", "x"), Item(3, "c", "y"))
        val order = drain(queue(items)).map { it.id }
        assertEquals(listOf(1, 3, 2), order)
    }

    @Test
    fun `failed item returns later in the same session`() {
        val items = (1..6).map { Item(it, "t${it % 3}", "x$it") }
        val q = queue(items)
        val first = q.next()!!
        q.requeue(first)
        val rest = drain(q)
        val pos = rest.indexOf(first)
        assertTrue("requeued item should come back", pos >= 0)
        assertTrue("requeued item should not come back immediately: $pos", pos >= 3)
    }

    @Test
    fun `failed item comes back even when the queue is otherwise empty`() {
        val q = queue(listOf(Item(1, "a", "x")))
        val item = q.next()!!
        q.requeue(item)
        assertEquals(item, q.next())
        assertEquals(null, q.next())
    }

    @Test
    fun `contrast pairs are served back to back`() {
        val items = listOf(
            Item(1, "fill", "a", "mochte"), Item(2, "vocab", "b"), Item(3, "vocab", "c"),
            Item(4, "fill", "a", "mochte"),
        )
        val order = drain(queue(items)).map { it.id }
        assertEquals(1, order[0])
        assertEquals(4, order[1])
    }
}
