package com.example.taskflow

import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.app.appsearch.GenericDocument
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.taskflow.appfunctions.TaskFunctionsIds
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskFunctionsIntegrationTest {

    private val context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private val manager: AppFunctionManager
        get() = checkNotNull(
            context.getSystemService(AppFunctionManager::class.java)
        ) {
            "This device does not provide AppFunctions."
        }

    private val returnKey =
        ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE

    @Test
    fun createReadUpdateDeleteThroughAndroid() = runBlocking {
        val ids = listOf(
            TaskFunctionsIds.CREATE_TASK_ID,
            TaskFunctionsIds.READ_TASKS_ID,
            TaskFunctionsIds.UPDATE_TASK_ID,
            TaskFunctionsIds.DELETE_TASK_ID
        )

        // Give Android time to index a newly installed app.
        withTimeout(20_000) {
            for (id in ids) {
                while (true) {
                    val enabled = try {
                        isEnabled(id)
                    } catch (_: IllegalArgumentException) {
                        null
                    }

                    if (enabled != null) {
                        assertTrue("Function is disabled: $id", enabled)
                        break
                    }

                    delay(300)
                }
            }
        }

        withTimeout(20_000) {
            val createResult = invoke(
                TaskFunctionsIds.CREATE_TASK_ID,
                parameters().setPropertyString(
                    "title",
                    "Learn AppFunctions"
                ).build()
            )

            val created = checkNotNull(
                createResult.getPropertyDocument(returnKey)
            )

            val taskId = checkNotNull(
                created.getPropertyString("id")
            )

            assertEquals(
                "Learn AppFunctions",
                created.getPropertyString("title")
            )
            assertFalse(created.getPropertyBoolean("completed"))

            val readResult = invoke(
                TaskFunctionsIds.READ_TASKS_ID
            )

            val tasks = readResult
                .getPropertyDocumentArray(returnKey)
                .orEmpty()

            assertTrue(
                tasks.any { it.getPropertyString("id") == taskId }
            )

            val updateResult = invoke(
                TaskFunctionsIds.UPDATE_TASK_ID,
                parameters()
                    .setPropertyString("id", taskId)
                    .setPropertyString(
                        "title",
                        "AppFunctions checkpoint complete"
                    )
                    .setPropertyBoolean("completed", true)
                    .build()
            )

            val updated = checkNotNull(
                updateResult.getPropertyDocument(returnKey)
            )

            assertEquals(
                "AppFunctions checkpoint complete",
                updated.getPropertyString("title")
            )
            assertTrue(updated.getPropertyBoolean("completed"))

            val deleteResult = invoke(
                TaskFunctionsIds.DELETE_TASK_ID,
                parameters()
                    .setPropertyString("id", taskId)
                    .build()
            )

            assertTrue(deleteResult.getPropertyBoolean(returnKey))

            val remaining = invoke(
                TaskFunctionsIds.READ_TASKS_ID
            ).getPropertyDocumentArray(returnKey).orEmpty()

            assertFalse(
                remaining.any {
                    it.getPropertyString("id") == taskId
                }
            )
        }
    }

    private fun parameters() =
        GenericDocument.Builder<GenericDocument.Builder<*>>(
            "",
            "",
            ""
        )

    private suspend fun isEnabled(id: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            manager.isAppFunctionEnabled(
                id,
                context.mainExecutor,
                object : OutcomeReceiver<Boolean, Exception> {
                    override fun onResult(result: Boolean) {
                        continuation.resume(result)
                    }

                    override fun onError(error: Exception) {
                        continuation.resumeWithException(error)
                    }
                }
            )
        }

    private suspend fun invoke(
        id: String,
        arguments: GenericDocument = parameters().build()
    ): GenericDocument = suspendCancellableCoroutine { continuation ->
        val cancellation = CancellationSignal()

        continuation.invokeOnCancellation {
            cancellation.cancel()
        }

        val request = ExecuteAppFunctionRequest.Builder(
            context.packageName,
            id
        )
            .setParameters(arguments)
            .build()

        manager.executeAppFunction(
            request,
            context.mainExecutor,
            cancellation,
            object : OutcomeReceiver<
                    ExecuteAppFunctionResponse,
                    AppFunctionException
                    > {
                override fun onResult(
                    result: ExecuteAppFunctionResponse
                ) {
                    continuation.resume(result.resultDocument)
                }

                override fun onError(
                    error: AppFunctionException
                ) {
                    continuation.resumeWithException(error)
                }
            }
        )
    }
}
