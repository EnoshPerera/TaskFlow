package com.example.taskflow.data

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TaskRepository {

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())

    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    @Synchronized
    fun create(title: String): Task {
        val task = Task(
            id = UUID.randomUUID().toString(),
            title = validatedTitle(title),
            completed = false
        )

        _tasks.value = _tasks.value + task

        return task
    }

    @Synchronized
    fun readAll(): List<Task> {
        return _tasks.value.toList()
    }

    @Synchronized
    fun update(
        id: String,
        title: String?,
        completed: Boolean?
    ): Task {
        require(title != null || completed != null) {
            "Provide a title or completion status to update."
        }

        val existing = _tasks.value.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException(
                "No task exists with ID: $id"
            )

        val updated = existing.copy(
            title = title?.let(::validatedTitle) ?: existing.title,
            completed = completed ?: existing.completed
        )

        _tasks.value = _tasks.value.map { task ->
            if (task.id == id) updated else task
        }

        return updated
    }

    @Synchronized
    fun delete(id: String): Boolean {
        require(_tasks.value.any { it.id == id }) {
            "No task exists with ID: $id"
        }

        _tasks.value = _tasks.value.filterNot { it.id == id }

        return true
    }

    private fun validatedTitle(title: String): String {
        val trimmed = title.trim()

        require(trimmed.isNotEmpty()) {
            "Task title must not be blank."
        }
        return trimmed
    }
}