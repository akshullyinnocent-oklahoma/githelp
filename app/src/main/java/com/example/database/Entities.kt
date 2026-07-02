package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val googleApiKey: String = "",
    val openRouterApiKey: String = "",
    val nvidiaApiKey: String = "",
    val mistralApiKey: String = "",
    val deepSeekApiKey: String = "",
    val qwenApiKey: String = "",
    val ollamaUrl: String = "http://localhost:11434",
    val selectedProvider: String = "Google", // Google, OpenRouter, Nvidia NIM, Mistral, DeepSeek, Qwen, Ollama
    val googleModel: String = "gemini-2.5-flash",
    val openRouterModel: String = "google/gemini-2.5-flash",
    val nvidiaModel: String = "meta/llama-3.1-405b-instruct",
    val mistralModel: String = "mistral-large-latest",
    val deepSeekModel: String = "deepseek-chat",
    val qwenModel: String = "qwen-turbo",
    val ollamaModel: String = "llama3",
    val customSystemPrompt: String = "",
    val githubPat: String = "",
    val huggingfacePat: String = "",
    val workspaceSafUri: String = "",
    val workspacePath: String = "", // Local fallback directory path
    val isAmoledTheme: Boolean = true,
    val isAutonomousActive: Boolean = false
)

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isAutonomous: Boolean = false,
    val autonomousGoal: String = "",
    val autonomousStatus: String = "Idle", // Idle, Running, Paused, Completed, Interrupted
    val autonomousStepCount: Int = 0,
    val maxAutonomousSteps: Int = 10,
    val lastSavedCheckpointTime: Long = 0
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String, // user, assistant, system, tool
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isCheckpoint: Boolean = false,
    val checkpointSnapshot: String = "" // JSON or text representing file snapshots
)

@Entity(tableName = "skill_uploads")
data class SkillUploadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val content: String,
    val isEnabled: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "mcp_servers")
data class McpServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val isEnabled: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_memories")
data class MemoryEntity(
    @PrimaryKey val key: String,
    val value: String,
    val timestamp: Long = System.currentTimeMillis()
)
