package com.example.workspace

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

data class InspectionResult(
    val fileType: String,
    val details: String,
    val contents: String,
    val zipEntries: List<String> = emptyList()
)

object Decompiler {

    fun inspectFile(file: File): InspectionResult {
        if (!file.exists()) {
            return InspectionResult("Unknown", "File not found", "The specified file does not exist.")
        }
        if (file.isDirectory) {
            return InspectionResult("Directory", "Directory path", "Name: ${file.name}\nPath: ${file.absolutePath}\nFiles: ${file.list()?.size ?: 0}")
        }

        val name = file.name.lowercase()
        return try {
            when {
                name.endsWith(".apk") || name.endsWith(".apks") || name.endsWith(".apkm") || name.endsWith(".aab") || name.endsWith(".zip") -> {
                    inspectZipArchive(file)
                }
                name.endsWith(".dex") -> {
                    val bytes = file.readBytes()
                    val details = "Dalvik Executable (DEX) file\nSize: ${bytes.size} bytes"
                    val contents = DexParser.parse(bytes)
                    InspectionResult("DEX", details, contents)
                }
                name.endsWith(".so") || name.equals("elf") || hasElfMagic(file) -> {
                    val bytes = file.readBytes()
                    val details = "Native Shared Library / ELF file\nSize: ${bytes.size} bytes"
                    val contents = ElfParser.parse(bytes)
                    InspectionResult("ELF", details, contents)
                }
                name.endsWith(".arsc") -> {
                    val bytes = file.readBytes()
                    val details = "Compiled Android Resource Table (ARSC)\nSize: ${bytes.size} bytes"
                    val contents = ArscParser.parse(bytes)
                    InspectionResult("ARSC", details, contents)
                }
                name.endsWith(".xml") && isBinaryXml(file) -> {
                    val bytes = file.readBytes()
                    val details = "Android Binary XML file (Compiled)\nSize: ${bytes.size} bytes"
                    val contents = BinaryXmlParser.parse(bytes)
                    InspectionResult("Binary XML", details, contents)
                }
                isTextFile(file) -> {
                    val text = file.readText(Charsets.UTF_8)
                    InspectionResult("Text/Code", "Plain Text / Code File\nSize: ${file.length()} bytes", text)
                }
                else -> {
                    // Try to show hex dump
                    val bytes = file.readBytes()
                    val hexDump = generateHexDump(bytes, 1024)
                    InspectionResult("Binary", "Raw Binary File\nSize: ${file.length()} bytes", hexDump)
                }
            }
        } catch (e: Exception) {
            InspectionResult("Error", "Failed to inspect file: ${file.name}", "Error occurred during extraction or parsing:\n${e.stackTraceToString()}")
        }
    }

    private fun hasElfMagic(file: File): Boolean {
        return try {
            val bytes = ByteArray(4)
            file.inputStream().use { it.read(bytes) }
            bytes[0] == 0x7F.toByte() && bytes[1] == 'E'.toByte() && bytes[2] == 'L'.toByte() && bytes[3] == 'F'.toByte()
        } catch (e: Exception) {
            false
        }
    }

    private fun isBinaryXml(file: File): Boolean {
        return try {
            val bytes = ByteArray(4)
            file.inputStream().use { it.read(bytes) }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            buffer.int == 0x00080003
        } catch (e: Exception) {
            false
        }
    }

    private fun isTextFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        val textExtensions = setOf("kts", "kt", "java", "xml", "txt", "md", "json", "gradle", "properties", "sh", "bat", "html", "css", "js", "cpp", "h", "c", "py", "prof", "profm")
        if (textExtensions.contains(ext)) return true

        // Read first 512 bytes to scan for control characters
        return try {
            val bytes = ByteArray(minOf(file.length().toInt(), 512))
            file.inputStream().use { it.read(bytes) }
            var textChars = 0
            var binaryChars = 0
            for (b in bytes) {
                val byteVal = b.toInt() and 0xFF
                if (byteVal == 0) return false // null char implies binary
                if (byteVal == 9 || byteVal == 10 || byteVal == 13 || byteVal in 32..126) {
                    textChars++
                } else if (byteVal < 32 || byteVal > 127) {
                    binaryChars++
                }
            }
            if (textChars + binaryChars == 0) return true
            (textChars.toFloat() / (textChars + binaryChars)) > 0.85
        } catch (e: Exception) {
            false
        }
    }

    private fun generateHexDump(bytes: ByteArray, maxBytes: Int): String {
        val sb = StringBuilder()
        sb.append("HEX DUMP (showing first $maxBytes bytes):\n")
        val end = minOf(bytes.size, maxBytes)
        for (i in 0 until end step 16) {
            sb.append(String.format("%08X  ", i))
            val lineBytesEnd = minOf(i + 16, end)
            for (j in i until i + 16) {
                if (j < lineBytesEnd) {
                    sb.append(String.format("%02X ", bytes[j]))
                } else {
                    sb.append("   ")
                }
            }
            sb.append(" |")
            for (j in i until lineBytesEnd) {
                val c = bytes[j].toChar()
                if (c in ' '..'~') {
                    sb.append(c)
                } else {
                    sb.append('.')
                }
            }
            sb.append("|\n")
        }
        if (bytes.size > maxBytes) {
            sb.append("... [truncated, ${bytes.size - maxBytes} more bytes]")
        }
        return sb.toString()
    }

    private fun inspectZipArchive(file: File): InspectionResult {
        val zipFile = ZipFile(file)
        val entries = mutableListOf<String>()
        val count = zipFile.size()
        
        val list = zipFile.entries()
        while (list.hasMoreElements()) {
            val element = list.nextElement()
            entries.add(element.name)
        }
        
        val details = "Archive Package File (${file.extension.uppercase()})\nTotal nested files: $count\nSize: ${file.length()} bytes"
        val sb = StringBuilder()
        sb.append("Archive Root Contents Listing:\n")
        sb.append("-----------------------------\n")
        entries.sorted().take(500).forEach {
            sb.append("- $it\n")
        }
        if (entries.size > 500) {
            sb.append("... and ${entries.size - 500} more file entries inside.")
        }
        
        return InspectionResult(
            fileType = "ARCHIVE",
            details = details,
            contents = sb.toString(),
            zipEntries = entries
        )
    }

    // Extraction helper to pull a file out of an APK/ZIP
    fun extractZipEntry(zipFile: File, entryName: String, destFile: File) {
        val zip = ZipFile(zipFile)
        val entry = zip.getEntry(entryName) ?: throw IllegalArgumentException("Entry not found: $entryName")
        zip.getInputStream(entry).use { input ->
            destFile.parentFile?.mkdirs()
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    // ==========================================
    // PARSERS
    // ==========================================

    object BinaryXmlParser {
        fun parse(bytes: ByteArray): String {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (bytes.size < 8) return "File too small to be Binary XML"
            val magic = buffer.int
            if (magic != 0x00080003) {
                return "Invalid Android Binary XML Magic: 0x${Integer.toHexString(magic)}"
            }
            val fileSize = buffer.int
            val sb = StringBuilder()
            sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")

            var indent = 0
            val strings = mutableListOf<String>()

            fun getString(id: Int): String {
                if (id in strings.indices) return strings[id]
                return "id_$id"
            }

            try {
                while (buffer.hasRemaining()) {
                    val chunkType = buffer.int
                    val chunkSize = buffer.int
                    val chunkStart = buffer.position() - 8

                    when (chunkType) {
                        0x001C0001 -> { // String Pool
                            val stringCount = buffer.int
                            val styleCount = buffer.int
                            val flags = buffer.int
                            val stringStart = buffer.int
                            val styleStart = buffer.int
                            val stringOffsets = IntArray(stringCount) { buffer.int }
                            val styleOffsets = IntArray(styleCount) { buffer.int }

                            val stringDataPos = chunkStart + stringStart
                            val isUtf8 = (flags and (1 shl 8)) != 0

                            for (i in 0 until stringCount) {
                                buffer.position(stringDataPos + stringOffsets[i])
                                val length = if (isUtf8) {
                                    var len = buffer.get().toInt() and 0xFF
                                    if ((len and 0x80) != 0) {
                                        len = ((len and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
                                    }
                                    var byteLen = buffer.get().toInt() and 0xFF
                                    if ((byteLen and 0x80) != 0) {
                                        byteLen = ((byteLen and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
                                    }
                                    byteLen
                                } else {
                                    var len = buffer.short.toInt() and 0xFFFF
                                    if ((len and 0x8000) != 0) {
                                        len = ((len and 0x7FFF) shl 16) or (buffer.short.toInt() and 0xFFFF)
                                    }
                                    len * 2
                                }

                                val strBytes = ByteArray(length)
                                buffer.get(strBytes)
                                val str = if (isUtf8) String(strBytes, Charsets.UTF_8) else String(strBytes, Charsets.UTF_16LE)
                                strings.add(str)
                            }
                        }
                        0x00080180 -> { // Resource IDs
                            // Skip resource IDs
                        }
                        0x00100102 -> { // Start Element
                            val line = buffer.int
                            val comment = buffer.int
                            val nsId = buffer.int
                            val nameId = buffer.int
                            val attributeStart = buffer.short
                            val attributeSize = buffer.short
                            val attributeCount = buffer.short
                            val idIndex = buffer.short
                            val classIndex = buffer.short
                            val styleIndex = buffer.short

                            val ns = if (nsId != -1) getString(nsId) else ""
                            val name = getString(nameId)

                            sb.append("  ".repeat(indent)).append("<").append(name)
                            if (ns.isNotBlank() && ns.contains("android") && indent == 0) {
                                sb.append(" xmlns:android=\"http://schemas.android.com/apk/res/android\"")
                            }

                            // Attributes
                            for (i in 0 until attributeCount.toInt()) {
                                val attrNsId = buffer.int
                                val attrNameId = buffer.int
                                val attrRawValueId = buffer.int
                                val attrSize = buffer.short
                                val attrRes = buffer.get()
                                val attrType = buffer.get()
                                val attrValueData = buffer.int

                                val attrName = getString(attrNameId)
                                val attrValue = if (attrRawValueId != -1) {
                                    getString(attrRawValueId)
                                } else {
                                    when (attrType.toInt() and 0xFF) {
                                        0x03 -> getString(attrValueData)
                                        0x10 -> attrValueData.toString()
                                        0x11 -> "0x" + Integer.toHexString(attrValueData)
                                        0x12 -> if (attrValueData != 0) "true" else "false"
                                        else -> "ref(0x${Integer.toHexString(attrValueData)})"
                                    }
                                }

                                sb.append("\n").append("  ".repeat(indent + 1))
                                if (attrNsId != -1 && getString(attrNsId).contains("android")) {
                                    sb.append("android:")
                                }
                                sb.append(attrName).append("=\"").append(attrValue).append("\"")
                            }
                            sb.append(">\n")
                            indent++
                        }
                        0x00100103 -> { // End Element
                            val line = buffer.int
                            val comment = buffer.int
                            val nsId = buffer.int
                            val nameId = buffer.int

                            val name = getString(nameId)
                            indent--
                            sb.append("  ".repeat(indent)).append("</").append(name).append(">\n")
                        }
                        0x00100104 -> { // Text
                            val line = buffer.int
                            val comment = buffer.int
                            val textId = buffer.int
                            val text = getString(textId)
                            sb.append("  ".repeat(indent)).append(text).append("\n")
                        }
                    }
                    buffer.position(chunkStart + chunkSize)
                }
            } catch (e: Exception) {
                sb.append("\n<!-- Parsing halted due to error: ${e.message} -->\n")
            }

            return sb.toString()
        }
    }

    object DexParser {
        fun parse(bytes: ByteArray): String {
            if (bytes.size < 112) return "File too small to be a DEX file."
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(8)
            buffer.get(magic)
            val magicStr = String(magic)
            if (!magicStr.startsWith("dex")) {
                return "Invalid DEX magic: $magicStr"
            }

            val checksum = buffer.int
            val signature = ByteArray(20)
            buffer.get(signature)
            val fileSize = buffer.int
            val headerSize = buffer.int
            val endianTag = buffer.int
            val linkSize = buffer.int
            val linkOff = buffer.int
            val mapOff = buffer.int
            val stringIdsSize = buffer.int
            val stringIdsOff = buffer.int
            val typeIdsSize = buffer.int
            val typeIdsOff = buffer.int
            val protoIdsSize = buffer.int
            val protoIdsOff = buffer.int
            val fieldIdsSize = buffer.int
            val fieldIdsOff = buffer.int
            val methodIdsSize = buffer.int
            val methodIdsOff = buffer.int
            val classDefsSize = buffer.int
            val classDefsOff = buffer.int
            val dataSize = buffer.int
            val dataOff = buffer.int

            val sb = StringBuilder()
            sb.append("DEX File Header Analytics:\n")
            sb.append("==========================\n")
            sb.append("Magic Type: ${magicStr.trim()}\n")
            sb.append("Size in Bytes: $fileSize\n")
            sb.append("String Identifiers: $stringIdsSize (Off: $stringIdsOff)\n")
            sb.append("Type Descriptors: $typeIdsSize (Off: $typeIdsOff)\n")
            sb.append("Proto Definitions: $protoIdsSize (Off: $protoIdsOff)\n")
            sb.append("Fields Listed: $fieldIdsSize (Off: $fieldIdsOff)\n")
            sb.append("Methods Counted: $methodIdsSize (Off: $methodIdsOff)\n")
            sb.append("Classes Defined: $classDefsSize (Off: $classDefsOff)\n\n")

            val stringOffsets = IntArray(minOf(stringIdsSize, 15000))
            if (stringIdsOff in 0 until bytes.size) {
                buffer.position(stringIdsOff)
                for (i in stringOffsets.indices) {
                    if (buffer.remaining() >= 4) {
                        stringOffsets[i] = buffer.int
                    }
                }
            }

            fun readUleb128(buf: ByteBuffer): Int {
                var result = 0
                var count = 0
                var b: Int
                do {
                    b = buf.get().toInt() and 0xFF
                    result = result or ((b and 0x7F) shl (count * 7))
                    count++
                } while ((b and 0x80) != 0 && count < 5)
                return result
            }

            fun getString(id: Int): String {
                if (id !in stringOffsets.indices) return "str_$id"
                val offset = stringOffsets[id]
                if (offset !in 0 until bytes.size) return "invalid_offset_$offset"
                
                val tempBuffer = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                tempBuffer.position(offset)
                val utf16Len = readUleb128(tempBuffer)
                val out = ByteArrayOutputStream()
                while (tempBuffer.hasRemaining()) {
                    val b = tempBuffer.get()
                    if (b == 0.toByte()) break
                    out.write(b.toInt())
                }
                return String(out.toByteArray(), Charsets.UTF_8)
            }

            val typeIds = IntArray(minOf(typeIdsSize, 10000))
            if (typeIdsOff in 0 until bytes.size) {
                buffer.position(typeIdsOff)
                for (i in typeIds.indices) {
                    if (buffer.remaining() >= 4) {
                        typeIds[i] = buffer.int
                    }
                }
            }

            fun getTypeName(id: Int): String {
                if (id !in typeIds.indices) return "type_$id"
                return getString(typeIds[id])
            }

            sb.append("Classes Extracted (${minOf(classDefsSize, 200)} shown):\n")
            sb.append("-------------------------------------------\n")
            if (classDefsOff in 0 until bytes.size) {
                buffer.position(classDefsOff)
                for (i in 0 until minOf(classDefsSize, 200)) {
                    if (buffer.remaining() >= 32) {
                        val classIdx = buffer.int
                        val accessFlags = buffer.int
                        val superClassIdx = buffer.int
                        val interfacesOff = buffer.int
                        val sourceFileIdx = buffer.int
                        val annotationsOff = buffer.int
                        val classDataOff = buffer.int
                        val staticValuesOff = buffer.int

                        val className = getTypeName(classIdx)
                        val superName = if (superClassIdx != -1) getTypeName(superClassIdx) else "None"
                        
                        val flagsStr = buildString {
                            if (accessFlags and 0x0001 != 0) append("public ")
                            if (accessFlags and 0x0002 != 0) append("private ")
                            if (accessFlags and 0x0004 != 0) append("protected ")
                            if (accessFlags and 0x0008 != 0) append("static ")
                            if (accessFlags and 0x0010 != 0) append("final ")
                            if (accessFlags and 0x0200 != 0) append("interface ")
                            if (accessFlags and 0x0400 != 0) append("abstract ")
                        }

                        sb.append("- $flagsStr$className\n")
                        sb.append("  Super: $superName\n")
                        if (sourceFileIdx != -1) {
                            sb.append("  Source: ${getString(sourceFileIdx)}\n")
                        }
                    }
                }
                if (classDefsSize > 200) {
                    sb.append("... and ${classDefsSize - 200} more classes.")
                }
            }

            return sb.toString()
        }
    }

    object ElfParser {
        fun parse(bytes: ByteArray): String {
            if (bytes.size < 52) return "File is too small to be an ELF library."
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (bytes[0] != 0x7F.toByte() || bytes[1] != 'E'.toByte() || bytes[2] != 'L'.toByte() || bytes[3] != 'F'.toByte()) {
                return "Invalid ELF Magic header."
            }

            val is64Bit = bytes[4] == 2.toByte()
            val isLittleEndian = bytes[5] == 1.toByte()
            if (!isLittleEndian) {
                buffer.order(ByteOrder.BIG_ENDIAN)
            }

            buffer.position(16)
            val type = buffer.short
            val machine = buffer.short
            val version = buffer.int

            val typeStr = when (type.toInt()) {
                1 -> "Relocatable file (ET_REL)"
                2 -> "Executable file (ET_EXEC)"
                3 -> "Shared object file (ET_DYN)"
                4 -> "Core file (ET_CORE)"
                else -> "Unknown ($type)"
            }

            val machineStr = when (machine.toInt()) {
                3 -> "Intel 80386 (x86)"
                40 -> "ARM 32-bit"
                62 -> "AMD x86-64"
                183 -> "AArch64 (ARM 64-bit)"
                else -> "Unknown ($machine)"
            }

            val sb = StringBuilder()
            sb.append("ELF Native Compiled Library:\n")
            sb.append("============================\n")
            sb.append("Bitness: ${if (is64Bit) "64-bit" else "32-bit"}\n")
            sb.append("Endianness: ${if (isLittleEndian) "Little Endian" else "Big Endian"}\n")
            sb.append("ELF Header Type: $typeStr\n")
            sb.append("Processor Target: $machineStr\n")
            sb.append("Format Version: $version\n\n")

            // Scan strings
            sb.append("Printable Strings Extracted (len >= 6, max 120):\n")
            sb.append("-----------------------------------------------\n")
            var currentString = StringBuilder()
            var stringsCount = 0
            for (i in bytes.indices) {
                val b = bytes[i].toInt() and 0xFF
                if (b in 32..126) {
                    currentString.append(b.toChar())
                } else {
                    if (currentString.length >= 6) {
                        sb.append("- ${currentString.toString()}\n")
                        stringsCount++
                        if (stringsCount >= 120) break
                    }
                    currentString = StringBuilder()
                }
            }
            return sb.toString()
        }
    }

    object ArscParser {
        fun parse(bytes: ByteArray): String {
            if (bytes.size < 12) return "File is too small to be resources.arsc"
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val type = buffer.short
            val headerSize = buffer.short
            val size = buffer.int
            val packageCount = buffer.int

            val sb = StringBuilder()
            sb.append("Android Resource Table Analysis:\n")
            sb.append("================================\n")
            sb.append("Chunk Header Type: 0x${Integer.toHexString(type.toInt())}\n")
            sb.append("Header Size: $headerSize bytes\n")
            sb.append("Declared Table Size: $size bytes\n")
            sb.append("Target Asset Packages: $packageCount\n")
            return sb.toString()
        }
    }
}
