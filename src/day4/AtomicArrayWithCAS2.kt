@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day4

import day4.AtomicArrayWithCAS2.Status.*
import java.util.concurrent.atomic.*

// This implementation never stores `null` values.
class AtomicArrayWithCAS2<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        return when (val value = array[index]) {
            is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                if (value.status.get() == SUCCESS) {
                    if (value.index1 == index) value.update1 as E else value.update2 as E
                } else {
                    if (value.index1 == index) value.expected1 as E else value.expected2 as E
                }
            }
            is AtomicArrayWithCAS2<*>.DCSSDescriptor -> value.expected1 as E
            else -> value as E
        }
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

    fun dcss(
        index1: Int, expected1: E, update1: CAS2Descriptor,
        statusReference: AtomicReference<Status>, expectedStatus: Status
    ): Boolean {
        val descriptor = DCSSDescriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            statusReference = statusReference, expectedStatus = expectedStatus)
        descriptor.apply()
        return descriptor.status.get() === SUCCESS
    }

    inner class CAS2Descriptor(index1: Int, expected1: E, update1: E, index2: Int, expected2: E, update2: E) {
        val index1: Int
        val expected1: E
        val update1: E
        val index2: Int
        val expected2: E
        val update2: E

        val status = AtomicReference(UNDECIDED)

        init {
            if (index1 < index2) {
                this.index1 = index1
                this.expected1 = expected1
                this.update1 = update1
                this.index2 = index2
                this.expected2 = expected2
                this.update2 = update2
            }
            else if (index2 < index1) {
                this.index1 = index2
                this.expected1 = expected2
                this.update1 = update2
                this.index2 = index1
                this.expected2 = expected1
                this.update2 = update1
            }
            else {
                throw IllegalArgumentException("The indices should be different")
            }
        }


        private fun install() {
            fun processIndex(index: Int, expected: E) {
                while (status.get() == UNDECIDED) {
                    when (val value = array[index]) {
                        is AtomicArrayWithCAS2<*>.CAS2Descriptor -> {
                            if (value == this) return
                            value.apply()
                        }
                        is AtomicArrayWithCAS2<*>.DCSSDescriptor -> value.apply()
                        expected -> if (dcss(index, value as E, this, status, UNDECIDED)) return
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

    inner class DCSSDescriptor(
        val index1: Int,
        val expected1: E,
        val update1: CAS2Descriptor,
        val statusReference: AtomicReference<Status>,
        val expectedStatus: Status
    ) {
        val status = AtomicReference(UNDECIDED)

        private fun install() {
            while (status.get() == UNDECIDED) {
                val value = array[index1]
                if (value is AtomicArrayWithCAS2<*>.DCSSDescriptor && value != this) {
                    value.apply()
                } else if (value != expected1) {
                    status.compareAndSet(UNDECIDED, FAILED)
                } else if (array.compareAndSet(index1, expected1, this)) {
                    break
                }
            }
        }

        fun apply() {
            install()
            val currentStatus = statusReference.get()
            val finalStatus = if (currentStatus == expectedStatus) SUCCESS else FAILED
            status.compareAndSet(UNDECIDED, finalStatus)
            val newValue = if (status.get() == SUCCESS && currentStatus == expectedStatus) update1 else expected1
            array.compareAndSet(index1, this, newValue)
        }
    }
}