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
        // Fast path: deterministic keyword matching before LLM classification.
        // This handles common queries reliably even when the LLM returns verbose responses.
        val keywordMatch = classifyByKeywords(query)
        if (keywordMatch != null) {
            log.info("Keyword-based classification: {} with recipes {}", keywordMatch.strategy, keywordMatch.recipeIds)
            return keywordMatch
        }

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

            val classification = parseClassification(response)

            // Guard: if LLM returns CATALOG but the query has no keyword overlap with
            // the matched recipe, the classification is likely wrong (e.g., a domain-specific
            // question like "are we managing currencies?" being mapped to find-annotations).
            if (classification.strategy == ClassificationResult.Strategy.CATALOG) {
                val entry = recipeCatalog.findById(classification.recipeIds.first())
                if (entry != null) {
                    val lowerQuery = query.lowercase()
                    val hasOverlap = entry.keywords.any { kw -> lowerQuery.contains(kw.lowercase()) }
                    if (!hasOverlap) {
                        log.warn(
                            "LLM returned CATALOG:{} but query has no keyword overlap — rejecting",
                            entry.id
                        )
                        return ClassificationResult(strategy = ClassificationResult.Strategy.GENERATE)
                    }
                }
            }

            classification
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

    /**
     * Deterministic keyword-based classifier. Scores each catalog entry by how many
     * of its keywords appear in the query. Returns CATALOG for a single strong match,
     * COMPOSE if two entries match equally well, or null to fall through to LLM.
     */
    private fun classifyByKeywords(query: String): ClassificationResult? {
        val lowerQuery = query.lowercase()

        val scores = recipeCatalog.entries.map { entry ->
            val score = entry.keywords.count { keyword -> lowerQuery.contains(keyword.lowercase()) }
            entry to score
        }.filter { it.second > 0 }
            .sortedByDescending { it.second }

        if (scores.isEmpty()) return null

        val topScore = scores.first().second
        val topMatches = scores.filter { it.second == topScore }

        return when {
            topMatches.size == 1 -> {
                ClassificationResult(
                    strategy = ClassificationResult.Strategy.CATALOG,
                    recipeIds = listOf(topMatches.first().first.id)
                )
            }
            topMatches.size == 2 -> {
                ClassificationResult(
                    strategy = ClassificationResult.Strategy.COMPOSE,
                    recipeIds = topMatches.map { it.first.id }
                )
            }
            else -> null // Ambiguous — let the LLM decide
        }
    }
}
