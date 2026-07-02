package com.example.database

import kotlinx.coroutines.flow.Flow

class GitHelpRepository(private val dao: DatabaseDao) {

    // Settings
    val settingsFlow: Flow<SettingsEntity?> = dao.getSettingsFlow()

    suspend fun getSettings(): SettingsEntity {
        return dao.getSettings() ?: SettingsEntity().also {
            dao.insertSettings(it)
        }
    }

    suspend fun updateSettings(settings: SettingsEntity) {
        dao.insertSettings(settings)
    }

    // Sessions
    val allSessionsFlow: Flow<List<ChatSessionEntity>> = dao.getAllSessionsFlow()

    suspend fun getSessionById(id: Long): ChatSessionEntity? = dao.getSessionById(id)

    suspend fun insertSession(session: ChatSessionEntity): Long = dao.insertSession(session)

    suspend fun updateSession(session: ChatSessionEntity) = dao.updateSession(session)

    suspend fun deleteSession(id: Long) {
        dao.deleteMessagesForSession(id)
        dao.deleteSessionById(id)
    }

    // Messages
    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessageEntity>> =
        dao.getMessagesForSessionFlow(sessionId)

    suspend fun getMessagesForSessionList(sessionId: Long): List<ChatMessageEntity> =
        dao.getMessagesForSession(sessionId)

    suspend fun insertMessage(message: ChatMessageEntity): Long = dao.insertMessage(message)

    suspend fun deleteMessageById(messageId: Long) = dao.deleteMessageById(messageId)

    suspend fun deleteMessagesForSession(sessionId: Long) = dao.deleteMessagesForSession(sessionId)

    // Skills
    val allSkillsFlow: Flow<List<SkillUploadEntity>> = dao.getAllSkillsFlow()

    suspend fun getEnabledSkills(): List<SkillUploadEntity> = dao.getEnabledSkills()

    suspend fun insertSkill(skill: SkillUploadEntity): Long = dao.insertSkill(skill)

    suspend fun updateSkill(skill: SkillUploadEntity) = dao.updateSkill(skill)

    suspend fun deleteSkillById(id: Long) = dao.deleteSkillById(id)

    // MCP Servers
    val allMcpServersFlow: Flow<List<McpServerEntity>> = dao.getAllMcpServersFlow()

    suspend fun getEnabledMcpServers(): List<McpServerEntity> = dao.getEnabledMcpServers()

    suspend fun insertMcpServer(server: McpServerEntity): Long = dao.insertMcpServer(server)

    suspend fun updateMcpServer(server: McpServerEntity) = dao.updateMcpServer(server)

    suspend fun deleteMcpServerById(id: Long) = dao.deleteMcpServerById(id)

    // Memories
    val allMemoriesFlow: Flow<List<MemoryEntity>> = dao.getAllMemoriesFlow()

    suspend fun getMemoryByKey(key: String): MemoryEntity? = dao.getMemoryByKey(key)

    suspend fun insertMemory(memory: MemoryEntity) = dao.insertMemory(memory)

    suspend fun deleteMemoryByKey(key: String) = dao.deleteMemoryByKey(key)
}
