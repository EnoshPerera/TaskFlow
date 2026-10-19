package com.example.taskflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.taskflow.data.Task
import com.example.taskflow.data.TaskRepository
import com.example.taskflow.chat.TaskChatSession
import com.example.taskflow.chat.TaskChatSheet
import com.example.taskflow.ui.theme.TaskFlowTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val taskFlow = application as TaskFlowApplication

        setContent {
            TaskFlowTheme {
                TaskFlowScreen(taskFlow.taskRepository, taskFlow.chatSession)
            }
        }
    }
}

@Composable
private fun TaskFlowScreen(repository: TaskRepository, chatSession: TaskChatSession) {
    val tasks by repository.tasks.collectAsStateWithLifecycle()
    var chatOpen by rememberSaveable { mutableStateOf(false) }
    var chatDraft by rememberSaveable { mutableStateOf("") }
    var addTaskOpen by rememberSaveable { mutableStateOf(false) }
    var editingTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val selectedTask = tasks.firstOrNull { it.id == selectedTaskId }

    BackHandler(enabled = selectedTaskId != null) { selectedTaskId = null }
    LaunchedEffect(selectedTaskId, selectedTask) {
        if (selectedTaskId != null && selectedTask == null) {
            editingTaskId = null
            selectedTaskId = null
        }
    }

    fun changeTask(action: () -> Unit) {
        try {
            action()
        } catch (error: IllegalArgumentException) {
            // A task may have been changed or deleted through Chat or AppFunctions.
            scope.launch {
                snackbarHostState.showSnackbar(error.message ?: "Unable to change this task.")
            }
        }
    }

    if (addTaskOpen) {
        TaskTitleDialog(
            dialogTitle = "Add a task",
            confirmLabel = "Add",
            onSave = { title ->
                repository.create(title)
                addTaskOpen = false
            },
            onDismiss = { addTaskOpen = false }
        )
    }

    val editingTask = tasks.firstOrNull { it.id == editingTaskId }
    if (editingTask != null) {
        key(editingTask.id) {
            TaskTitleDialog(
                dialogTitle = "Edit task",
                confirmLabel = "Save",
                initialTitle = editingTask.title,
                onSave = { title ->
                    changeTask {
                        repository.update(editingTask.id, title = title, completed = null)
                    }
                    editingTaskId = null
                },
                onDismiss = { editingTaskId = null }
            )
        }
    }

    if (chatOpen) {
        TaskChatSheet(
            session = chatSession,
            draft = chatDraft,
            onDraftChange = { chatDraft = it },
            onDismiss = { chatOpen = false }
        )
    }

    if (selectedTask != null) {
        TaskDetailScreen(
            task = selectedTask,
            snackbarHostState = snackbarHostState,
            onBack = { selectedTaskId = null },
            onCompletedChange = { completed ->
                changeTask {
                    repository.update(selectedTask.id, title = null, completed = completed)
                }
            },
            onEdit = { editingTaskId = selectedTask.id },
            onDelete = {
                changeTask { repository.delete(selectedTask.id) }
                editingTaskId = null
                selectedTaskId = null
            }
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { chatOpen = true },
                icon = { Icon(painterResource(R.drawable.ic_chat), contentDescription = null) },
                text = { Text("Chat") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "TaskFlow",
                style = MaterialTheme.typography.headlineLarge
            )

            Text(
                text = "${tasks.size} tasks · " +
                        "${tasks.count { it.completed }} completed",
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = "Tasks are stored in memory and disappear " +
                        "when the app process ends.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = { addTaskOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add task")
            }

            Text(
                text = "Tick a task to complete it. Tap its name to view details.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (tasks.isEmpty()) {
                Text(
                    text = "No tasks yet. Tap Add task or open Chat to get started.",
                    modifier = Modifier.padding(top = 24.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = tasks,
                        key = { it.id }
                    ) { task ->
                        TaskCard(
                            task = task,
                            onCompletedChange = { completed ->
                                changeTask {
                                    repository.update(task.id, title = null, completed = completed)
                                }
                            },
                            onOpen = { selectedTaskId = task.id }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskTitleDialog(
    dialogTitle: String,
    confirmLabel: String,
    initialTitle: String = "",
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    val focusRequester = remember { FocusRequester() }
    val submit = {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isNotEmpty()) onSave(trimmedTitle)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                label = { Text("Task title") },
                placeholder = { Text("Buy groceries") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submit() })
            )
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = title.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun TaskCard(
    task: Task,
    onCompletedChange: (Boolean) -> Unit,
    onOpen: () -> Unit
) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = task.completed,
                onCheckedChange = onCompletedChange,
                modifier = Modifier.semantics {
                    contentDescription = if (task.completed) "Reopen ${task.title}"
                    else "Mark ${task.title} complete"
                }
            )
            Text(
                text = task.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
            )
        }
    }
}

@Composable
private fun TaskDetailScreen(
    task: Task,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onCompletedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            TextButton(onClick = onBack) { Text("Back to tasks") }
            Text("Task details", style = MaterialTheme.typography.labelLarge)
            Text(
                text = task.title,
                style = MaterialTheme.typography.headlineLarge,
                textDecoration = if (task.completed) {
                    TextDecoration.LineThrough
                } else {
                    TextDecoration.None
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = task.completed,
                        role = Role.Checkbox,
                        onValueChange = onCompletedChange
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(checked = task.completed, onCheckedChange = null)
                Text(
                    text = if (task.completed) "Completed · tap to reopen" else "Mark complete",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit") }
                Button(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            }
        }
    }
}
