package com.example.taskflow.data

import androidx.appfunctions.AppFunctionSerializable

/**
 * A task stored temporarily in this application process.
 *
 * @property id Unique identifier used to update or delete the task.
 * @property title The task's title.
 * @property completed Whether the task has been completed.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class Task(
    val id: String,
    val title: String,
    val completed: Boolean
)