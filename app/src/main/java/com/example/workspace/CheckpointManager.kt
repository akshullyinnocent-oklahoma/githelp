package com.example.workspace

import android.content.Context
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Checkpoint(
    val timestamp: Long,
    val formattedTime: String,
    val relativePath: String,
    val backupFile: File
)

object CheckpointManager {
    private const val CHECKPOINTS_DIR_NAME = "githelp_checkpoints"

    private fun getCheckpointsDir(context: Context): File {
        val dir = File(context.filesDir, CHECKPOINTS_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    // Capture a failsafe checkpoint of a workspace file before modifying it
    fun createCheckpoint(context: Context, relativePath: String) {
        try {
            val workspaceDir = WorkspaceManager.getWorkspaceDir(context)
            val sourceFile = File(workspaceDir, relativePath)
            if (!sourceFile.exists()) return

            val checkpointsDir = getCheckpointsDir(context)
            val timestamp = System.currentTimeMillis()
            val timeString = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(timestamp))
            
            // Clean filename to prevent path traversal in checkpoint storage
            val safePathName = relativePath.replace(File.separator, "_")
            val backupFileName = "cp_${timeString}_${safePathName}"
            val backupFile = File(checkpointsDir, backupFileName)

            sourceFile.copyTo(backupFile, overwrite = true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // List all saved checkpoints
    fun getCheckpoints(context: Context): List<Checkpoint> {
        val checkpointsDir = getCheckpointsDir(context)
        val files = checkpointsDir.listFiles() ?: return emptyList()
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        return files.mapNotNull { file ->
            val name = file.name
            if (name.startsWith("cp_") && name.length > 18) {
                try {
                    val datePart = name.substring(3, 18) // yyyyMMdd_HHmmss
                    val relativePathPart = name.substring(19).replace("_", File.separator)
                    val parsedDate = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).parse(datePart)
                    val timestamp = parsedDate?.time ?: file.lastModified()
                    Checkpoint(
                        timestamp = timestamp,
                        formattedTime = format.format(Date(timestamp)),
                        relativePath = relativePathPart,
                        backupFile = file
                    )
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }.sortedByDescending { it.timestamp }
    }

    // Restore a workspace file from a checkpoint
    fun restoreCheckpoint(context: Context, checkpoint: Checkpoint): Boolean {
        return try {
            val workspaceDir = WorkspaceManager.getWorkspaceDir(context)
            val destFile = File(workspaceDir, checkpoint.relativePath)
            destFile.parentFile?.mkdirs()
            checkpoint.backupFile.copyTo(destFile, overwrite = true)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Clear all checkpoints
    fun clearCheckpoints(context: Context) {
        val dir = getCheckpointsDir(context)
        dir.deleteRecursively()
        dir.mkdirs()
    }
}
