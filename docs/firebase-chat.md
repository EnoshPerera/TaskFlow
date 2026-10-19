# Firebase chat implementation notes

Firebase AI Logic is already connected in the source: `TaskFlowApplication.chatSession` selects `FirebaseTaskChatBackend`. For project creation, App Check, build instructions, GitHub exclusions, and model troubleshooting, start with the [README](../README.md).

The Android application ID registered in Firebase is `com.appfunctions.taskflow`. The Kotlin namespace is `com.example.taskflow`; these intentionally differ. Each developer downloads their own ignored `app/google-services.json`.

## Backend contract

`TaskChatBackend.reply(history, tools)` receives conversation messages including the latest user message and returns the assistant's text. `TaskChatSession` owns loading and error state, prevents concurrent sends, and preserves coroutine cancellation.

The current backend creates a fresh Firebase chat per turn using earlier user/assistant text, then sends the newest user message. It excludes the welcome message and errors. Tool exchanges remain in that Firebase conversation during the turn; only final text is retained for subsequent turns. If changing to a persistent SDK chat object, do not also resend the entire conversation on every turn.

The model uses `GenerativeBackend.googleAI()` and currently selects `gemini-3.6-flash`. Change `modelName` in the actual [backend source](../app/src/main/java/com/example/taskflow/chat/FirebaseTaskChatBackend.kt) to test another supported text/function-calling model. There is no automatic model fallback.

## Tool contract

| Function | Required arguments | Optional arguments | Result payload |
| --- | --- | --- | --- |
| `createTask` | `title`: string | None | `task` |
| `readTasks` | None | None | `tasks` |
| `updateTask` | `id`: string | `title`: string, `completed`: boolean | `task` |
| `deleteTask` | `id`: string | None | `deleted` |

Each task has `id`, `title`, and `completed`. An update requires at least one field. Omitted update values stay `null`, preserving existing values; an omitted `completed` must not become `false`.

The backend validates supported function names and argument types, dispatches through `TaskChatTools`, and sends actual results or structured errors back to Firebase. `AppFunctionChatTools` calls the same `TaskFunctions` implementation used by Android AppFunctions, sharing the application's repository. In-app chat does not call the Android system service.

The function loop is bounded to eight rounds. A round can contain several calls; it is not an eight-action limit. A final response arriving after the last permitted round is still returned. Exceptions during an individual operation are converted to tool errors, except cancellation, which is propagated.

Operations are not transactional. A network failure after tool execution does not undo a change. Avoid automatic retries of a whole turn because a successful mutation could be repeated.

## Instructions and state

The model is instructed to read current tasks before resolving names, use real IDs, clarify ambiguity, and mutate tasks only at the user's request. Manual edits may occur between messages. Instructions guide model behavior but are not a separate user-confirmation gate.

Tasks and messages are process-local. They survive activity recreation while the application process remains alive; process termination clears both. Firebase receives chat text and task data returned as tool results, but this project does not use a Firebase database for persistence.

The backend uses `kotlinx.serialization.json` directly; its dependency is already declared in the app. JSON objects are built manually, so no Kotlin serialization compiler plugin is required for this code.

## Local command backend

`LocalTaskChatBackend` remains available for explicit local commands. Import it and select it in `TaskFlowApplication` to use it. This does not remove the Google Services plugin or Firebase initialization, so the current build still needs Firebase client configuration. See the README for commands and limitations.

## Reference example

[`examples/FirebaseTaskChatBackend.kt.example`](examples/FirebaseTaskChatBackend.kt.example) is an illustrative snapshot, not a compiled source file. The implementation under `app/src/main/` is authoritative; do not copy the example over it as a setup step.

For SDK behavior, consult the [official Firebase function-calling guide](https://firebase.google.com/docs/ai-logic/function-calling).
