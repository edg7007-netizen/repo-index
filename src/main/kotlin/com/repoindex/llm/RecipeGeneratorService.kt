package com.repoindex.llm

import com.repoindex.model.IndexedRepository
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Service

@Service
class RecipeGeneratorService(
    chatClientBuilder: ChatClient.Builder
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val chatClient = chatClientBuilder.build()

    fun generateRecipeCode(query: String, repo: IndexedRepository): String {
        val fileList = repo.sourceFiles
            .take(50)
            .joinToString("\n") { "  - ${it.sourcePath}" }

        val prompt = """
            |$SYSTEM_PROMPT
            |
            |## Repository Context
            |Repository: ${repo.name}
            |Languages: ${repo.languageBreakdown.entries.joinToString { "${it.key}: ${it.value} files" }}
            |Sample files:
            |$fileList
            |
            |## User Query
            |$query
            |
            |## Instructions
            |Generate a single Kotlin class that extends `org.openrewrite.Recipe` and contains the appropriate
            |visitor(s) to answer the user's query. The recipe should collect results into a list of findings.
            |
            |Return ONLY the Kotlin code, no explanation. The code must:
            |1. Be a complete, compilable Kotlin class
            |2. Have a unique recipe name based on the query
            |3. Use `org.openrewrite.java.JavaIsoVisitor` for Java/Kotlin AST traversal
            |4. Collect findings via marker or print statements that can be captured
            |5. Do NOT include a `package` declaration — the code will be evaluated as a Kotlin script
            |6. Do NOT include import statements for `org.openrewrite.*`, `org.openrewrite.java.*`,
            |   `org.openrewrite.java.tree.*`, `org.openrewrite.kotlin.tree.*`, or
            |   `org.openrewrite.marker.SearchResult` — these are already imported
        """.trimMargin()

        log.info("Generating recipe for query: {}", query)

        val response = chatClient.prompt()
            .user(prompt)
            .call()
            .content() ?: throw RuntimeException("LLM returned empty response")

        return extractKotlinCode(response)
    }

    fun fixRecipeCode(
        originalCode: String,
        error: String,
        attempt: Int,
        query: String = "",
        previousErrors: List<String> = emptyList()
    ): String {
        val truncatedError = truncateError(error)

        val previousAttemptsSection = if (previousErrors.isNotEmpty()) {
            val history = previousErrors.mapIndexed { i, err ->
                "Attempt ${i + 1}: ${truncateError(err)}"
            }.joinToString("\n|")
            """
            |
            |## Previous Failed Attempts
            |The following fixes were already tried and FAILED. Do NOT repeat the same approach.
            |$history
            """.trimIndent()
        } else ""

        val querySection = if (query.isNotBlank()) {
            """
            |
            |## Original User Query
            |The recipe was generated to answer this question: "$query"
            |Make sure the fixed recipe still addresses this query.
            """.trimIndent()
        } else ""

        val prompt = """
            |The following OpenRewrite recipe code failed to compile.
            |$querySection
            |
            |## Original Code
            |```kotlin
            |$originalCode
            |```
            |
            |## Compilation Error (Attempt $attempt)
            |```
            |$truncatedError
            |```
            |$previousAttemptsSection
            |
            |## Important API Rules
            |- `JavaIsoVisitor` does NOT have a generic `visit` method. Override specific methods like
            |  `visitClassDeclaration`, `visitMethodDeclaration`, `visitCompilationUnit`, etc.
            |- Each visitor method must return the SAME type it receives (e.g. `visitClassDeclaration` returns `J.ClassDeclaration`)
            |- Always call `super.visitXxx(node, p)` first, then return the result or a marked version
            |- Use `SearchResult.found(node, "description")` to mark findings
            |- Do NOT use methods or types that don't exist in the OpenRewrite API
            |- `J.ClassDeclaration` uses `.extends` and `.implements` (not `extends_` or `implements_`)
            |- `SearchResult.found()` returns a platform type — always cast the result (e.g. `as J.ClassDeclaration`)
            |- `super.visitXxx()` returns a platform type — always use `!!` on the result
            |
            |## Working Example
            |```kotlin
            |class CountClassesRecipe : Recipe() {
            |    override fun getDisplayName() = "Count Classes"
            |    override fun getDescription() = "Counts class declarations."
            |    override fun getVisitor(): TreeVisitor<*, ExecutionContext> {
            |        return object : JavaIsoVisitor<ExecutionContext>() {
            |            override fun visitClassDeclaration(classDecl: J.ClassDeclaration, p: ExecutionContext): J.ClassDeclaration {
            |                val cd = super.visitClassDeclaration(classDecl, p)!!
            |                return SearchResult.found(cd, "Class: ${'$'}{cd.simpleName}") as J.ClassDeclaration
            |            }
            |        }
            |    }
            |}
            |```
            |
            |## Instructions
            |Fix the code to resolve the compilation error. Return ONLY the corrected Kotlin code.
            |This is attempt $attempt. The previous approach did not work — try a DIFFERENT fix strategy.
            |
            |The corrected code must:
            |1. Be a COMPLETE, compilable Kotlin class that extends `org.openrewrite.Recipe`
            |2. Do NOT include a `package` declaration — the code will be evaluated as a Kotlin script
            |3. Do NOT include import statements for `org.openrewrite.*`, `org.openrewrite.java.*`,
            |   `org.openrewrite.java.tree.*`, `org.openrewrite.kotlin.tree.*`, or
            |   `org.openrewrite.marker.SearchResult` — these are already imported
            |4. Include ALL closing braces and ensure no code is truncated
        """.trimMargin()

        val response = chatClient.prompt()
            .user(prompt)
            .call()
            .content() ?: throw RuntimeException("LLM returned empty response for fix attempt $attempt")

        return extractKotlinCode(response)
    }

    /**
     * Truncates excessively long error messages (e.g. ScriptException stack traces)
     * to help the LLM focus on the key error information.
     */
    private fun truncateError(error: String, maxLines: Int = 30, maxLength: Int = 2000): String {
        val lines = error.lines()
        val truncatedLines = if (lines.size > maxLines) {
            lines.take(maxLines) + listOf("... (${lines.size - maxLines} more lines truncated)")
        } else {
            lines
        }
        val result = truncatedLines.joinToString("\n")
        return if (result.length > maxLength) {
            result.take(maxLength) + "\n... (truncated)"
        } else {
            result
        }
    }

    fun formatAnswer(query: String, findings: List<String>): String {
        if (findings.isEmpty()) {
            return "No results found for your query: \"$query\""
        }

        val systemMessage = """
            |You are a code analysis assistant. You answer user questions about their codebase
            |based ONLY on the concrete findings provided below. These findings come from running
            |an automated code analysis recipe (OpenRewrite) against the user's repository.
            |
            |Rules:
            |- Answer ONLY based on the provided findings. Do NOT speculate or add information not in the findings.
            |- Do NOT mention pull requests, PRs, commits, diffs, or repository problems.
            |- If the findings are a list of items (e.g. classes, methods), summarize them clearly with a count.
            |- Keep your answer focused, concise, and directly relevant to the user's question.
            |- Use markdown formatting for readability.
        """.trimMargin()

        val userMessage = """
            |Question: "$query"
            |
            |Analysis findings (${findings.size} results):
            |${findings.joinToString("\n") { "- $it" }}
        """.trimMargin()

        return chatClient.prompt()
            .system(systemMessage)
            .user(userMessage)
            .call()
            .content() ?: findings.joinToString("\n")
    }

    private fun extractKotlinCode(response: String): String {
        // Extract code from markdown code blocks if present
        val codeBlockPattern = Regex("```(?:kotlin)?\\s*\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL)
        val match = codeBlockPattern.find(response)
        return match?.groupValues?.get(1)?.trim() ?: response.trim()
    }

    companion object {
        private val SYSTEM_PROMPT = """
            |You are an expert at writing OpenRewrite recipes in Kotlin.
            |You understand the OpenRewrite LST (Lossless Semantic Tree) API deeply.
            |
            |Key OpenRewrite concepts:
            |- `Recipe` is the base class for all transformations/analyses
            |- `JavaIsoVisitor<ExecutionContext>` is used to traverse Java/Kotlin ASTs
            |- Common LST types: `J.ClassDeclaration`, `J.MethodDeclaration`, `J.VariableDeclarations`,
            |  `J.FieldAccess`, `J.Identifier`, `J.MethodInvocation`, `J.Annotation`, `J.Import`,
            |  `J.Block`, `J.If`, `J.ForLoop`, `J.Return`, `J.Literal`, `J.NewClass`
            |- `JavaType.FullyQualified` for type information
            |- `TreeVisitor.Cursor` for navigating the tree context
            |- Use `SearchResult.found(tree, "description")` marker to mark found elements
            |- Access type info via `tree.type`, `method.methodType`, etc.
            |
            |IMPORTANT: `JavaIsoVisitor` does NOT have a generic `visit` method. You MUST override
            |specific visitor methods. Each visitor method receives the specific LST node type and
            |must return the SAME type. The correct method signatures are:
            |
            |- `override fun visitClassDeclaration(classDecl: J.ClassDeclaration, p: ExecutionContext): J.ClassDeclaration`
            |- `override fun visitMethodDeclaration(method: J.MethodDeclaration, p: ExecutionContext): J.MethodDeclaration`
            |- `override fun visitCompilationUnit(cu: J.CompilationUnit, p: ExecutionContext): J.CompilationUnit`
            |- `override fun visitVariableDeclarations(multiVariable: J.VariableDeclarations, p: ExecutionContext): J.VariableDeclarations`
            |- `override fun visitMethodInvocation(method: J.MethodInvocation, p: ExecutionContext): J.MethodInvocation`
            |- `override fun visitAnnotation(annotation: J.Annotation, p: ExecutionContext): J.Annotation`
            |- `override fun visitImport(import_: J.Import, p: ExecutionContext): J.Import`
            |
            |Here is a complete working example that counts classes:
            |```kotlin
            |class CountClassesRecipe : Recipe() {
            |    override fun getDisplayName() = "Count Classes"
            |    override fun getDescription() = "Counts class declarations."
            |    override fun getVisitor(): TreeVisitor<*, ExecutionContext> {
            |        return object : JavaIsoVisitor<ExecutionContext>() {
            |            override fun visitClassDeclaration(classDecl: J.ClassDeclaration, p: ExecutionContext): J.ClassDeclaration {
            |                val cd = super.visitClassDeclaration(classDecl, p)!!
            |                return SearchResult.found(cd, "Class: ${'$'}{cd.simpleName}") as J.ClassDeclaration
            |            }
            |        }
            |    }
            |}
            |```
            |
            |NEVER use `override fun visit(...)` — it does not exist.
            |Always call `super.visitXxx(node, p)` first, then return the result (or a marked version).
            |IMPORTANT: OpenRewrite is a Java library. `super.visitXxx()` and `SearchResult.found()` return
            |platform types that Kotlin may treat as nullable. Always use `!!` on `super.visitXxx()` calls
            |and cast the result of `SearchResult.found()` to the expected non-null type (e.g. `as J.ClassDeclaration`).
        """.trimMargin()
    }
}
