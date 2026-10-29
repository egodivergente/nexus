package com.vidadomu.nexus.core.util

/** Small synchronized LRU: bounds process lifetime caches without background jobs. */
class BoundedCache<K, V>(private var capacity: Int) {
    init { require(capacity > 0) }
    private val entries = object : LinkedHashMap<K, V>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>) = size > capacity
    }
    @Synchronized operator fun get(key: K): V? = entries[key]
    @Synchronized operator fun set(key: K, value: V) { entries[key] = value }
    @Synchronized fun getOrPut(key: K, create: () -> V): V = entries[key] ?: create().also { entries[key] = it }
    @Synchronized fun putIfAbsent(key:K,value:V):V? {val previous=entries[key];if(previous==null) entries[key]=value;return previous}
    /** Grow without losing entries; shrinking removes the least recently accessed entries first. */
    @Synchronized fun resize(capacity: Int) {
        require(capacity > 0)
        this.capacity = capacity
        val oldest = entries.entries.iterator()
        while (entries.size > capacity && oldest.hasNext()) {
            oldest.next()
            oldest.remove()
        }
    }
    @Synchronized fun clear() = entries.clear()
    @Synchronized fun size() = entries.size
}
