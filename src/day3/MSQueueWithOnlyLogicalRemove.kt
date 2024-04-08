package day3

import day1.MSQueue
import java.util.concurrent.atomic.*

class MSQueueWithOnlyLogicalRemove<E> : QueueWithRemove<E> {
    private val head: AtomicReference<Node<E>>
    private val tail: AtomicReference<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    override fun enqueue(element: E) {
        val newNode = Node(element)
        while (true) {
            val currentTail = tail.get()
            val nextNode = currentTail.next
            if (nextNode.compareAndSet(null, newNode)) {
                tail.compareAndSet(currentTail, newNode)
                return
            } else {
                tail.compareAndSet(currentTail, nextNode.get())
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currentHead = head.get()
            val nextNode = currentHead.next.get() ?: return null
            if (head.compareAndSet(currentHead, nextNode)) {
                if (!nextNode.markExtractedOrRemoved()) {
                    continue
                }
                val element = nextNode.element
                nextNode.element = null
                return element
            }
        }
    }

    override fun remove(element: E): Boolean {
        var currentNode = head.get()
        while (true) {
            val nextNode = currentNode.next.get() ?: return false
            currentNode = nextNode
            if (currentNode.element == element && currentNode.remove()) return true
        }
    }

    /**
     * This is an internal function for tests.
     * DO NOT CHANGE THIS CODE.
     */
    override fun validate() {
        // In this version, we allow storing
        // removed elements in the linked list.
        check(tail.get().next.get() == null) {
            "`tail.next` must be `null`"
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = AtomicReference<Node<E>?>(null)

        /**
         * TODO: Both [dequeue] and [remove] should mark
         * TODO: nodes as "extracted or removed".
         */
        private val _extractedOrRemoved = AtomicBoolean(false)
        val extractedOrRemoved
            get() =
                _extractedOrRemoved.get()

        fun markExtractedOrRemoved(): Boolean =
            _extractedOrRemoved.compareAndSet(false, true)

        /**
         * Removes this node from the queue structure.
         * Returns `true` if this node was successfully
         * removed, or `false` if it has already been
         * removed by [remove] or extracted by [dequeue].
         */
        fun remove(): Boolean = markExtractedOrRemoved()

    }
}