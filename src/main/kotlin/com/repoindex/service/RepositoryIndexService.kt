package com.repoindex.service

import com.repoindex.config.RepoIndexProperties
import com.repoindex.model.IndexedRepository
import com.repoindex.model.RepositorySummary
import org.openrewrite.InMemoryExecutionContext
import org.openrewrite.SourceFile
import org.openrewrite.java.JavaParser
import org.openrewrite.kotlin.KotlinParser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.pathString

@Service
class RepositoryIndexService(
    private val properties: RepoIndexProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val repositories = ConcurrentHashMap<String, IndexedRepository>()

    /** Set by Spring after AnalysisService is initialized (avoids circular dependency). */
    private var analysisService: AnalysisService? = null

    fun setAnalysisService(service: AnalysisService) {
        this.analysisService = service
    }

    fun indexRepository(repoPath: String): RepositorySummary {
        val path = Path.of(repoPath).toAbsolutePath().normalize()
        require(Files.isDirectory(path)) { "Path does not exist or is not a directory: $repoPath" }

        log.info("Indexing repository at: {}", path)

        val sourceFiles = collectSourceFiles(path)
        val languageBreakdown = mutableMapOf<String, Int>()
        val parsedFiles = mutableListOf<SourceFile>()

        val javaFiles = sourceFiles.filter { it.extension == "java" }
        val kotlinFiles = sourceFiles.filter { it.extension in listOf("kt", "kts") }
        val scalaFiles = sourceFiles.filter { it.extension == "scala" }

        if (javaFiles.isNotEmpty()) {
            log.info("Parsing {} Java files", javaFiles.size)
            val parsed = parseJavaFiles(javaFiles, path)
            parsedFiles.addAll(parsed)
            languageBreakdown["Java"] = javaFiles.size
        }

        if (kotlinFiles.isNotEmpty()) {
            log.info("Parsing {} Kotlin files", kotlinFiles.size)
            val parsed = parseKotlinFiles(kotlinFiles, path)
            parsedFiles.addAll(parsed)
            languageBreakdown["Kotlin"] = kotlinFiles.size
        }

        if (scalaFiles.isNotEmpty()) {
            log.info("Parsing {} Scala files (stored as raw source)", scalaFiles.size)
            // Scala files are tracked but stored as raw paths for now
            // Full Scala LST parsing can be added when rewrite-scala stabilizes
            languageBreakdown["Scala"] = scalaFiles.size
        }

        val repo = IndexedRepository(
            path = path.pathString,
            name = path.fileName.name,
            sourceFiles = parsedFiles,
            languageBreakdown = ConcurrentHashMap(languageBreakdown)
        )

        repositories[repo.id] = repo
        log.info("Indexed repository '{}': {} files parsed ({} total source files)",
            repo.name, parsedFiles.size, sourceFiles.size)

        // Auto-analyze: run standard recipe suite to pre-compute facts
        try {
            analysisService?.analyzeRepository(repo)
        } catch (e: Exception) {
            log.warn("Auto-analysis failed for '{}': {}", repo.name, e.message)
        }

        return repo.toSummary()
    }

    fun getRepository(id: String): IndexedRepository? = repositories[id]

    fun listRepositories(): List<RepositorySummary> = repositories.values.map { it.toSummary() }

    fun removeRepository(id: String): Boolean {
        val removed = repositories.remove(id) != null
        if (removed) {
            analysisService?.removeAnalysis(id)
        }
        return removed
    }

    private fun collectSourceFiles(root: Path): List<Path> {
        val maxSizeBytes = properties.index.maxFileSizeKb * 1024L
        val extensions = properties.index.supportedExtensions.map { it.removePrefix(".") }.toSet()

        return Files.walk(root)
            .filter { it.isRegularFile() }
            .filter { it.extension in extensions }
            .filter { Files.size(it) <= maxSizeBytes }
            .filter { !it.pathString.contains("/build/") }
            .filter { !it.pathString.contains("/target/") }
            .filter { !it.pathString.contains("/.gradle/") }
            .filter { !it.pathString.contains("/.git/") }
            .toList()
    }

    private fun parseJavaFiles(files: List<Path>, root: Path): List<SourceFile> {
        val ctx = InMemoryExecutionContext { t -> log.warn("Java parse error: {}", t.message) }
        return try {
            JavaParser.fromJavaVersion()
                .build()
                .parse(files, root, ctx)
                .map { it as SourceFile }
                .toList()
        } catch (e: Exception) {
            log.error("Failed to parse Java files: {}", e.message)
            emptyList()
        }
    }

    private fun parseKotlinFiles(files: List<Path>, root: Path): List<SourceFile> {
        val ctx = InMemoryExecutionContext { t -> log.warn("Kotlin parse error: {}", t.message) }
        return try {
            KotlinParser.builder()
                .build()
                .parse(files, root, ctx)
                .map { it as SourceFile }
                .toList()
        } catch (e: Exception) {
            log.error("Failed to parse Kotlin files: {}", e.message)
            emptyList()
        }
    }
}
