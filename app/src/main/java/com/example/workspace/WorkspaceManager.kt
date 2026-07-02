package com.example.workspace

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException

object WorkspaceManager {
    private const val WORKSPACE_DIR_NAME = "githelp_workspace"

    // Get the local workspace directory
    fun getWorkspaceDir(context: Context): File {
        val dir = File(context.filesDir, WORKSPACE_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
            // Create a default readme to show some initial contents
            File(dir, "README.md").writeText(
                "# GitHelp Workspace\n\nThis is your active local development workspace. " +
                "You can run terminal commands, write code, and use SAF to sync this with your device storage."
            )
        }
        return dir
    }

    // List all files in the workspace (relative paths)
    fun listFiles(context: Context): List<String> {
        val workspace = getWorkspaceDir(context)
        val result = mutableListOf<String>()
        workspace.walkTopDown().forEach { file ->
            if (file.isFile) {
                result.add(file.relativeTo(workspace).path)
            }
        }
        return result
    }

    // Read file contents
    fun readFile(context: Context, relativePath: String): String {
        val workspace = getWorkspaceDir(context)
        val file = File(workspace, relativePath)
        if (!file.exists()) {
            throw IOException("File does not exist: $relativePath")
        }
        return file.readText()
    }

    // Write file contents
    fun writeFile(context: Context, relativePath: String, content: String) {
        val workspace = getWorkspaceDir(context)
        val file = File(workspace, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    // Surgical edit (Search & Replace)
    fun editFile(context: Context, relativePath: String, target: String, replacement: String): Boolean {
        val workspace = getWorkspaceDir(context)
        val file = File(workspace, relativePath)
        if (!file.exists()) {
            return false
        }
        val content = file.readText()
        if (!content.contains(target)) {
            return false
        }
        val updatedContent = content.replace(target, replacement)
        file.writeText(updatedContent)
        return true
    }

    // Import from SAF Tree Uri into the local workspace
    fun importFromSaf(context: Context, treeUri: Uri): Int {
        val workspace = getWorkspaceDir(context)
        // Clear workspace first (except perhaps special hidden files)
        workspace.deleteRecursively()
        workspace.mkdirs()

        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return 0
        var filesCopied = 0

        fun copyRecursive(docFile: DocumentFile, currentRelativePath: String) {
            if (docFile.isDirectory) {
                val subDir = File(workspace, currentRelativePath)
                subDir.mkdirs()
                docFile.listFiles().forEach { child ->
                    val childPath = if (currentRelativePath.isEmpty()) child.name ?: "" else "$currentRelativePath/${child.name}"
                    copyRecursive(child, childPath)
                }
            } else if (docFile.isFile) {
                try {
                    val destFile = File(workspace, currentRelativePath)
                    destFile.parentFile?.mkdirs()
                    context.contentResolver.openInputStream(docFile.uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    filesCopied++
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        rootDoc.listFiles().forEach { child ->
            copyRecursive(child, child.name ?: "")
        }
        return filesCopied
    }

    // Export local workspace into SAF Tree Uri
    fun exportToSaf(context: Context, treeUri: Uri): Int {
        val workspace = getWorkspaceDir(context)
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return 0
        var filesCopied = 0

        // Helper to find or create document subdirectories and files
        fun getOrCreateFile(parent: DocumentFile, parts: List<String>, isDir: Boolean): DocumentFile? {
            var current: DocumentFile = parent
            for (i in parts.indices) {
                val part = parts[i]
                val isLast = i == parts.size - 1
                val existing = current.findFile(part)
                if (existing != null) {
                    current = existing
                } else {
                    current = if (isLast && !isDir) {
                        current.createFile("text/plain", part) ?: return null
                    } else {
                        current.createDirectory(part) ?: return null
                    }
                }
            }
            return current
        }

        workspace.walkTopDown().forEach { file ->
            if (file.isFile) {
                val relativePath = file.relativeTo(workspace).path
                val parts = relativePath.split(File.separator)
                try {
                    val destDoc = getOrCreateFile(rootDoc, parts, isDir = false)
                    if (destDoc != null) {
                        context.contentResolver.openOutputStream(destDoc.uri, "rwt")?.use { output ->
                            file.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        filesCopied++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return filesCopied
    }
}
