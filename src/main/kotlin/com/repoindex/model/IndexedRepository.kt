package com.repoindex.model

import org.openrewrite.SourceFile
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class IndexedRepository(
    val id: String = UUID.randomUUID().toString(),
    val path: String,
    val name: String,
    val indexedAt: Instant = Instant.now(),
    val sourceFiles: MutableList<SourceFile> = mutableListOf(),
    val languageBreakdown: MutableMap<String, Int> = ConcurrentHashMap()
) {
    val fileCount: Int get() = sourceFiles.size

    fun toSummary() = RepositorySummary(
        id = id,
        path = path,
        name = name,
        indexedAt = indexedAt,
        fileCount = fileCount,
        languageBreakdown = languageBreakdown.toMap()
    )
}

data class RepositorySummary(
    val id: String,
    val path: String,
    val name: String,
    val indexedAt: Instant,
    val fileCount: Int,
    val languageBreakdown: Map<String, Int>
)
