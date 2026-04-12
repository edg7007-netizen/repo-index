package com.repoindex.model

import java.time.Instant

data class ChatMessage(
    val role: Role,
    val content: String,
    val timestamp: Instant = Instant.now()
) {
    enum class Role { USER, ASSISTANT, SYSTEM }
}

data class QueryRequest(
    val repositoryId: String,
    val query: String
)

data class QueryResponse(
    val answer: String,
    val generatedRecipe: String? = null,
    val executionResults: List<RecipeResult> = emptyList(),
    val error: String? = null
)

data class RecipeResult(
    val filePath: String,
    val matches: List<String> = emptyList(),
    val changes: List<String> = emptyList()
)

data class IndexRequest(
    val path: String
)
