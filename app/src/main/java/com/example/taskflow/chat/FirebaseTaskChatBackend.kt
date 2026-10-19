package com.example.taskflow.chat
import com.example.taskflow.data.Task
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
class FirebaseTaskChatBackend : TaskChatBackend {
    override val statusLabel = "Firebase AI"
    override val welcomeMessage = "Tell me what you’d like to do with your tasks."
    private val declarations = listOf(
        FunctionDeclaration(
            "createTask", "Create a task with a nonblank title.",
            mapOf("title" to Schema.string("The task title"))
        ),
        FunctionDeclaration("readTasks", "Read the current task list and IDs.", emptyMap()),
        FunctionDeclaration(
            "updateTask", "Update a task by ID. Supply at least one update field.",
            mapOf(
                "id" to Schema.string("Task ID returned by readTasks or createTask"),
                "title" to Schema.string("New title"),
                "completed" to Schema.boolean("True to complete, false to reopen")
            ),
            optionalParameters = listOf("title", "completed")
        ),
        FunctionDeclaration(
            "deleteTask", "Delete a task only when requested by the user.",
            mapOf("id" to Schema.string("Task ID returned by readTasks or createTask"))
        )
    )
    private val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
        modelName = "gemini-3.6-flash",
        tools = listOf(Tool.functionDeclarations(declarations)),
        systemInstruction = content {
            text(
                "You help manage TaskFlow tasks. Use tools for task operations. " +
                        "Read the current task list before resolving a task name because the user " +
                        "can also edit tasks manually. Never invent task IDs or claim success before " +
                        "a tool succeeds. Ask for clarification when names are ambiguous. " +
                        "Only create, update, or delete when requested. Tasks are temporary and " +
                        "disappear when the app process ends. Omit unchanged update fields. " +
                        "Do not automatically repeat failed mutations. Keep replies concise."
            )
        }
    )
    override suspend fun reply(history: List<ChatMessage>, tools: TaskChatTools): String {

        val messages = history.drop(1).filterNot { it.isError }
        require(messages.isNotEmpty() && messages.last().isUser)
        val chat = model.startChat(history = messages.dropLast(1).map { message ->
            content(if (message.isUser) "user" else "model") { text(message.text) }
        })
        var response = chat.sendMessage(messages.last().text)
        repeat(8) {
            val calls = response.functionCalls
            if (calls.isEmpty()) {
                return response.text?.takeIf { it.isNotBlank() }
                    ?: "No response was returned. Please try another message."
            }
            val results = calls.map { call ->
                val result = try {
                    execute(call.name, call.args, tools)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    buildJsonObject { put("error", error.message ?: "Task operation failed") }
                }
                FunctionResponsePart(call.name, result, call.id)
            }
            response = chat.sendMessage(content("user") {
                results.forEach { part(it) }
            })
        }
        // The last permitted tool response may already contain the final answer.
        if (response.functionCalls.isEmpty()) {
            return response.text ?: "Task operations finished. Check your task list."
        }
        return "I reached the action limit for this message. Check your task list before continuing."
    }
    private fun execute(name: String, args: Map<String, JsonElement>, tools: TaskChatTools): JsonObject {
        fun string(key: String, optional: Boolean = false): String? {
            val value = args[key]
            if (optional && (value == null || value is JsonNull)) return null
            require(value is JsonPrimitive && value.isString) { "$key must be a string" }
            return value.content
        }
        fun completed(): Boolean? {
            val value = args["completed"]
            if (value == null || value is JsonNull) return null
            require(value is JsonPrimitive && !value.isString && value.booleanOrNull != null) {
                "completed must be a boolean"
            }
            return value.booleanOrNull
        }
        return when (name) {
            "createTask" -> buildJsonObject {
                put("task", tools.createTask(string("title")!!).asJson())
            }
            "readTasks" -> buildJsonObject {
                put("tasks", JsonArray(tools.readTasks().map { it.asJson() }))
            }
            "updateTask" -> buildJsonObject {
                put("task", tools.updateTask(string("id")!!, string("title", true), completed()).asJson())
            }
            "deleteTask" -> buildJsonObject {
                put("deleted", tools.deleteTask(string("id")!!))
            }
            else -> error("Unknown task function: $name")
        }
    }
    private fun Task.asJson() = buildJsonObject {
        put("id", id)
        put("title", title)
        put("completed", completed)
    }
}
