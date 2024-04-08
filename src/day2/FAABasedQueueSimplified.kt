package day2

import day1.*
import java.util.concurrent.atomic.*
import kotlin.math.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = AtomicReferenceArray<Any?>(100) // conceptually infinite array
    private val enqueueIndex = AtomicLong(0)
    private val dequeueIndex = AtomicLong(0)

    override fun enqueue(element: E) {
        while (true) {
            val index = enqueueIndex.getAndIncrement()
            if (infiniteArray.compareAndSet(index.toInt(), null, element)) return
        }
    }

    private fun shouldTryDequeue(): Boolean {
        while (true) {
            val enqueue = enqueueIndex.get()
            val dequeue = dequeueIndex.get()
            if (enqueue != enqueueIndex.get()) continue
            return enqueue > dequeue
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (!shouldTryDequeue()) return null
            val index = dequeueIndex.getAndIncrement()
            val element = infiniteArray.getAndSet(index.toInt(), POISONED)
            if (element != POISONED && element != null) {
                return element as E
            }
        }
    }

    override fun validate() {
        for (i in 0 until min(dequeueIndex.get().toInt(), enqueueIndex.get().toInt())) {
            check(infiniteArray[i] == null || infiniteArray[i] == POISONED) {
                "`infiniteArray[$i]` must be `null` or `POISONED` with `dequeueIndex = ${dequeueIndex.get()}` at the end of the execution"
            }
        }
        for (i in max(dequeueIndex.get().toInt(), enqueueIndex.get().toInt()) until infiniteArray.length()) {
            check(infiniteArray[i] == null || infiniteArray[i] == POISONED) {
                "`infiniteArray[$i]` must be `null` or `POISONED` with `enqueueIndex = ${enqueueIndex.get()}` at the end of the execution"
            }
        }
    }
}

private val POISONED = Any()
