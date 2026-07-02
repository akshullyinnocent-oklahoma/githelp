package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.ChatMessageEntity
import com.example.database.McpServerEntity
import com.example.database.SkillUploadEntity
import com.example.database.SettingsEntity
import com.example.network.ApiClient
import com.example.network.ToolCall
import androidx.compose.foundation.text.BasicTextField
import com.example.workspace.Checkpoint
import com.example.workspace.CheckpointManager
import com.example.workspace.WorkspaceManager
import kotlinx.coroutines.launch
import java.io.File

// Neon colors definition for AMOLED layout
val NeonCyan = Color(0xFF00E5FF)
val NeonGreen = Color(0xFF39FF14)
val NeonPink = Color(0xFFFF007F)
val NeonYellow = Color(0xFFFFD700)
val NeonOrange = Color(0xFFFF5F1F)
val AmoledBackground = Color(0xFF000000)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHelpMainScreen(viewModel: GitHelpViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Data observing
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val sessions by viewModel.sessionsState.collectAsStateWithLifecycle()
    val skills by viewModel.skillsState.collectAsStateWithLifecycle()
    val mcpServers by viewModel.mcpServersState.collectAsStateWithLifecycle()
    val memories by viewModel.memoriesState.collectAsStateWithLifecycle()
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val messages by viewModel.currentMessages.collectAsStateWithLifecycle()
    
    val workspaceFiles by viewModel.activeWorkspaceFiles.collectAsStateWithLifecycle()
    val fetchedModels by viewModel.fetchedModels.collectAsStateWithLifecycle()
    val isFetchingModels by viewModel.isFetchingModels.collectAsStateWithLifecycle()
    val isTestingConnection by viewModel.isTestingConnection.collectAsStateWithLifecycle()
    val connectionResult by viewModel.connectionTestResult.collectAsStateWithLifecycle()
    val terminalLogs by viewModel.terminalLogs.collectAsStateWithLifecycle()
    val autoStatus by viewModel.autonomousStatusText.collectAsStateWithLifecycle()
    val autoSteps by viewModel.autonomousStepCount.collectAsStateWithLifecycle()

    // Screen selection (0: Chat, 1: Terminal/IDE, 2: Checkpoints, 3: Memories & Skills)
    var selectedTab by remember { mutableStateOf(0) }

    // Checkpoints state
    var checkpointsList by remember { mutableStateOf<List<Checkpoint>>(emptyList()) }
    LaunchedEffect(key1 = selectedTab) {
        if (selectedTab == 2) {
            checkpointsList = CheckpointManager.getCheckpoints(context)
        }
    }

    // SAF File selection launchers
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.importWorkspaceFromSaf(uri)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.exportWorkspaceToSaf(uri)
        }
    }

    // Color Theme configuration based on settings.isAmoledTheme
    val backgroundColor = if (settings.isAmoledTheme) AmoledBackground else MaterialTheme.colorScheme.background
    val surfaceColor = if (settings.isAmoledTheme) Color(0xFF121212) else MaterialTheme.colorScheme.surfaceVariant
    val cardColor = if (settings.isAmoledTheme) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surface

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(340.dp),
                drawerContainerColor = backgroundColor,
                drawerContentColor = MaterialTheme.colorScheme.onBackground
            ) {
                // Side Drawer Config items
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "GitHelp Configs",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (settings.isAmoledTheme) NeonCyan else MaterialTheme.colorScheme.primary
                    )

                    HorizontalDivider(color = if (settings.isAmoledTheme) NeonCyan.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant)

                    // Historical Chat Sessions Selector
                    Text("Chat History", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        border = if (settings.isAmoledTheme) BorderStroke(1.dp, NeonYellow.copy(alpha = 0.3f)) else null
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            sessions.forEach { session ->
                                val isSelected = session.id == currentSessionId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectSession(session.id)
                                            coroutineScope.launch { drawerState.close() }
                                        }
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = session.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) (if (settings.isAmoledTheme) NeonGreen else MaterialTheme.colorScheme.primary) else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (sessions.size > 1) {
                                        IconButton(onClick = { viewModel.deleteSession(session.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Session", tint = Color.Red.copy(alpha = 0.7f))
                                        }
                                    }
                                }
                            }
                            Button(
                                onClick = { viewModel.createNewSession() },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = if (settings.isAmoledTheme) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "New Session")
                                Spacer(Modifier.width(8.dp))
                                Text("New Session")
                            }
                        }
                    }

                    // Provider & BYOK Section
                    Text("Provider BYOK Setup", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        border = if (settings.isAmoledTheme) BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)) else null
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Active Provider Select
                            val providers = listOf("Google", "OpenRouter", "Nvidia NIM", "Mistral", "DeepSeek", "Qwen", "Ollama")
                            var providerExpanded by remember { mutableStateOf(false) }

                            Text("Selected Provider:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { providerExpanded = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(settings.selectedProvider)
                                    Spacer(Modifier.weight(1f))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = providerExpanded,
                                    onDismissRequest = { providerExpanded = false }
                                ) {
                                    providers.forEach { p ->
                                        DropdownMenuItem(
                                            text = { Text(p) },
                                            onClick = {
                                                providerExpanded = false
                                                val defaultModel = ApiClient.getDefaultModels(p).firstOrNull() ?: ""
                                                viewModel.updateSelectedProviderAndModel(p, defaultModel)
                                            }
                                        )
                                    }
                                }
                            }

                            // Dynamic API Key input (except Ollama)
                            if (settings.selectedProvider != "Ollama") {
                                var showKey by remember { mutableStateOf(false) }
                                val currentKey = when (settings.selectedProvider) {
                                    "Google" -> settings.googleApiKey
                                    "OpenRouter" -> settings.openRouterApiKey
                                    "Nvidia NIM" -> settings.nvidiaApiKey
                                    "Mistral" -> settings.mistralApiKey
                                    "DeepSeek" -> settings.deepSeekApiKey
                                    "Qwen" -> settings.qwenApiKey
                                    else -> ""
                                }

                                OutlinedTextField(
                                    value = currentKey,
                                    onValueChange = { viewModel.updateApiKey(settings.selectedProvider, it) },
                                    label = { Text("${settings.selectedProvider} API Key") },
                                    singleLine = true,
                                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showKey = !showKey }) {
                                            Icon(
                                                imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Toggle Visibility"
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                // Ollama custom URL setup
                                OutlinedTextField(
                                    value = settings.ollamaUrl,
                                    onValueChange = { viewModel.updateOllamaUrl(it) },
                                    label = { Text("Ollama Server URL") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Model Select
                            val activeModel = when (settings.selectedProvider) {
                                "Google" -> settings.googleModel
                                "OpenRouter" -> settings.openRouterModel
                                "Nvidia NIM" -> settings.nvidiaModel
                                "Mistral" -> settings.mistralModel
                                "DeepSeek" -> settings.deepSeekModel
                                "Qwen" -> settings.qwenModel
                                "Ollama" -> settings.ollamaModel
                                else -> ""
                            }

                            var modelExpanded by remember { mutableStateOf(false) }
                            Text("Model Selection:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { modelExpanded = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(activeModel.ifBlank { "Select Model" })
                                    Spacer(Modifier.weight(1f))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = modelExpanded,
                                    onDismissRequest = { modelExpanded = false }
                                ) {
                                    // Use fetched models first, then fall back to defaults
                                    val modelsList = fetchedModels.ifEmpty { ApiClient.getDefaultModels(settings.selectedProvider) }
                                    modelsList.forEach { m ->
                                        DropdownMenuItem(
                                            text = { Text(m) },
                                            onClick = {
                                                modelExpanded = false
                                                viewModel.updateSelectedProviderAndModel(settings.selectedProvider, m)
                                            }
                                        )
                                    }
                                }
                            }

                            // Connection and Models fetch tools
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.fetchProviderModels(settings.selectedProvider) },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isFetchingModels,
                                    colors = ButtonDefaults.buttonColors(containerColor = if (settings.isAmoledTheme) NeonCyan else MaterialTheme.colorScheme.secondary)
                                ) {
                                    if (isFetchingModels) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                                    } else {
                                        Text("Fetch Models", fontSize = 11.sp, color = Color.Black)
                                    }
                                }

                                Button(
                                    onClick = { viewModel.testProviderConnection(settings.selectedProvider) },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isTestingConnection,
                                    colors = ButtonDefaults.buttonColors(containerColor = if (settings.isAmoledTheme) NeonGreen else MaterialTheme.colorScheme.primary)
                                ) {
                                    if (isTestingConnection) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                                    } else {
                                        Text("Test Conn", fontSize = 11.sp, color = Color.Black)
                                    }
                                }
                            }

                            if (!connectionResult.isNullOrBlank()) {
                                Text(
                                    text = connectionResult ?: "",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (connectionResult!!.contains("Success")) Color.Green else Color.Red,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }

                    // Tokens Inputs Area (GitHub PAT & HuggingFace PAT)
                    Text("Integrations Tokens", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        border = if (settings.isAmoledTheme) BorderStroke(1.dp, NeonOrange.copy(alpha = 0.3f)) else null
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = settings.githubPat,
                                onValueChange = { viewModel.updateGithubPat(it) },
                                label = { Text("GitHub PAT Token") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = settings.huggingfacePat,
                                onValueChange = { viewModel.updateHuggingfacePat(it) },
                                label = { Text("HuggingFace PAT Token") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // MCP Connection area
                    Text("Model Context Protocol (MCP)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        border = if (settings.isAmoledTheme) BorderStroke(1.dp, NeonPink.copy(alpha = 0.3f)) else null
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            var mcpName by remember { mutableStateOf("") }
                            var mcpUrl by remember { mutableStateOf("") }

                            Text(
                                text = "To connect to an MCP server running on your Termux on port 8080, use http://127.0.0.1:8080 or http://localhost:8080 as the Endpoint URL.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )

                            OutlinedTextField(
                                value = mcpName,
                                onValueChange = { mcpName = it },
                                label = { Text("Server Name") },
                                placeholder = { Text("e.g. Termux MCP") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = mcpUrl,
                                onValueChange = { mcpUrl = it },
                                label = { Text("SSE / Endpoint URL") },
                                placeholder = { Text("e.g. http://127.0.0.1:8080") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Quick loopback presets
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        mcpName = "Termux MCP"
                                        mcpUrl = "http://127.0.0.1:8080"
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (settings.isAmoledTheme) NeonCyan.copy(alpha = 0.2f) else MaterialTheme.colorScheme.secondaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("Preset: 127.0.0.1:8080", fontSize = 10.sp, color = if (settings.isAmoledTheme) NeonCyan else MaterialTheme.colorScheme.onSecondaryContainer)
                                }

                                Button(
                                    onClick = {
                                        mcpName = "Localhost MCP"
                                        mcpUrl = "http://localhost:8080"
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (settings.isAmoledTheme) NeonGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.secondaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("Preset: localhost:8080", fontSize = 10.sp, color = if (settings.isAmoledTheme) NeonGreen else MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }

                            Button(
                                onClick = {
                                    if (mcpName.isNotBlank() && mcpUrl.isNotBlank()) {
                                        viewModel.addMcpServer(mcpName, mcpUrl)
                                        mcpName = ""
                                        mcpUrl = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = if (settings.isAmoledTheme) NeonPink else MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Add MCP Server", color = Color.Black)
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            mcpServers.forEach { server ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(server.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(server.url, fontSize = 10.sp, maxLines = 1)
                                    }
                                    Switch(
                                        checked = server.isEnabled,
                                        onCheckedChange = { viewModel.toggleMcpServer(server) },
                                        colors = SwitchDefaults.colors(checkedThumbColor = NeonPink)
                                    )
                                    IconButton(onClick = { viewModel.deleteMcpServer(server.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red.copy(alpha = 0.7f))
                                    }
                                }
                            }
                        }
                    }

                    // Workspace Sync Section
                    Text("Workspace SAF Synchronization", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        border = if (settings.isAmoledTheme) BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)) else null
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { importLauncher.launch(null) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = if (settings.isAmoledTheme) NeonCyan else MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.Black)
                                Spacer(Modifier.width(8.dp))
                                Text("Import folder from SAF", color = Color.Black)
                            }

                            Button(
                                onClick = { exportLauncher.launch(null) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = if (settings.isAmoledTheme) NeonGreen else MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.Black)
                                Spacer(Modifier.width(8.dp))
                                Text("Export folder to SAF", color = Color.Black)
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "GitHelp",
                            fontWeight = FontWeight.Bold,
                            color = if (settings.isAmoledTheme) NeonCyan else MaterialTheme.colorScheme.primary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Menu Drawer",
                                tint = if (settings.isAmoledTheme) NeonCyan else LocalContentColor.current
                            )
                        }
                    },
                    actions = {
                        // AMOLED black vs Standard Dark switcher
                        IconButton(onClick = { viewModel.toggleAmoledTheme(!settings.isAmoledTheme) }) {
                            Icon(
                                imageVector = if (settings.isAmoledTheme) Icons.Default.ToggleOn else Icons.Default.ToggleOff,
                                contentDescription = "Toggle AMOLED Mode",
                                tint = if (settings.isAmoledTheme) NeonGreen else LocalContentColor.current
                            )
                        }
                        Text(
                            "AMOLED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (settings.isAmoledTheme) NeonGreen else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(8.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = backgroundColor
                    )
                )
            },
            bottomBar = {
                // Bottom tabs with neon features buttons layout
                NavigationBar(
                    containerColor = backgroundColor,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Chat, contentDescription = "Chat") },
                        label = { Text("Chat") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (settings.isAmoledTheme) NeonGreen else MaterialTheme.colorScheme.primary,
                            indicatorColor = if (settings.isAmoledTheme) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Terminal, contentDescription = "Terminal") },
                        label = { Text("IDE Terminal") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (settings.isAmoledTheme) NeonCyan else MaterialTheme.colorScheme.primary,
                            indicatorColor = if (settings.isAmoledTheme) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Backup, contentDescription = "Checkpoints") },
                        label = { Text("Checkpoints") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (settings.isAmoledTheme) NeonPink else MaterialTheme.colorScheme.primary,
                            indicatorColor = if (settings.isAmoledTheme) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Psychology, contentDescription = "Skills") },
                        label = { Text("Memory/Skills") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (settings.isAmoledTheme) NeonYellow else MaterialTheme.colorScheme.primary,
                            indicatorColor = if (settings.isAmoledTheme) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            },
            containerColor = backgroundColor
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Render selected panel
                when (selectedTab) {
                    0 -> ChatTab(viewModel, settings, cardColor, backgroundColor)
                    1 -> TerminalTab(viewModel, settings, cardColor, backgroundColor, workspaceFiles)
                    2 -> CheckpointsTab(viewModel, settings, cardColor, checkpointsList) {
                        checkpointsList = CheckpointManager.getCheckpoints(context)
                    }
                    3 -> MemorySkillsTab(viewModel, settings, cardColor, skills, memories)
                }
            }
        }
    }
}

// ==========================================
// CHAT TAB (Gemini Google Like UI)
// ==========================================
@Composable
fun ChatTab(viewModel: GitHelpViewModel, settings: SettingsEntity, cardColor: Color, backgroundColor: Color) {
    val messages by viewModel.currentMessages.collectAsStateWithLifecycle()
    val autoStatus by viewModel.autonomousStatusText.collectAsStateWithLifecycle()
    val autoSteps by viewModel.autonomousStepCount.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current

    var inputText by remember { mutableStateOf("") }
    var autonomousEnabled by remember { mutableStateOf(false) }
    var maxStepsInput by remember { mutableStateOf("10") }

    val listState = rememberLazyListState()

    // Autoscroll to bottom when messages list size changes
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Autonomous status indicators & controls
        if (autoStatus != "Idle") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (settings.isAmoledTheme) Color(0xFF111111) else MaterialTheme.colorScheme.primaryContainer
                ),
                border = if (settings.isAmoledTheme) BorderStroke(1.dp, NeonGreen) else null
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = if (settings.isAmoledTheme) NeonGreen else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Autonomous Execution: $autoStatus",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (settings.isAmoledTheme) NeonGreen else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Current step: $autoSteps",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Messages scrolling view
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            items(messages) { message ->
                MessageBubble(message, settings, cardColor)
            }

            item { Spacer(Modifier.height(16.dp)) }
        }

        // Autonomous config toolbar before typing area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = autonomousEnabled,
                    onCheckedChange = { autonomousEnabled = it },
                    colors = CheckboxDefaults.colors(checkedColor = NeonGreen)
                )
                Text(
                    "Autonomous Task Mode",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (settings.isAmoledTheme) NeonGreen else MaterialTheme.colorScheme.onBackground
                )
            }

            if (autonomousEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Max Steps: ", fontSize = 12.sp)
                    OutlinedTextField(
                        value = maxStepsInput,
                        onValueChange = { maxStepsInput = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.width(60.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                }
            }
        }

        // Text typing input area (resembling standard AI chat apps)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Ask GitHelp or describe a task...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (settings.isAmoledTheme) NeonCyan else MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank()) {
                            val steps = maxStepsInput.toIntOrNull() ?: 10
                            viewModel.sendMessage(inputText, startAutonomous = autonomousEnabled, maxSteps = steps)
                            inputText = ""
                            keyboardController?.hide()
                        }
                    }
                )
            )

            FloatingActionButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        val steps = maxStepsInput.toIntOrNull() ?: 10
                        viewModel.sendMessage(inputText, startAutonomous = autonomousEnabled, maxSteps = steps)
                        inputText = ""
                        keyboardController?.hide()
                    }
                },
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(24.dp),
                containerColor = if (settings.isAmoledTheme) NeonCyan else MaterialTheme.colorScheme.primary,
                contentColor = Color.Black
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Message")
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessageEntity, settings: SettingsEntity, cardColor: Color) {
    val isUser = message.role == "user"
    val isSystem = message.role == "system"

    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleBg = when {
        isUser -> if (settings.isAmoledTheme) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.primaryContainer
        isSystem -> if (settings.isAmoledTheme) Color(0xFF110011) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        else -> cardColor
    }

    val bubbleBorder = when {
        isUser && settings.isAmoledTheme -> BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
        isSystem && settings.isAmoledTheme -> BorderStroke(1.dp, NeonPink.copy(alpha = 0.5f))
        !isUser && settings.isAmoledTheme -> BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
        else -> null
    }

    val labelText = when (message.role) {
        "user" -> "You"
        "system" -> "Tool / System Output"
        "tool" -> "Tool Execution Feedback"
        else -> "GitHelp Assistant"
    }

    val labelColor = when (message.role) {
        "user" -> if (settings.isAmoledTheme) NeonCyan else MaterialTheme.colorScheme.primary
        "system" -> if (settings.isAmoledTheme) NeonPink else Color.Red
        else -> if (settings.isAmoledTheme) NeonGreen else MaterialTheme.colorScheme.secondary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        Text(
            text = labelText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = labelColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )

        Card(
            modifier = Modifier
                .widthIn(max = 320.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = bubbleBg),
            border = bubbleBorder
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (isSystem) {
                    // System execution reports can be in monospace format
                    Text(
                        text = message.content,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = message.content,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

// ==========================================
// TERMINAL & FILES IDE TAB
// ==========================================
@Composable
fun TerminalTab(
    viewModel: GitHelpViewModel,
    settings: SettingsEntity,
    cardColor: Color,
    backgroundColor: Color,
    workspaceFiles: List<String>
) {
    val terminalLogs by viewModel.terminalLogs.collectAsStateWithLifecycle()
    var userCommandInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Workspace Files",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (settings.isAmoledTheme) NeonCyan else MaterialTheme.colorScheme.primary
            )

            IconButton(onClick = { viewModel.refreshWorkspaceFileList() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Files", tint = if (settings.isAmoledTheme) NeonCyan else LocalContentColor.current)
            }
        }

        // Small workspace file listing card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            border = if (settings.isAmoledTheme) BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)) else null
        ) {
            if (workspaceFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Workspace is empty.", fontSize = 12.sp, color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.padding(8.dp)) {
                    items(workspaceFiles) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.InsertDriveFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (settings.isAmoledTheme) NeonCyan else MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = file,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        Text(
            "Local Terminal Console (sh)",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = if (settings.isAmoledTheme) NeonGreen else MaterialTheme.colorScheme.primary
        )

        // Monospaced Real terminal display
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            border = if (settings.isAmoledTheme) BorderStroke(1.dp, NeonGreen.copy(alpha = 0.3f)) else null
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = terminalLogs,
                        color = NeonGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }

                HorizontalDivider(color = NeonGreen.copy(alpha = 0.2f))

                // Terminal Input area
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$ ", color = NeonGreen, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    BasicTextField(
                        value = userCommandInput,
                        onValueChange = { userCommandInput = it },
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = NeonGreen,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                if (userCommandInput.isNotBlank()) {
                                    viewModel.runUserTerminalCommand(userCommandInput)
                                    userCommandInput = ""
                                    keyboardController?.hide()
                                }
                            }
                        ),
                        singleLine = true
                    )
                    IconButton(onClick = { viewModel.clearTerminalLogs() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear logs", tint = NeonGreen)
                    }
                }
            }
        }
    }
}

// ==========================================
// CHECKPOINTS TAB
// ==========================================
@Composable
fun CheckpointsTab(
    viewModel: GitHelpViewModel,
    settings: SettingsEntity,
    cardColor: Color,
    checkpoints: List<Checkpoint>,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Failsafe Checkpoints",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (settings.isAmoledTheme) NeonPink else MaterialTheme.colorScheme.primary
            )

            Row {
                IconButton(onClick = {
                    CheckpointManager.clearCheckpoints(context)
                    onRefresh()
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear Checkpoints", tint = Color.Red.copy(alpha = 0.7f))
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Checkpoints", tint = if (settings.isAmoledTheme) NeonPink else LocalContentColor.current)
                }
            }
        }

        Text(
            "Below is the timeline of physical file backups auto-generated before any write or edit actions. Use them to instantly roll back if progress gets interrupted.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        if (checkpoints.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No checkpoints captured yet.", fontSize = 13.sp, color = Color.Gray)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(checkpoints) { cp ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        border = if (settings.isAmoledTheme) BorderStroke(1.dp, NeonPink.copy(alpha = 0.3f)) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cp.relativePath,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (settings.isAmoledTheme) NeonPink else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Backup time: ${cp.formattedTime}",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            Button(
                                onClick = {
                                    val restored = CheckpointManager.restoreCheckpoint(context, cp)
                                    if (restored) {
                                        viewModel.refreshWorkspaceFileList()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (settings.isAmoledTheme) NeonPink else MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, tint = Color.Black)
                                Spacer(Modifier.width(4.dp))
                                Text("Restore", color = Color.Black, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// MEMORY & SKILLS TAB
// ==========================================
@Composable
fun MemorySkillsTab(
    viewModel: GitHelpViewModel,
    settings: SettingsEntity,
    cardColor: Color,
    skills: List<SkillUploadEntity>,
    memories: List<com.example.database.MemoryEntity>
) {
    var skillName by remember { mutableStateOf("") }
    var skillContent by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Skill uploads section
        Text(
            "Skill.md Guidelines",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = if (settings.isAmoledTheme) NeonYellow else MaterialTheme.colorScheme.primary
        )

        Text(
            "Load custom rules or instruction guidelines to enforce behavior in the LLM during autonomous coding runs.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = cardColor),
            border = if (settings.isAmoledTheme) BorderStroke(1.dp, NeonYellow.copy(alpha = 0.2f)) else null
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = skillName,
                    onValueChange = { skillName = it },
                    label = { Text("Skill File Name (e.g., room_rules.md)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = skillContent,
                    onValueChange = { skillContent = it },
                    label = { Text("Skill / Rules Markdown Content") },
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )

                Button(
                    onClick = {
                        if (skillName.isNotBlank() && skillContent.isNotBlank()) {
                            viewModel.addSkill(skillName, skillContent)
                            skillName = ""
                            skillContent = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (settings.isAmoledTheme) NeonYellow else MaterialTheme.colorScheme.primary)
                ) {
                    Text("Add Skill", color = Color.Black)
                }
            }
        }

        // Active Skills List
        skills.forEach { skill ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                border = if (settings.isAmoledTheme) BorderStroke(1.dp, NeonYellow.copy(alpha = 0.15f)) else null
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(skill.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(skill.content, maxLines = 2, fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = skill.isEnabled,
                        onCheckedChange = { viewModel.toggleSkill(skill) },
                        colors = SwitchDefaults.colors(checkedThumbColor = NeonYellow)
                    )
                    IconButton(onClick = { viewModel.deleteSkill(skill.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red.copy(alpha = 0.7f))
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Persistent memories logs
        Text(
            "Persistent LLM Memory Logs",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = if (settings.isAmoledTheme) NeonCyan else MaterialTheme.colorScheme.primary
        )

        Text(
            "Key-Value memories and coding preferences recorded dynamically by the LLM during autonomous iterations.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        if (memories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No persistent memories saved yet.", fontSize = 12.sp, color = Color.Gray)
            }
        } else {
            memories.forEach { mem ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    border = if (settings.isAmoledTheme) BorderStroke(1.dp, NeonCyan.copy(alpha = 0.15f)) else null
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mem.key, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (settings.isAmoledTheme) NeonCyan else MaterialTheme.colorScheme.primary)
                            Text(mem.value, fontSize = 12.sp)
                        }
                        IconButton(onClick = { viewModel.deleteMemoryByKey(mem.key) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }
}

// Simple modifier helper for sizing
fun Modifier.size(size: Int): Modifier = this.size(size.dp)
