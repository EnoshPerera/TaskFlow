package com.example.taskflow.chat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatMessage(
    val text: String,
    val isUser: Boolean = false,
    val isError: Boolean = false
)

data class TaskChatState(
    val messages: List<ChatMessage>,
    val isSending: Boolean = false
)

/** Process-lifetime chat state, matching the lifetime of the in-memory task list. */
class TaskChatSession(
    private val backend: TaskChatBackend,
    private val tools: TaskChatTools,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    val statusLabel: String get() = backend.statusLabel
    private val mutableState = MutableStateFlow(
        TaskChatState(listOf(ChatMessage(backend.welcomeMessage)))
    )
    val state = mutableState.asStateFlow()

    /** Called from the UI thread. Only one turn may run at a time. */
    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || mutableState.value.isSending) return
        val history = mutableState.value.messages + ChatMessage(message, isUser = true)
        mutableState.value = TaskChatState(history, isSending = true)
        scope.launch {
            try {
                val reply = backend.reply(history.filterNot { it.isError }, tools)
                mutableState.value = TaskChatState(history + ChatMessage(reply))
            } catch (cancelled: CancellationException) {
                mutableState.value = TaskChatState(history)
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = TaskChatState(
                    history + ChatMessage(
                        error.message ?: "Something went wrong. Please try again.",
                        isError = true
                    )
                )
            }
        }
    }
}
