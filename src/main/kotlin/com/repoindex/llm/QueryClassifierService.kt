package com.repoindex.llm

import com.repoindex.recipe.RecipeCatalog
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Service

/**
 * Uses the LLM to classify a user query against the pre-built recipe catalog.
 * Returns which strategy to use: direct catalog match, composition of multiple recipes,
 * or fall back to dynamic recipe generation.
 */
@Service
class QueryClassifierService(
    chatClientBuilder: ChatClient.Builder,
    private val recipeCatalog: RecipeCatalog
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val chatClient = chatClientBuilder.build()

    data class ClassificationResult(
        val strategy: Strategy,
        val recipeIds: List<String> = emptyList(),
        val searchTerms: List<String> = emptyList()
    ) {
        enum class Strategy { CATALOG, COMPOSE, ANALYZE, GENERATE }
    }

    fun classify(query: String): ClassificationResult {
        val catalogDescription = recipeCatalog.toCatalogDescription()

        val prompt = """
            |You are a query classifier for a code analysis tool. Your job is to determine whether
            |a user's question can be answered using pre-built analysis recipes, or if a new recipe
            |needs to be generated.
            |
            |## Available Recipes
            |$catalogDescription
            |
            |## User Query
            |$query
            |
            |## Instructions
            |Analyze the user's query and respond with EXACTLY one of these formats:
            |
            |1. If ONE recipe can answer the query:
            |   CATALOG: <recipe-id>
            |
            |2. If MULTIPLE recipes combined can answer the query:
            |   COMPOSE: <recipe-id-1>, <recipe-id-2>
            |
            |3. If the query is a broad, domain-specific or conceptual question (e.g., "how do we manage
            |   currencies?", "what is the error handling strategy?", "how is authentication implemented?"),
            |   respond with ANALYZE followed by relevant search terms that should be used to filter code
            |   analysis findings. The search terms should be type names, annotation names, import prefixes,
            |   method name fragments, or keywords that relate to the concept:
            |   ANALYZE: <term1>, <term2>, <term3>, ...
            |
            |4. If NO existing recipe or analysis strategy can answer the query:
            |   GENERATE
            |
            |Rules:
            |- Prefer CATALOG over COMPOSE, COMPOSE over ANALYZE, and ANALYZE over GENERATE.
            |- Only select recipes that are truly relevant to the query.
            |- If the query asks about "classes" or "how many classes", use list-classes.
            |- If the query asks about both classes AND methods, use COMPOSE with both recipe IDs.
            |- If the query needs specific filtering (e.g., "classes that have more than 5 methods"),
            |  use GENERATE because pre-built recipes can't filter that way.
            |- Use ANALYZE for open-ended questions about how the codebase handles a domain concept
            |  (e.g., currencies, authentication, logging, caching, error handling).
            |  Include 5-15 search terms: type names (e.g., Currency, Money, BigDecimal),
            |  package prefixes (e.g., javax.money, java.util.Currency), and method name
            |  fragments (e.g., convert, exchange, format).
            |- Respond with ONLY the classification line, nothing else.
        """.trimMargin()

        log.info("Classifying query: {}", query)

        return try {
            val response = chatClient.prompt()
                .user(prompt)
                .call()
                .content()?.trim() ?: "GENERATE"

            parseClassification(response)
        } catch (e: Exception) {
            log.warn("Classification failed, falling back to GENERATE: {}", e.message)
            ClassificationResult(strategy = ClassificationResult.Strategy.GENERATE)
        }
    }

    private fun parseClassification(response: String): ClassificationResult {
        val trimmed = response.trim().lines().first().trim()
        log.info("Classification response: {}", trimmed)

        return when {
            trimmed.startsWith("CATALOG:") -> {
                val id = trimmed.removePrefix("CATALOG:").trim()
                if (recipeCatalog.findById(id) != null) {
                    ClassificationResult(
                        strategy = ClassificationResult.Strategy.CATALOG,
                        recipeIds = listOf(id)
                    )
                } else {
                    log.warn("LLM returned unknown recipe ID '{}', falling back to GENERATE", id)
                    ClassificationResult(strategy = ClassificationResult.Strategy.GENERATE)
                }
            }
            trimmed.startsWith("COMPOSE:") -> {
                val ids = trimmed.removePrefix("COMPOSE:").trim()
                    .split(",")
                    .map { it.trim() }
                    .filter { recipeCatalog.findById(it) != null }
                if (ids.size >= 2) {
                    ClassificationResult(
                        strategy = ClassificationResult.Strategy.COMPOSE,
                        recipeIds = ids
                    )
                } else if (ids.size == 1) {
                    ClassificationResult(
                        strategy = ClassificationResult.Strategy.CATALOG,
                        recipeIds = ids
                    )
                } else {
                    log.warn("COMPOSE had no valid recipe IDs, falling back to GENERATE")
                    ClassificationResult(strategy = ClassificationResult.Strategy.GENERATE)
                }
            }
            trimmed.startsWith("ANALYZE:") -> {
                val terms = trimmed.removePrefix("ANALYZE:").trim()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                if (terms.isNotEmpty()) {
                    ClassificationResult(
                        strategy = ClassificationResult.Strategy.ANALYZE,
                        searchTerms = terms
                    )
                } else {
                    log.warn("ANALYZE had no search terms, falling back to GENERATE")
                    ClassificationResult(strategy = ClassificationResult.Strategy.GENERATE)
                }
            }
            else -> {
                ClassificationResult(strategy = ClassificationResult.Strategy.GENERATE)
            }
        }
    }
}
