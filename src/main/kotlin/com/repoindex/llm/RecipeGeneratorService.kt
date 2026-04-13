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

    fun fixRecipeCode(originalCode: String, error: String, attempt: Int): String {
        val prompt = """
            |The following OpenRewrite recipe code failed to compile.
            |
            |## Original Code
            |```kotlin
            |$originalCode
            |```
            |
            |## Compilation Error
            |```
            |$error
            |```
            |
            |## Instructions
            |Fix the code to resolve the compilation error. Return ONLY the corrected Kotlin code.
            |This is attempt $attempt. Make sure the fix is correct.
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

    fun formatAnswer(query: String, findings: List<String>): String {
        if (findings.isEmpty()) {
            return "No results found for your query: \"$query\""
        }

        val prompt = """
            |The user asked: "$query"
            |
            |The code analysis produced these findings:
            |${findings.joinToString("\n") { "- $it" }}
            |
            |Provide a clear, concise summary answering the user's question based on these findings.
            |Use markdown formatting for readability.
        """.trimMargin()

        return chatClient.prompt()
            .user(prompt)
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
            |- Use `SearchResult.found()` marker to mark found elements
            |- Access type info via `tree.type`, `method.methodType`, etc.
            |
            |For collecting results, add findings to the ExecutionContext:
            |```kotlin
            |executionContext.putMessage("finding", "description of what was found")
            |```
            |
            |Or use markers:
            |```kotlin
            |SearchResult.found(tree, "description")
            |```
        """.trimMargin()
    }
}
