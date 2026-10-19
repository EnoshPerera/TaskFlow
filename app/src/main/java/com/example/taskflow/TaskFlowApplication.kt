package com.example.taskflow

import android.app.Application
import androidx.appfunctions.service.AppFunctionConfiguration
import com.example.taskflow.appfunctions.TaskFunctions
import com.example.taskflow.data.TaskRepository
import com.example.taskflow.chat.AppFunctionChatTools
import com.example.taskflow.chat.FirebaseTaskChatBackend
import com.example.taskflow.chat.TaskChatSession
import android.content.pm.ApplicationInfo
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class TaskFlowApplication :
    Application(),
    AppFunctionConfiguration.Provider {

    val taskRepository: TaskRepository by lazy {
        TaskRepository()
    }

    val chatSession: TaskChatSession by lazy {
        TaskChatSession(
            // Use LocalTaskChatBackend instead for explicit local commands; see README.md.
            backend = FirebaseTaskChatBackend(),
            tools = AppFunctionChatTools(this, TaskFunctions(taskRepository))
        )
    }

    override fun onCreate() {
        super.onCreate()

        val isDebug =
            applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            if (isDebug) {
                DebugAppCheckProviderFactory.getInstance()
            } else {
                PlayIntegrityAppCheckProviderFactory.getInstance()
            }
        )
    }

    override val appFunctionConfiguration:
            AppFunctionConfiguration by lazy {

        AppFunctionConfiguration.Builder()
            .addEnclosingClassFactory(TaskFunctions::class.java) {
                TaskFunctions(taskRepository)
            }
            .build()
    }
}
