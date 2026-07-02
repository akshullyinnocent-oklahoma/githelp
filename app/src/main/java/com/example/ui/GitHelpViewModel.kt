package com.example.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.database.*
import com.example.network.ApiClient
import com.example.network.AgentParser
import com.example.network.ChatCompletionMessage
import com.example.network.SystemPrompts
import com.example.network.ToolCall
import com.example.terminal.TerminalExecutor
import com.example.workspace.CheckpointManager
import com.example.workspace.WorkspaceManager
import com.example.workspace.Decompiler
import com.example.workspace.InspectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException

class GitHelpViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "GitHelpViewModel"
    private val repository: GitHelpRepository
    private val httpClient = OkHttpClient()

    // Database flow bindings
    val settingsState: StateFlow<SettingsEntity>
    val sessionsState: StateFlow<List<ChatSessionEntity>>
    val skillsState: StateFlow<List<SkillUploadEntity>>
    val mcpServersState: StateFlow<List<McpServerEntity>>
    val memoriesState: StateFlow<List<MemoryEntity>>

    // UI Local State
    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()

    val currentMessages: StateFlow<List<ChatMessageEntity>> = _currentSessionId
        .flatMapLatest { id ->
            if (id != null) repository.getMessagesForSession(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Model and Connection Test states
    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _connectionTestResult = MutableStateFlow<String?>(null)
    val connectionTestResult: StateFlow<String?> = _connectionTestResult.asStateFlow()

    private val _fetchedModels = MutableStateFlow<List<String>>(emptyList())
    val fetchedModels: StateFlow<List<String>> = _fetchedModels.asStateFlow()

    private val _isFetchingModels = MutableStateFlow(false)
    val isFetchingModels: StateFlow<Boolean> = _isFetchingModels.asStateFlow()

    // Autonomous Loop Status
    private val _autonomousStatusText = MutableStateFlow("Idle")
    val autonomousStatusText: StateFlow<String> = _autonomousStatusText.asStateFlow()

    private val _autonomousStepCount = MutableStateFlow(0)
    val autonomousStepCount: StateFlow<Int> = _autonomousStepCount.asStateFlow()

    private val _activeWorkspaceFiles = MutableStateFlow<List<String>>(emptyList())
    val activeWorkspaceFiles: StateFlow<List<String>> = _activeWorkspaceFiles.asStateFlow()

    private val _isStoragePermissionGranted = MutableStateFlow(false)
    val isStoragePermissionGranted: StateFlow<Boolean> = _isStoragePermissionGranted.asStateFlow()

    private val _inspectedFileResult = MutableStateFlow<InspectionResult?>(null)
    val inspectedFileResult: StateFlow<InspectionResult?> = _inspectedFileResult.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = GitHelpRepository(database.databaseDao())

        // Initialize flows
        settingsState = repository.settingsFlow
            .map { it ?: SettingsEntity() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsEntity())

        sessionsState = repository.allSessionsFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        skillsState = repository.allSkillsFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        mcpServersState = repository.allMcpServersFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        memoriesState = repository.allMemoriesFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Create default session if none exists on startup
        viewModelScope.launch {
            val sessions = repository.allSessionsFlow.firstOrNull() ?: emptyList()
            if (sessions.isEmpty()) {
                val defaultId = repository.insertSession(
                    ChatSessionEntity(title = "New GitHelp Session")
                )
                _currentSessionId.value = defaultId
            } else {
                _currentSessionId.value = sessions.first().id
            }
            refreshWorkspaceFileList()
        }
    }

    fun selectSession(sessionId: Long) {
        _currentSessionId.value = sessionId
    }

    fun createNewSession() {
        viewModelScope.launch {
            val id = repository.insertSession(ChatSessionEntity(title = "Session ${System.currentTimeMillis() / 1000}"))
            _currentSessionId.value = id
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            val remaining = repository.allSessionsFlow.firstOrNull() ?: emptyList()
            if (remaining.isNotEmpty()) {
                _currentSessionId.value = remaining.first().id
            } else {
                createNewSession()
            }
        }
    }

    // Settings Updating
    fun updateApiKey(provider: String, key: String) {
        viewModelScope.launch {
            val curr = repository.getSettings()
            val updated = when (provider) {
                "Google" -> curr.copy(googleApiKey = key)
                "OpenRouter" -> curr.copy(openRouterApiKey = key)
                "Nvidia NIM" -> curr.copy(nvidiaApiKey = key)
                "Mistral" -> curr.copy(mistralApiKey = key)
                "DeepSeek" -> curr.copy(deepSeekApiKey = key)
                "Qwen" -> curr.copy(qwenApiKey = key)
                else -> curr
            }
            repository.updateSettings(updated)
        }
    }

    fun updateSelectedProviderAndModel(provider: String, model: String) {
        viewModelScope.launch {
            val curr = repository.getSettings()
            val updated = when (provider) {
                "Google" -> curr.copy(selectedProvider = provider, googleModel = model)
                "OpenRouter" -> curr.copy(selectedProvider = provider, openRouterModel = model)
                "Nvidia NIM" -> curr.copy(selectedProvider = provider, nvidiaModel = model)
                "Mistral" -> curr.copy(selectedProvider = provider, mistralModel = model)
                "DeepSeek" -> curr.copy(selectedProvider = provider, deepSeekModel = model)
                "Qwen" -> curr.copy(selectedProvider = provider, qwenModel = model)
                "Ollama" -> curr.copy(selectedProvider = provider, ollamaModel = model)
                else -> curr.copy(selectedProvider = provider)
            }
            repository.updateSettings(updated)
        }
    }

    fun updateOllamaUrl(url: String) {
        viewModelScope.launch {
            val curr = repository.getSettings()
            repository.updateSettings(curr.copy(ollamaUrl = url))
        }
    }

    fun updateSystemPrompt(prompt: String) {
        viewModelScope.launch {
            val curr = repository.getSettings()
            repository.updateSettings(curr.copy(customSystemPrompt = prompt))
        }
    }

    fun updateGithubPat(pat: String) {
        viewModelScope.launch {
            val curr = repository.getSettings()
            repository.updateSettings(curr.copy(githubPat = pat))
        }
    }

    fun updateHuggingfacePat(pat: String) {
        viewModelScope.launch {
            val curr = repository.getSettings()
            repository.updateSettings(curr.copy(huggingfacePat = pat))
        }
    }

    fun toggleAmoledTheme(amoled: Boolean) {
        viewModelScope.launch {
            val curr = repository.getSettings()
            repository.updateSettings(curr.copy(isAmoledTheme = amoled))
        }
    }

    // Connection testing
    fun testProviderConnection(provider: String) {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionTestResult.value = "Testing connection to $provider..."
            try {
                val settings = repository.getSettings()
                val key = when (provider) {
                    "Google" -> settings.googleApiKey
                    "OpenRouter" -> settings.openRouterApiKey
                    "Nvidia NIM" -> settings.nvidiaApiKey
                    "Mistral" -> settings.mistralApiKey
                    "DeepSeek" -> settings.deepSeekApiKey
                    "Qwen" -> settings.qwenApiKey
                    "Ollama" -> ""
                    else -> ""
                }
                val model = when (provider) {
                    "Google" -> settings.googleModel
                    "OpenRouter" -> settings.openRouterModel
                    "Nvidia NIM" -> settings.nvidiaModel
                    "Mistral" -> settings.mistralModel
                    "DeepSeek" -> settings.deepSeekModel
                    "Qwen" -> settings.qwenModel
                    "Ollama" -> settings.ollamaModel
                    else -> ""
                }

                if (provider != "Ollama" && key.isBlank()) {
                    _connectionTestResult.value = "Failed: API Key for $provider is missing!"
                    _isTestingConnection.value = false
                    return@launch
                }

                val ok = ApiClient.testConnection(
                    provider = provider,
                    apiKey = key,
                    model = model,
                    customUrl = if (provider == "Ollama") settings.ollamaUrl else ""
                )

                if (ok) {
                    _connectionTestResult.value = "Success! Connected to $provider using $model."
                } else {
                    _connectionTestResult.value = "Failed: Connection to $provider timed out or returned an error."
                }
            } catch (e: Exception) {
                _connectionTestResult.value = "Error: ${e.message}"
            } finally {
                _isTestingConnection.value = false
            }
        }
    }

    // Model Fetching
    fun fetchProviderModels(provider: String) {
        viewModelScope.launch {
            _isFetchingModels.value = true
            try {
                val settings = repository.getSettings()
                val key = when (provider) {
                    "Google" -> settings.googleApiKey
                    "OpenRouter" -> settings.openRouterApiKey
                    "Nvidia NIM" -> settings.nvidiaApiKey
                    "Mistral" -> settings.mistralApiKey
                    "DeepSeek" -> settings.deepSeekApiKey
                    "Qwen" -> settings.qwenApiKey
                    else -> ""
                }
                val models = ApiClient.fetchModels(
                    provider = provider,
                    apiKey = key,
                    customUrl = if (provider == "Ollama") settings.ollamaUrl else ""
                )
                _fetchedModels.value = models
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching models", e)
            } finally {
                _isFetchingModels.value = false
            }
        }
    }

    // Skills Upload and Management
    fun addSkill(name: String, content: String) {
        viewModelScope.launch {
            repository.insertSkill(SkillUploadEntity(name = name, content = content, isEnabled = true))
        }
    }

    fun toggleSkill(skill: SkillUploadEntity) {
        viewModelScope.launch {
            repository.updateSkill(skill.copy(isEnabled = !skill.isEnabled))
        }
    }

    fun deleteSkill(skillId: Long) {
        viewModelScope.launch {
            repository.deleteSkillById(skillId)
        }
    }

    // MCP Server Management
    fun addMcpServer(name: String, url: String) {
        viewModelScope.launch {
            repository.insertMcpServer(McpServerEntity(name = name, url = url, isEnabled = true))
        }
    }

    fun toggleMcpServer(server: McpServerEntity) {
        viewModelScope.launch {
            repository.updateMcpServer(server.copy(isEnabled = !server.isEnabled))
        }
    }

    fun deleteMcpServer(serverId: Long) {
        viewModelScope.launch {
            repository.deleteMcpServerById(serverId)
        }
    }

    fun deleteMemoryByKey(key: String) {
        viewModelScope.launch {
            repository.deleteMemoryByKey(key)
        }
    }

    // Workspace Files refresh
    fun refreshWorkspaceFileList() {
        val app = getApplication<Application>()
        _activeWorkspaceFiles.value = WorkspaceManager.listFiles(app)
    }

    // Import / Export SAF
    fun importWorkspaceFromSaf(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            WorkspaceManager.importFromSaf(app, uri)
            refreshWorkspaceFileList()
        }
    }

    fun exportWorkspaceToSaf(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            WorkspaceManager.exportToSaf(app, uri)
        }
    }

    // Direct local storage import and permission methods (using MANAGE_EXTERNAL_STORAGE)
    fun checkStoragePermission(context: Context) {
        _isStoragePermissionGranted.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            read && write
        }
    }

    fun importWorkspaceFromLocalPath(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val src = File(path)
                if (src.exists()) {
                    val app = getApplication<Application>()
                    val dest = WorkspaceManager.getWorkspaceDir(app)
                    if (src.isDirectory) {
                        src.copyRecursively(dest, overwrite = true)
                    } else {
                        val destFile = File(dest, src.name)
                        src.copyTo(destFile, overwrite = true)
                    }
                    refreshWorkspaceFileList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error copying local path", e)
            }
        }
    }

    fun inspectFile(fileRelativePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val workspace = WorkspaceManager.getWorkspaceDir(app)
            val file = File(workspace, fileRelativePath)
            if (file.exists()) {
                val result = Decompiler.inspectFile(file)
                _inspectedFileResult.value = result.copy(
                    details = result.details + "\nPath: ${file.absolutePath}"
                )
            }
        }
    }

    fun inspectAbsoluteFile(absolutePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(absolutePath)
            if (file.exists()) {
                val result = Decompiler.inspectFile(file)
                _inspectedFileResult.value = result.copy(
                    details = result.details + "\nPath: ${file.absolutePath}"
                )
            }
        }
    }

    fun decompileZipEntry(zipFilePath: String, entryName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                val zipFile = File(zipFilePath)
                if (zipFile.exists()) {
                    val tempDest = File(app.cacheDir, "extracted_${entryName.replace('/', '_')}")
                    Decompiler.extractZipEntry(zipFile, entryName, tempDest)
                    val result = Decompiler.inspectFile(tempDest)
                    _inspectedFileResult.value = result.copy(
                        fileType = "${result.fileType} (from Zip)",
                        details = "Extracted from: ${zipFile.name}\nEntry: $entryName\n\n${result.details}"
                    )
                    tempDest.delete()
                }
            } catch (e: Exception) {
                _inspectedFileResult.value = InspectionResult("Error", "Extraction Failed", e.stackTraceToString())
            }
        }
    }

    fun clearInspectionResult() {
        _inspectedFileResult.value = null
    }

    // Send chat message & trigger agentic processing loop
    fun sendMessage(text: String, startAutonomous: Boolean = false, maxSteps: Int = 10) {
        val sessionId = _currentSessionId.value ?: return
        if (text.isBlank()) return

        viewModelScope.launch {
            // 1. Insert user message to Database
            repository.insertMessage(
                ChatMessageEntity(sessionId = sessionId, role = "user", content = text)
            )

            val settings = repository.getSettings()
            val provider = settings.selectedProvider
            val key = when (provider) {
                "Google" -> settings.googleApiKey
                "OpenRouter" -> settings.openRouterApiKey
                "Nvidia NIM" -> settings.nvidiaApiKey
                "Mistral" -> settings.mistralApiKey
                "DeepSeek" -> settings.deepSeekApiKey
                "Qwen" -> settings.qwenApiKey
                "Ollama" -> ""
                else -> ""
            }
            val model = when (provider) {
                "Google" -> settings.googleModel
                "OpenRouter" -> settings.openRouterModel
                "Nvidia NIM" -> settings.nvidiaModel
                "Mistral" -> settings.mistralModel
                "DeepSeek" -> settings.deepSeekModel
                "Qwen" -> settings.qwenModel
                "Ollama" -> settings.ollamaModel
                else -> ""
            }

            if (provider != "Ollama" && key.isBlank()) {
                repository.insertMessage(
                    ChatMessageEntity(
                        sessionId = sessionId,
                        role = "assistant",
                        content = "Error: API Key for $provider is not configured in settings drawer."
                    )
                )
                return@launch
            }

            // 2. Set up autonomous/reply state
            if (startAutonomous) {
                _autonomousStatusText.value = "Running"
                _autonomousStepCount.value = 0
            } else {
                _autonomousStatusText.value = "Thinking"
                _autonomousStepCount.value = 0
            }

            // 3. Execution Loop
            var loopActive = true
            while (loopActive && (_autonomousStatusText.value == "Running" || _autonomousStatusText.value == "Thinking")) {
                try {
                    // Refresh data for LLM context
                    val skills = repository.getEnabledSkills()
                    val memories = repository.allMemoriesFlow.first()
                    val workspaceFiles = WorkspaceManager.listFiles(getApplication())

                    // Compile customized dynamic system prompt
                    val skillsBlock = skills.joinToString("\n\n") { "=== SKILL: ${it.name} ===\n${it.content}" }
                    val memoriesBlock = memories.joinToString("\n") { "- ${it.key}: ${it.value}" }
                    val filesBlock = workspaceFiles.joinToString("\n") { "- $it" }

                    val customPrompt = buildString {
                        append(SystemPrompts.DEFAULT_SYSTEM_PROMPT)
                        append("\n\n=== USER ACTIVE WORKSPACE FILES ===\n")
                        if (filesBlock.isBlank()) append("(Empty workspace)\n") else append(filesBlock).append("\n")
                        
                        if (skillsBlock.isNotBlank()) {
                            append("\n=== USER ACTIVE SKILLS & GUIDELINES ===\n")
                            append(skillsBlock).append("\n")
                        }
                        if (memoriesBlock.isNotBlank()) {
                            append("\n=== PERSISTENT USER MEMORIES & CONTEXT ===\n")
                            append(memoriesBlock).append("\n")
                        }
                        if (settings.customSystemPrompt.isNotBlank()) {
                            append("\n=== USER CUSTOM SYSTEM RULES ===\n")
                            append(settings.customSystemPrompt).append("\n")
                        }
                    }

                    // Map chat history to network models
                    val historyMessages = repository.getMessagesForSessionList(sessionId)
                    val apiHistory = historyMessages.map { dbMsg ->
                        ChatCompletionMessage(role = dbMsg.role, content = dbMsg.content)
                    }

                    // Call LLM Completions API
                    val response = ApiClient.chatCompletion(
                        provider = provider,
                        apiKey = key,
                        model = model,
                        systemPrompt = customPrompt,
                        history = apiHistory,
                        customUrl = if (provider == "Ollama") settings.ollamaUrl else ""
                    )

                    // Save raw LLM response to chat DB
                    repository.insertMessage(
                        ChatMessageEntity(sessionId = sessionId, role = "assistant", content = response)
                    )

                    // Parse tool invocations from the response
                    val toolCalls = AgentParser.parseToolCalls(response)
                    
                    if (toolCalls.isEmpty()) {
                        // Conversational response or completion without explicit tool call
                        _autonomousStatusText.value = "Idle"
                        loopActive = false
                    } else {
                        // Execute tool calls sequentially
                        val outcomes = StringBuilder()
                        for (call in toolCalls) {
                            outcomes.append("=== TOOL CALLED: ").append(call.name).append(" ===\n")
                            val outcome = executeToolOnDevice(call)
                            outcomes.append(outcome).append("\n\n")
                        }

                        // Write final execution outcome to database as system/tool feedback
                        repository.insertMessage(
                            ChatMessageEntity(
                                sessionId = sessionId,
                                role = "system",
                                content = outcomes.toString()
                            )
                        )

                        // If user requested only a single turn (Thinking mode), we stop
                        if (_autonomousStatusText.value == "Thinking") {
                            _autonomousStatusText.value = "Idle"
                            loopActive = false
                        } else {
                            // Autonomous loop mode continues
                            _autonomousStepCount.value += 1
                            if (_autonomousStepCount.value >= maxSteps) {
                                _autonomousStatusText.value = "Paused (Max steps reached)"
                                repository.insertMessage(
                                    ChatMessageEntity(
                                        sessionId = sessionId,
                                        role = "system",
                                        content = "System alert: Autonomous task execution paused because the limit of $maxSteps steps was reached to prevent unbounded loops."
                                    )
                                )
                                loopActive = false
                            }
                        }
                    }
                } catch (e: Exception) {
                    _autonomousStatusText.value = "Error"
                    repository.insertMessage(
                        ChatMessageEntity(
                            sessionId = sessionId,
                            role = "system",
                            content = "System Error: ${e.message}"
                        )
                    )
                    loopActive = false
                }
            }
        }
    }

    // Physical tool execution router
    private suspend fun executeToolOnDevice(call: ToolCall): String {
        val context = getApplication<Application>()
        return when (call.name) {
            "list_workspace_files" -> {
                val files = WorkspaceManager.listFiles(context)
                refreshWorkspaceFileList()
                if (files.isEmpty()) "Workspace is currently empty."
                else "Files in workspace:\n" + files.joinToString("\n") { "- $it" }
            }
            "read_file" -> {
                try {
                    val content = WorkspaceManager.readFile(context, call.path)
                    "Content of file '${call.path}':\n\n$content"
                } catch (e: Exception) {
                    "Failed to read file '${call.path}': ${e.message}"
                }
            }
            "write_file" -> {
                try {
                    // Create fallsafe checkpoint before modifying
                    CheckpointManager.createCheckpoint(context, call.path)
                    
                    WorkspaceManager.writeFile(context, call.path, call.textContent)
                    refreshWorkspaceFileList()
                    "File '${call.path}' written successfully. (Failsafe checkpoint auto-created)"
                } catch (e: Exception) {
                    "Failed to write file '${call.path}': ${e.message}"
                }
            }
            "edit_file" -> {
                try {
                    CheckpointManager.createCheckpoint(context, call.path)
                    val success = WorkspaceManager.editFile(context, call.path, call.target, call.replacement)
                    refreshWorkspaceFileList()
                    if (success) {
                        "Surgical edit applied successfully to file '${call.path}'. (Failsafe checkpoint auto-created)"
                    } else {
                        "Surgical edit failed: target block was not found exactly in file '${call.path}'."
                    }
                } catch (e: Exception) {
                    "Failed to edit file '${call.path}': ${e.message}"
                }
            }
            "run_terminal_command" -> {
                try {
                    val out = TerminalExecutor.executeCommand(context, call.textContent)
                    "Terminal output:\n$out"
                } catch (e: Exception) {
                    "Failed to execute command: ${e.message}"
                }
            }
            "save_memory" -> {
                try {
                    repository.insertMemory(MemoryEntity(key = call.key, value = call.textContent))
                    "Memory successfully saved for key '${call.key}'."
                } catch (e: Exception) {
                    "Failed to save memory: ${e.message}"
                }
            }
            "get_memory" -> {
                try {
                    val memory = repository.getMemoryByKey(call.key)
                    if (memory != null) "Memory content for key '${call.key}':\n${memory.value}"
                    else "No memory found for key '${call.key}'."
                } catch (e: Exception) {
                    "Error querying memory: ${e.message}"
                }
            }
            "list_skills" -> {
                val skills = repository.getEnabledSkills()
                if (skills.isEmpty()) "No custom Skills are currently enabled."
                else "Enabled skills:\n\n" + skills.joinToString("\n\n") { "=== SKILL: ${it.name} ===\n${it.content}" }
            }
            "call_mcp_server" -> {
                callMcpServerHttp(call.server, call.method, call.textContent)
            }
            "task_done" -> {
                _autonomousStatusText.value = "Completed"
                "Task completed status signal received. Done!"
            }
            else -> "Unknown tool name: '${call.name}'."
        }
    }

    // Call user-defined MCP server via standard REST POST
    private suspend fun callMcpServerHttp(serverName: String, method: String, paramsJson: String): String {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Find enabled server configuration matching serverName
                val mcpServers = repository.getEnabledMcpServers()
                val targetServer = mcpServers.find { it.name.equals(serverName, ignoreCase = true) }
                    ?: return@withContext "Error: MCP server '$serverName' is not configured or not enabled."

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = """
                    {
                        "method": "$method",
                        "params": $paramsJson
                    }
                """.trimIndent()

                val request = Request.Builder()
                    .url(targetServer.url)
                    .post(requestBody.toRequestBody(mediaType))
                    .build()

                val response = httpClient.newCall(request).execute()
                val bodyText = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    "MCP server '$serverName' response:\n$bodyText"
                } else {
                    "Error from MCP server '$serverName' (Code ${response.code}): $bodyText"
                }
            } catch (e: Exception) {
                "Error calling MCP server '$serverName': ${e.message}"
            }
        }
    }

    // Direct terminal commands executed by the user in the Terminal UI
    private val _terminalLogs = MutableStateFlow("GitHelp local shell terminal ready.\nType commands to execute in active workspace...\n")
    val terminalLogs: StateFlow<String> = _terminalLogs.asStateFlow()

    fun runUserTerminalCommand(command: String) {
        if (command.isBlank()) return
        viewModelScope.launch {
            _terminalLogs.value += "\n$ " + command + "\n"
            val output = TerminalExecutor.executeCommand(getApplication(), command)
            _terminalLogs.value += output + "\n"
        }
    }

    fun clearTerminalLogs() {
        _terminalLogs.value = "Terminal cleared.\n"
    }
}
