package com.utophii.config

import java.io.File
import java.nio.file.Files

internal class EffectFile private constructor(val diskFile: File, val relativePath: String) {
    private val namespace = relativePath.substringBefore('/', "")
    private val directory = relativePath.substringBeforeLast('/', "").substringAfter('/', "")

    fun id(configured: Any?): String {
        val name = configured?.toString()?.takeIf(String::isNotBlank) ?: diskFile.nameWithoutExtension
        if (namespace.isEmpty()) return name
        return if (directory.isEmpty()) "$namespace:$name" else "$namespace:$directory/$name"
    }

    fun reference(name: String): String = when {
        namespace.isEmpty() -> name
        name.startsWith(':') -> name.drop(1)
        ':' in name -> name
        else -> "$namespace:$name"
    }

    companion object {
        fun discover(root: File): List<EffectFile> = root.walkTopDown()
            .onEnter { it == root || !Files.isSymbolicLink(it.toPath()) }
            .filter { it.isFile && it.extension.equals("yml", ignoreCase = true) }
            .map { EffectFile(it, it.relativeTo(root).invariantSeparatorsPath) }
            .sortedBy { it.relativePath }
            .toList()

        fun from(root: File, file: File): EffectFile {
            val path = file.absoluteFile.normalize()
            val base = root.absoluteFile.normalize()
            val relative = if (path.startsWith(base)) path.relativeTo(base).invariantSeparatorsPath else file.name
            return EffectFile(file, relative)
        }
    }
}