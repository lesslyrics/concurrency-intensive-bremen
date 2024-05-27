package day6

import day1.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>() // sequential queue
    private val combinerLock = AtomicBoolean(false) // unlocked initially
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        var cellIndex = -1
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                try {
                    queue.addLast(element)
                    helpOthers()
                } finally {
                    combinerLock.set(false)
                }
                return
            } else {
                if (cellIndex == -1) {
                    cellIndex = randomCellIndex()
                }
                if (tasksForCombiner.compareAndSet(cellIndex, null, element)) {
                    while (tasksForCombiner[cellIndex] !is Result<*>) {
                        if (combinerLock.compareAndSet(false, true)) {
                            try {
                                helpOthers()
                            } finally {
                                combinerLock.set(false)
                            }
                        }
                    }
                    tasksForCombiner.getAndSet(cellIndex, null) as Result<*>
                    return
                }
            }
        }
    }

    override fun dequeue(): E? {
        var cellIndex = -1
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                try {
                    val result = queue.removeFirstOrNull()
                    helpOthers()
                    return result
                } finally {
                    combinerLock.set(false)
                }
            } else {
                if (cellIndex == -1) {
                    cellIndex = randomCellIndex()
                }
                if (tasksForCombiner.compareAndSet(cellIndex, null, Dequeue)) {
                    while (tasksForCombiner[cellIndex] !is Result<*>) {
                        if (combinerLock.compareAndSet(false, true)) {
                            try {
                                helpOthers()
                            } finally {
                                combinerLock.set(false)
                            }
                        }
                    }
                    val result = tasksForCombiner.getAndSet(cellIndex, null) as Result<*>
                    return result.value as E?
                }
            }
        }
    }

    private fun helpOthers() {
        for (i in 0 until tasksForCombiner.length()) {
            val task = tasksForCombiner[i]
            if (task != null && task !is Result<*>) {
                when (task) {
                    is Dequeue -> {
                        val result = queue.removeFirstOrNull()
                        tasksForCombiner.set(i, Result(result))
                    }
                    else -> {
                        queue.addLast(task as E)
                        tasksForCombiner.set(i, Result(null))
                    }
                }
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.length())
}

private const val TASKS_FOR_COMBINER_SIZE = 3 // Do not change this constant!

// TODO: Put this token in `tasksForCombiner` for dequeue().
// TODO: enqueue()-s should put the inserting element.
private object Dequeue

// TODO: Put the result wrapped with `Result` when the operation in `tasksForCombiner` is processed.
private class Result<V>(
    val value: V
)
