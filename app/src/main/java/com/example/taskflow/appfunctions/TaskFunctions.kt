package com.example.taskflow.appfunctions

import android.annotation.SuppressLint
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.service.AppFunction
import com.example.taskflow.data.Task
import com.example.taskflow.data.TaskRepository

class TaskFunctions(
    private val repository: TaskRepository
) {

    @AppFunction(isDescribedByKDoc = true)
    fun createTask(
        @SuppressLint("RestrictedApi") appFunctionContext: AppFunctionContext,
        title: String
    ): Task = checked {
        repository.create(title)
    }

    @AppFunction(isDescribedByKDoc = true)
    fun readTasks(
        @SuppressLint("RestrictedApi") appFunctionContext: AppFunctionContext
    ): List<Task> {
        return repository.readAll()
    }

    @AppFunction(isDescribedByKDoc = true)
    fun updateTask(
        @SuppressLint("RestrictedApi") appFunctionContext: AppFunctionContext,
        id: String,
        title: String? = null,
        completed: Boolean? = null
    ): Task = checked {
        repository.update(
            id = id,
            title = title,
            completed = completed
        )
    }

    @AppFunction(isDescribedByKDoc = true)
    fun deleteTask(
        @SuppressLint("RestrictedApi") appFunctionContext: AppFunctionContext,
        id: String
    ): Boolean = checked {
        repository.delete(id)
    }

    private fun <T> checked(block: () -> T): T {
        return try {
            block()
        } catch (exception: IllegalArgumentException) {
            throw AppFunctionInvalidArgumentException(
                exception.message ?: "Invalid task argument."
            )
        }
    }
}