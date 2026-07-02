package com.example.network

import java.util.regex.Pattern

data class ToolCall(
    val name: String,
    val path: String = "",
    val key: String = "",
    val server: String = "",
    val method: String = "",
    val textContent: String = "",
    val target: String = "",
    val replacement: String = ""
)

object AgentParser {

    // Parses any XML tool calls present in the text
    fun parseToolCalls(text: String): List<ToolCall> {
        val calls = mutableListOf<ToolCall>()
        
        // Match <tool ...>...</tool> or self-closing <tool ... />
        // Using regex pattern matching for robust Android compatibility
        val toolPattern = Pattern.compile("<tool\\s+([^>]*?)(?:/>|>([\\s\\S]*?)</tool>)")
        val matcher = toolPattern.matcher(text)

        while (matcher.find()) {
            val attributesString = matcher.group(1) ?: ""
            val innerContent = matcher.group(2) ?: ""

            // Parse attributes
            val attributes = parseAttributes(attributesString)
            val name = attributes["name"] ?: continue

            var target = ""
            var replacement = ""

            // If it's edit_file, we need to extract <target> and <replacement> tags
            if (name == "edit_file") {
                val targetMatcher = Pattern.compile("<target>([\\s\\S]*?)</target>").matcher(innerContent)
                val replacementMatcher = Pattern.compile("<replacement>([\\s\\S]*?)</replacement>").matcher(innerContent)
                
                if (targetMatcher.find()) {
                    target = targetMatcher.group(1) ?: ""
                }
                if (replacementMatcher.find()) {
                    replacement = replacementMatcher.group(1) ?: ""
                }
            }

            calls.add(
                ToolCall(
                    name = name,
                    path = attributes["path"] ?: "",
                    key = attributes["key"] ?: "",
                    server = attributes["server"] ?: "",
                    method = attributes["method"] ?: "",
                    textContent = if (name == "edit_file") "" else innerContent.trim(),
                    target = target,
                    replacement = replacement
                )
            )
        }

        return calls
    }

    private fun parseAttributes(attributesString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val pattern = Pattern.compile("(\\w+)\\s*=\\s*\"([^\"]*)\"")
        val matcher = pattern.matcher(attributesString)
        while (matcher.find()) {
            val key = matcher.group(1) ?: continue
            val value = matcher.group(2) ?: ""
            map[key] = value
        }
        return map
    }
}
