@file:Suppress("FoldInitializerAndIfToElvis", "DuplicatedCode")

package day3

import java.util.concurrent.atomic.*

class MSQueueWithLinearTimeNonParallelRemove<E> : QueueWithRemove<E> {
    private val headNode: AtomicReference<Node>
    private val tailNode: AtomicReference<Node>

    init {
        val dummyNode = Node(null)
        headNode = AtomicReference(dummyNode)
        tailNode = AtomicReference(dummyNode)
    }

    private fun findPrevious(node: Node): Node? {
        var currentHead = headNode.get()
        while (currentHead.nextNode.get() != node && currentHead.nextNode.get() != null) {
            currentHead = currentHead.nextNode.get()
        }

        if (currentHead.nextNode.get() != node) {
            return null
        }
        return currentHead
    }

    override fun enqueue(element: E) {
        val newNode = Node(element)
        while (true) {
            val currentTail = tailNode.get()
            val nextNode = currentTail.nextNode
            if (nextNode.compareAndSet(null, newNode)) {
                tailNode.compareAndSet(currentTail, newNode)
                if (currentTail.extractedOrRemoved) {
                    currentTail.remove()
                }
                return
            } else {
                tailNode.compareAndSet(currentTail, nextNode.get())
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currentHead = headNode.get()
            val nextNode = currentHead.nextNode.get() ?: return null
            if (headNode.compareAndSet(currentHead, nextNode) && nextNode.markExtractedOrRemoved()) {
                return nextNode.element
            }
        }
    }

    override fun remove(element: E): Boolean {
        var currentNode = headNode.get()
        while (true) {
            val nextNode = currentNode.nextNode.get()
            if (nextNode == null) return false
            currentNode = nextNode
            if (currentNode.element == element && currentNode.remove()) return true
        }
    }

    override fun validate() {
        check(tailNode.get().nextNode.get() == null) {
            "tailNode.nextNode must be null"
        }
        var currentNode = headNode.get()
        while (true) {
            if (currentNode !== headNode.get() && currentNode !== tailNode.get()) {
                check(!currentNode.extractedOrRemoved) {
                    "Removed node with element ${currentNode.element} found in the middle of this queue"
                }
            }
            currentNode = currentNode.nextNode.get() ?: break
        }
    }

    private inner class Node(
        var element: E?
    ) {
        val nextNode = AtomicReference<Node?>(null)

        private val _extractedOrRemoved = AtomicBoolean(false)
        val extractedOrRemoved
            get() =
                _extractedOrRemoved.get()

        fun markExtractedOrRemoved(): Boolean =
            _extractedOrRemoved.compareAndSet(false, true)

        fun remove(): Boolean {
            val success = markExtractedOrRemoved()
            if (this != tailNode.get()) {
                val previousNode = findPrevious(this)
                previousNode?.nextNode?.set(this.nextNode.get())
            }
            return success
        }
    }
}
