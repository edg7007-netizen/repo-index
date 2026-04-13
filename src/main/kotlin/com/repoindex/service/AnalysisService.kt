package com.repoindex.service

import com.repoindex.model.AnalysisFact
import com.repoindex.model.AnalysisFact.FactType
import com.repoindex.model.IndexedRepository
import com.repoindex.recipe.RecipeCatalog
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Pre-computes structured analysis facts when a repository is indexed,
 * and provides fast cross-repo querying over the stored facts.
 */
@Service
class AnalysisService(
    private val recipeExecutionService: RecipeExecutionService,
    private val recipeCatalog: RecipeCatalog
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** repoId → list of facts */
    private val factStore = ConcurrentHashMap<String, MutableList<AnalysisFact>>()

    /** Recipe IDs that form the standard analysis suite run on every index. */
    private val standardSuiteRecipeIds = listOf(
        "list-classes",
        "list-methods",
        "find-annotations",
        "list-imports",
        "find-interfaces",
        "find-inheritance",
        "find-fields",
        "find-method-calls",
        "find-endpoints"
    )

    /** Map recipe IDs to the fact type their findings represent. */
    private val recipeToFactType = mapOf(
        "list-classes" to FactType.CLASS_DECLARATION,
        "list-methods" to FactType.METHOD_DECLARATION,
        "find-annotations" to FactType.ANNOTATION_USAGE,
        "list-imports" to FactType.IMPORT_STATEMENT,
        "find-interfaces" to FactType.INTERFACE_DECLARATION,
        "find-inheritance" to FactType.INHERITANCE_RELATION,
        "find-fields" to FactType.FIELD_DECLARATION,
        "find-method-calls" to FactType.METHOD_CALL,
        "find-endpoints" to FactType.ENDPOINT_MAPPING
    )

    /**
     * Run the full standard recipe suite against a repository and store all findings as facts.
     * Called automatically after a repo is indexed.
     */
    fun analyzeRepository(repo: IndexedRepository) {
        log.info("Running standard analysis suite on repository '{}'", repo.name)
        val facts = mutableListOf<AnalysisFact>()

        for (recipeId in standardSuiteRecipeIds) {
            val entry = recipeCatalog.findById(recipeId) ?: continue
            val factType = recipeToFactType[recipeId] ?: continue

            try {
                val results = recipeExecutionService.executeRecipe(entry.recipe, repo)
                for (result in results) {
                    for (match in result.matches) {
                        facts.add(
                            AnalysisFact(
                                repositoryId = repo.id,
                                recipeId = recipeId,
                                filePath = result.filePath,
                                factType = factType,
                                description = match
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                log.warn("Recipe '{}' failed during analysis of '{}': {}", recipeId, repo.name, e.message)
            }
        }

        factStore[repo.id] = facts
        log.info("Stored {} analysis facts for repository '{}'", facts.size, repo.name)
    }

    /**
     * Remove all stored facts for a repository.
     */
    fun removeAnalysis(repoId: String) {
        factStore.remove(repoId)
    }

    /**
     * Get all facts for a single repository.
     */
    fun getFactsForRepository(repoId: String): List<AnalysisFact> {
        return factStore[repoId]?.toList() ?: emptyList()
    }

    /**
     * Get facts across multiple repositories (the cross-repo query core).
     */
    fun getFactsForRepositories(repoIds: Collection<String>): List<AnalysisFact> {
        return repoIds.flatMap { factStore[it]?.toList() ?: emptyList() }
    }

    /**
     * Search facts by keyword across multiple repositories.
     */
    fun searchFacts(
        repoIds: Collection<String>,
        searchTerms: List<String>,
        factTypes: Set<FactType>? = null
    ): List<AnalysisFact> {
        val allFacts = getFactsForRepositories(repoIds)
        val lowerTerms = searchTerms.map { it.lowercase() }

        return allFacts.filter { fact ->
            val matchesType = factTypes == null || fact.factType in factTypes
            val matchesTerm = lowerTerms.isEmpty() || lowerTerms.any { term ->
                fact.description.lowercase().contains(term) ||
                    fact.filePath.lowercase().contains(term)
            }
            matchesType && matchesTerm
        }
    }

    /**
     * Get a high-level capability summary across multiple repos.
     */
    fun getCapabilitySummary(repoIds: Collection<String>): Map<String, Any> {
        val facts = getFactsForRepositories(repoIds)

        val classesByRepo = facts.filter { it.factType == FactType.CLASS_DECLARATION }
            .groupBy { it.repositoryId }
            .mapValues { it.value.size }

        val endpointsByRepo = facts.filter { it.factType == FactType.ENDPOINT_MAPPING }
            .groupBy { it.repositoryId }
            .mapValues { it.value.map { f -> f.description } }

        val annotationsUsed = facts.filter { it.factType == FactType.ANNOTATION_USAGE }
            .map { it.description }
            .distinct()

        val inheritanceRelations = facts.filter { it.factType == FactType.INHERITANCE_RELATION }
            .map { it.description }

        return mapOf(
            "totalFacts" to facts.size,
            "classesByRepo" to classesByRepo,
            "endpointsByRepo" to endpointsByRepo,
            "uniqueAnnotations" to annotationsUsed.size,
            "inheritanceRelations" to inheritanceRelations.size,
            "factBreakdown" to facts.groupBy { it.factType.name }.mapValues { it.value.size }
        )
    }
}
