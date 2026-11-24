package it.cerrone.dailyhub

import java.time.OffsetDateTime

/**
 * Same rules as the PC: the newer edit wins, and an equal time keeps the PC's copy.
 * Chat messages are only added, so the first copy of a uid is the one that stays.
 */

data class TaskRecord(
    val uid: String,
    val title: String,
    val due: String?,
    val done: Boolean,
    val createdAt: String,
    val completedAt: String?,
    val updatedAt: String,
    val deleted: Boolean,
)

data class MessageRecord(
    val uid: String,
    val conversationId: String,
    val at: String,
    val role: String,
    val text: String,
    val updatedAt: String,
    val deleted: Boolean,
)

fun preferIncoming(localAt: String, incomingAt: String, incomingFromPc: Boolean): Boolean {
    val local = OffsetDateTime.parse(localAt)
    val incoming = OffsetDateTime.parse(incomingAt)
    if (incoming.isAfter(local)) return true
    if (incoming.isBefore(local)) return false
    return incomingFromPc
}

fun mergeTask(local: TaskRecord?, incoming: TaskRecord, incomingFromPc: Boolean): TaskRecord {
    if (local == null || preferIncoming(local.updatedAt, incoming.updatedAt, incomingFromPc)) return incoming
    return local
}

fun mergeMessage(local: MessageRecord?, incoming: MessageRecord): MessageRecord =
    local ?: incoming
