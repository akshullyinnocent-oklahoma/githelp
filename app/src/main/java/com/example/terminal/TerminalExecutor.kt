package com.example.terminal

import android.content.Context
import com.example.workspace.WorkspaceManager
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TerminalExecutor {
    private const val TAG = "TerminalExecutor"

    suspend fun executeCommand(context: Context, command: String): String {
        return withContext(Dispatchers.IO) {
            val workspaceDir = WorkspaceManager.getWorkspaceDir(context)
            try {
                // Initialize process. On Android, sh is always available under /system/bin/sh
                val process = ProcessBuilder()
                    .directory(workspaceDir)
                    .command("sh", "-c", command)
                    .redirectErrorStream(true) // Merge stdout and stderr
                    .start()

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = StringBuilder()
                var line: String?
                
                // Read lines from output stream
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }

                val exitCode = process.waitFor()
                if (output.isEmpty()) {
                    "Command executed successfully with exit code $exitCode (no output)"
                } else {
                    "Exit Code: $exitCode\n\n$output"
                }
            } catch (e: Exception) {
                "Error executing command: ${e.message}\nEnsure your Android device supports standard command utilities. (Termux packages can be configured externally or accessed if termux-exec/PATH is linked)"
            }
        }
    }
}
