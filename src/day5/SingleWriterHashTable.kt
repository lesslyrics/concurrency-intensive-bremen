@file:Suppress("UNCHECKED_CAST")

package day5

import java.util.concurrent.atomic.*
import kotlin.math.*

class SingleWriterHashTable<K : Any, V : Any>(initialCapacity: Int) : HashTable<K, V> {
    private val table = AtomicReference(Table<K, V>(initialCapacity))

    override fun put(key: K, value: V): V? {
        while (true) {
            // Try to insert the key/value pair.
            table.get().put(key, value).let {
                if (it === NEEDS_RESIZE) {
                    // The current table is too small to insert a new key.
                    // Create a new table of x2 capacity,
                    // copy all elements to it,
                    // and restart the current operation.
                    resize()
                } else {
                    // The operation has been successfully performed,
                    // return the previous value associated with the key.
                    return it as V?
                }
            }
        }
    }

    override fun get(key: K): V? = table.get().get(key)

    override fun remove(key: K): V? = table.get().remove(key)

    private fun resize() {
        val curCore = table.get()
        val newTable = Table<K, V>(curCore.capacity * 2)
        repeat(curCore.capacity) { index ->
            val key = curCore.keys[index]
            val value = curCore.values[index]
            if (key != null && key != REMOVED_KEY && value != null) {
                newTable.put(key as K, value)
            }
        }
        table.set(newTable)
    }

    class Table<K : Any, V : Any>(val capacity: Int) {
        val keys = AtomicReferenceArray<Any?>(capacity)
        val values = AtomicReferenceArray<V?>(capacity)

        fun put(key: K, value: V): Any? {
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key nor an empty cell is found,
            // inform the caller that the table should be resized.
            repeat(MAX_PROBES) {
                val curKey = keys[index]
                when (curKey) {
                    key -> {
                        val oldValue = values[index]
                        values[index] = value
                        return oldValue
                    }
                    null, REMOVED_KEY -> {
                        keys[index] = key
                        values[index] = value
                        return null
                    }
                }
                index = (index + 1) % capacity
            }
            return NEEDS_RESIZE
        }

        fun get(key: K): V? {
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key is not found after that,
            // the table does not contain it.
            repeat(MAX_PROBES) {
                // Read the key.
                val curKey = keys[index]
                when (curKey) {
                    key -> {
                        return values[index]
                    }
                    null -> {
                        return null
                    }
                }
                index = (index + 1) % capacity
            }
            return null
        }

        fun remove(key: K): V? {
            var index = index(key)
            // Search for a specified key probing `MAX_PROBES` cells.
            // If neither the key is not found after that,
            // the table does not contain it.
            repeat(MAX_PROBES) {
                val curKey = keys[index]
                when (curKey) {
                    key -> {
                        val oldValue = values[index]
                        values[index] = null
                        return oldValue
                    }
                    null -> {
                        return null
                    }
                }
                index = (index + 1) % capacity
            }
            return null
        }

        private fun index(key: Any) = ((key.hashCode() * MAGIC) % capacity).absoluteValue
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val MAX_PROBES = 2 // DO NOT CHANGE THIS CONSTANT
val NEEDS_RESIZE = Any()

// TODO: Once a table cell is associated with a key,
// TODO: it should be associated with it forever.
// TODO: This way, `remove()` should only set `null` to the value slot,
// TODO: without replacing the key slot with `REMOVED_KEY`.
val REMOVED_KEY = Any()