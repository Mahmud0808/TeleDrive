package com.drdisagree.teledrive.core.telegram

/**
 * Remembers what this session wrote to the channel, so the updates Telegram
 * sends back for those writes are not taken for changes made on another
 * device and applied again. A caption only counts as this session's when it
 * matches exactly, so another device editing the same message still gets
 * through.
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
