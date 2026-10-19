package com.example.taskflow.chat

import android.content.Context
import androidx.appfunctions.AppFunctionContext
import com.example.taskflow.appfunctions.TaskFunctions
import com.example.taskflow.data.Task

/** Implement this interface with Firebase AI Logic, then select it in TaskFlowApplication. */
interface TaskChatBackend {
    val statusLabel: String
    val welcomeMessage: String

    /** History includes the latest user message. Invoke tools only when requested by the user. */
    suspend fun reply(history: List<ChatMessage>, tools: TaskChatTools): String
}

/** The same operations exposed to Android AppFunctions, available for Firebase function calls. */
interface TaskChatTools {
    fun createTask(title: String): Task
    fun readTasks(): List<Task>
    fun updateTask(id: String, title: String? = null, completed: Boolean? = null): Task
    fun deleteTask(id: String): Boolean
}

class AppFunctionChatTools(
    context: Context,
    private val functions: TaskFunctions
) : TaskChatTools {
    private val functionContext = object : AppFunctionContext {
        override val context: Context = context.applicationContext
    }

    override fun createTask(title: String) = functions.createTask(functionContext, title)
    override fun readTasks() = functions.readTasks(functionContext)
    override fun updateTask(id: String, title: String?, completed: Boolean?) =
        functions.updateTask(functionContext, id, title, completed)
    override fun deleteTask(id: String) = functions.deleteTask(functionContext, id)
}

/** An explicit command mode that works before an AI provider is connected. */
class LocalTaskChatBackend : TaskChatBackend {
    override val statusLabel = "Local commands"
    override val welcomeMessage =
        "Let’s get your tasks organized. Try “Add Buy milk” or “Show tasks”. " +
            "I’m using local commands for now."

    override suspend fun reply(history: List<ChatMessage>, tools: TaskChatTools): String {
        val input = history.last { it.isUser }.text.trim()
        if (input.equals("show tasks", true) || input.equals("list tasks", true)) {
            val tasks = tools.readTasks()
            return if (tasks.isEmpty()) "You have no tasks yet. Try “Add Buy milk”."
            else tasks.joinToString("\n\n") {
                "${if (it.completed) "✓" else "○"} ${it.title}\nID: ${it.id}"
            }
        }
        val command = input.substringBefore(' ').lowercase()
        val argument = input.substringAfter(' ', "").trim()
        return when (command) {
            "add" -> {
                require(argument.isNotBlank()) { "Add a title, for example: Add Buy milk" }
                val task = tools.createTask(argument)
                "Added “${task.title}”."
            }
            "complete", "reopen", "delete" -> {
                val task = resolveTask(argument, tools)
                when (command) {
                    "delete" -> {
                        check(tools.deleteTask(task.id)) { "The task could not be deleted." }
                        "Deleted “${task.title}”."
                    }
                    else -> {
                        val updated = tools.updateTask(task.id, completed = command == "complete")
                        if (updated.completed) "Completed “${updated.title}”."
                        else "Reopened “${updated.title}”."
                    }
                }
            }
            "rename" -> {
                val separator = argument.indexOf(" to ", ignoreCase = true)
                require(separator > 0) { "Try: Rename Buy milk to Buy oat milk" }
                val task = resolveTask(argument.substring(0, separator), tools)
                val title = argument.substring(separator + 4).trim()
                require(title.isNotBlank()) { "Please provide a new task title." }
                val updated = tools.updateTask(task.id, title = title)
                "Renamed “${task.title}” to “${updated.title}”."
            }
            else -> "Available commands:\n\nAdd Buy milk\nShow tasks\nComplete Buy milk\n" +
                "Reopen Buy milk\nRename Buy milk to Buy oat milk\nDelete Buy milk\n\n" +
                "Use the exact task title or its ID. Free-form AI chat is not connected yet."
        }
    }

    private fun resolveTask(reference: String, tools: TaskChatTools): Task {
        require(reference.isNotBlank()) { "Please include a task title or ID." }
        val tasks = tools.readTasks()
        tasks.firstOrNull { it.id == reference.trim() }?.let { return it }
        val matches = tasks.filter { it.title.equals(reference.trim(), ignoreCase = true) }
        require(matches.isNotEmpty()) { "No task matches “$reference”. Use “Show tasks” to see your list." }
        require(matches.size == 1) { "More than one task has that title. Use its ID from “Show tasks”." }
        return matches.single()
    }
}
