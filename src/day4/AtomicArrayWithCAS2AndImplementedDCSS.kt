@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day4

import day4.AtomicArrayWithCAS2AndImplementedDCSS.Status.*
import java.util.concurrent.atomic.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2AndImplementedDCSS<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        val value = array[index]
        if (value is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor) {
            return if (value.status.get() == SUCCESS) {
                if (value.index1 == index) value.update1 as E else value.update2 as E
            } else {
                if (value.index1 == index) value.expected1 as E else value.expected2 as E
            }
        }
        return value as E
    }


    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = if (index1 < index2) {
            CAS2Descriptor(
                index1 = index1, expected1 = expected1, update1 = update1,
                index2 = index2, expected2 = expected2, update2 = update2
            )
        } else {
            CAS2Descriptor(
                index1 = index2, expected1 = expected2, update1 = update2,
                index2 = index1, expected2 = expected1, update2 = update1
            )
        }
        descriptor.apply()
        return descriptor.status.get() === SUCCESS
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = AtomicReference(UNDECIDED)

        private fun install() {
            fun processIndex(index: Int, expected: Any) {
                while (status.get() == UNDECIDED) {
                    when (val value = array[index]) {
                        is AtomicArrayWithCAS2AndImplementedDCSS<*>.CAS2Descriptor -> {
                            if (value == this) return
                            value.apply()
                        }
                        expected -> {
                            if (dcss(index, value, this, status, UNDECIDED)) return
                        }
                        else -> {
                            status.compareAndSet(UNDECIDED, FAILED)
                            return
                        }
                    }
                }
            }

            processIndex(index1, expected1)
            if (status.get() == UNDECIDED) {
                processIndex(index2, expected2)
                if (status.get() == FAILED) {
                    array.compareAndSet(index1, this, expected1)
                }
            }
        }


        fun apply() {
            if (status.get() == FAILED) {
                array.compareAndSet(index1, this, expected1)
                array.compareAndSet(index2, this, expected2)
                return
            }
            if (status.get() == SUCCESS) {
                array.compareAndSet(index1, this, update1)
                array.compareAndSet(index2, this, update2)
                return
            }
            install()
            status.compareAndSet(UNDECIDED, SUCCESS)
            if (status.get() == SUCCESS) {
                array.compareAndSet(index1, this, update1)
                array.compareAndSet(index2, this, update2)
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }

    fun dcss(
        index: Int,
        expectedCellState: Any?,
        updateCellState: Any?,
        statusReference: AtomicReference<*>,
        expectedStatus: Any?
    ): Boolean =
        if (array[index] == expectedCellState && statusReference.get() == expectedStatus) {
            array[index] = updateCellState
            true
        } else {
            false
        }
}