package com.drdisagree.teledrive.core.telegram

/**
 * A caption counts as this session's only when it matches exactly, so another device's edit still
 * gets through.
 */
class OwnWrites(private val capacity: Int = DEFAULT_CAPACITY) {

    private val captions = object : LinkedHashMap<Long, String>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, String>?) =
            size > capacity
    }
    private val deletions = object : LinkedHashMap<Long, Unit>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Unit>?) =
            size > capacity
    }

    @Synchronized
    fun wroteCaption(messageId: Long, caption: String) {
        captions.remove(messageId)
        captions[messageId] = caption
    }

    @Synchronized
    fun deleted(messageIds: Collection<Long>) {
        messageIds.forEach { deletions[it] = Unit }
    }

    @Synchronized
    fun isOwnContent(messageId: Long, caption: String?): Boolean =
        caption != null && captions[messageId] == caption

    @Synchronized
    fun deletedElsewhere(messageIds: Collection<Long>): List<Long> =
        messageIds.filter { it !in deletions }

    private companion object {
        const val DEFAULT_CAPACITY = 4096
    }
}
