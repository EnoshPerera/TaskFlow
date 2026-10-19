package com.example.taskflow.chat

import com.example.taskflow.data.TaskRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class TaskChatTest {
    private class TestTools : TaskChatTools {
        val repository = TaskRepository()
        override fun createTask(title: String) = repository.create(title)
        override fun readTasks() = repository.readAll()
        override fun updateTask(id: String, title: String?, completed: Boolean?) =
            repository.update(id, title, completed)
        override fun deleteTask(id: String) = repository.delete(id)
    }

    @Test
    fun commandsCreateReadUpdateAndDeleteTheSameTask() = runBlocking {
        val tools = TestTools()
        val backend = LocalTaskChatBackend()
        suspend fun send(text: String) = backend.reply(listOf(ChatMessage(text, isUser = true)), tools)

        send("Add Buy milk")
        val task = tools.readTasks().single()
        assertFalse(task.completed)
        assertTrue(send("Show tasks").contains(task.id))
        send("Complete Buy milk")
        assertTrue(tools.readTasks().single().completed)
        send("Rename Buy milk to Buy oat milk")
        assertEquals("Buy oat milk", tools.readTasks().single().title)
        send("Reopen ${task.id}")
        assertFalse(tools.readTasks().single().completed)
        send("Delete ${task.id}")
        assertTrue(tools.readTasks().isEmpty())
    }

    @Test
    fun ambiguousOrUnknownCommandsDoNotDeleteTasks() = runBlocking {
        val tools = TestTools()
        tools.createTask("Buy milk")
        tools.createTask("Buy milk")
        val backend = LocalTaskChatBackend()
        try {
            backend.reply(listOf(ChatMessage("Delete Buy milk", isUser = true)), tools)
            fail("Duplicate titles must require an ID")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("ID"))
        }
        try {
            backend.reply(listOf(ChatMessage("delete everything", isUser = true)), tools)
            fail("Missing task must not succeed")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("No task matches"))
        }
        assertEquals(2, tools.readTasks().size)
    }

    @Test
    fun unknownTextOffersHelpWithoutChangingTasks() = runBlocking {
        val tools = TestTools()
        tools.createTask("Buy milk")
        val response = LocalTaskChatBackend().reply(
            listOf(ChatMessage("Can you organize everything?", isUser = true)), tools
        )
        assertTrue(response.contains("Available commands"))
        assertEquals(1, tools.readTasks().size)
    }

    @Test
    fun sessionRejectsDuplicateSendsWhileReplyIsPending() = runBlocking {
        val gate = CompletableDeferred<String>()
        val backend = object : TaskChatBackend {
            override val statusLabel = "Test"
            override val welcomeMessage = "Hello"
            override suspend fun reply(history: List<ChatMessage>, tools: TaskChatTools) = gate.await()
        }
        val session = TaskChatSession(backend, TestTools(), this)
        session.send("Show tasks")
        session.send("Add another task")
        assertTrue(session.state.value.isSending)
        assertEquals(2, session.state.value.messages.size)
        gate.complete("No tasks")
        yield()
        assertFalse(session.state.value.isSending)
        assertEquals("No tasks", session.state.value.messages.last().text)
    }

    @Test
    fun invalidCommandDisplaysErrorAndAllowsNextMessage() = runBlocking {
        val tools = TestTools()
        val session = TaskChatSession(LocalTaskChatBackend(), tools, this)
        session.send("Add")
        yield()
        assertTrue(session.state.value.messages.last().isError)
        assertFalse(session.state.value.isSending)
        session.send("Add Buy milk")
        yield()
        assertFalse(session.state.value.messages.last().isError)
        assertEquals("Buy milk", tools.readTasks().single().title)
    }
}
