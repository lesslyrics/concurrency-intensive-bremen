@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day4

import day4.AtomicArrayWithCAS2Simplified.Status.*
import java.util.concurrent.atomic.*


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        val element = array[index]
        if (element is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            return when {
                element.status.get() == SUCCESS && element.index1 == index -> element.update1 as E
                element.status.get() == SUCCESS && element.index2 == index -> element.update2 as E
                else -> {
                    when {
                        element.index1 == index -> element.expected1 as E
                        else -> element.expected2 as E
                    }
                }
            }
        }
        return element as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = if (index1 < index2) CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        ) else CAS2Descriptor(
            index1 = index2, expected1 = expected2, update1 = update2,
            index2 = index1, expected2 = expected1, update2 = update1
        )
        descriptor.apply()
        return descriptor.status.get() === SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int, val expected1: E, val update1: E,
        val index2: Int, val expected2: E, val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        private fun install() {
            while (true) {
                val currentValue1 = array[index1]
                val currentValue2 = array[index2]

                if ((currentValue1 == expected1 || currentValue1 == this) && (currentValue2 == expected2 || currentValue2 == this)) {
                    if (array.compareAndSet(index1, expected1, this) || array.get(index1) == this) {
                        if (array.compareAndSet(index2, expected2, this) || array.get(index2) == this) {
                            return
                        }
                        array.compareAndSet(index1, this, expected1)
                    }
                } else if (currentValue1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && currentValue1 != this) {
                    currentValue1.apply()
                } else if (currentValue2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && currentValue2 != this) {
                    currentValue2.apply()
                } else {
                    status.compareAndSet(UNDECIDED, FAILED)
                    return
                }
            }
        }

        private fun updateFirst() {
            array.compareAndSet(index1, this, update1)
        }

        private fun updateSecond() {
            array.compareAndSet(index2, this, update2)
        }

        fun apply() {
            when {
                status.get() == FAILED -> {
                    array.compareAndSet(index1, this, expected1)
                    array.compareAndSet(index2, this, expected2)
                }
                status.get() == SUCCESS -> {
                    updateFirst()
                    updateSecond()
                }
                else -> {
                    install()
                    status.compareAndSet(UNDECIDED, SUCCESS)
                    if (status.get() == SUCCESS) {
                        updateFirst()
                        updateSecond()
                    }
                }
            }
        }

    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}