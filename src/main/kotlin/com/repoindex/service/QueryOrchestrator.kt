package com.repoindex.service

import com.repoindex.config.RepoIndexProperties
import com.repoindex.llm.QueryClassifierService
import com.repoindex.llm.QueryClassifierService.ClassificationResult.Strategy
import com.repoindex.llm.RecipeGeneratorService
import com.repoindex.model.IndexedRepository
import com.repoindex.model.QueryResponse
import com.repoindex.model.RecipeResult
import com.repoindex.recipe.RecipeCatalog
import org.openrewrite.Recipe
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class QueryOrchestrator(
    private val repositoryIndexService: RepositoryIndexService,
    private val recipeGeneratorService: RecipeGeneratorService,
    private val recipeCompilerService: RecipeCompilerService,
    private val recipeExecutionService: RecipeExecutionService,
    private val queryClassifierService: QueryClassifierService,
    private val recipeCatalog: RecipeCatalog,
    private val properties: RepoIndexProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun processQuery(repositoryId: String, query: String): QueryResponse {
        val repo = repositoryIndexService.getRepository(repositoryId)
            ?: return QueryResponse(answer = "Repository not found: $repositoryId", error = "NOT_FOUND")

        if (repo.sourceFiles.isEmpty()) {
            return QueryResponse(
                answer = "Repository '${repo.name}' has no parsed source files. " +
                    "Ensure it contains supported source files (.java, .kt, .scala).",
                error = "EMPTY_REPO"
            )
        }

        // Tier 1 & 2: Classify query against the pre-built recipe catalog
        val classification = queryClassifierService.classify(query)
        log.info("Query classified as {} with recipes {}", classification.strategy, classification.recipeIds)

        return when (classification.strategy) {
            Strategy.CATALOG -> {
                // Tier 1: Direct catalog match — use a single pre-built recipe
                val entry = recipeCatalog.findById(classification.recipeIds.first())!!
                log.info("Using pre-built recipe: {} ({})", entry.displayName, entry.id)
                executeAndFormat(entry.recipe, query, repo, catalogRecipeId = entry.id)
            }
            Strategy.COMPOSE -> {
                // Tier 2: Compose multiple pre-built recipes
                val entries = classification.recipeIds.mapNotNull { recipeCatalog.findById(it) }
                log.info("Composing {} pre-built recipes: {}", entries.size, entries.map { it.id })
                executeComposedAndFormat(entries.map { it.recipe }, query, repo,
                    catalogRecipeIds = entries.map { it.id })
            }
            Strategy.ANALYZE -> {
                // Tier 2.5: Run broad recipes and filter findings by search terms
                log.info("ANALYZE strategy with search terms: {}", classification.searchTerms)
                analyzeAndFormat(query, repo, classification.searchTerms)
            }
            Strategy.GENERATE -> {
                // Tier 3: Fall back to LLM-generated recipe
                log.info("No catalog match — falling back to dynamic recipe generation")
                generateAndExecute(query, repo)
            }
        }
    }

    /**
     * Tier 2.5: Run broad analysis recipes, filter findings by search terms,
     * and let the LLM synthesize a coherent answer from the filtered results.
     */
    private fun analyzeAndFormat(
        query: String,
        repo: IndexedRepository,
        searchTerms: List<String>
    ): QueryResponse {
        // Run a broad set of recipes to gather comprehensive findings
        val analysisRecipeIds = listOf(
            "list-classes", "find-fields", "list-imports",
            "find-annotations", "find-inheritance", "list-methods"
        )
        val recipes = analysisRecipeIds.mapNotNull { recipeCatalog.findById(it)?.recipe }

        val allResults = mutableListOf<RecipeResult>()
        for (recipe in recipes) {
            try {
                allResults.addAll(recipeExecutionService.executeRecipe(recipe, repo))
            } catch (e: Exception) {
                log.warn("Analysis recipe failed: {}", e.message)
            }
        }

        // Filter findings to only those matching search terms (case-insensitive)
        val lowerTerms = searchTerms.map { it.lowercase() }
        val filteredResults = allResults.mapNotNull { result ->
            val matchingFindings = result.matches.filter { finding ->
                val lowerFinding = finding.lowercase()
                lowerTerms.any { term -> lowerFinding.contains(term.lowercase()) }
            }
            if (matchingFindings.isNotEmpty()) {
                result.copy(matches = matchingFindings)
            } else {
                null
            }
        }

        log.info(
            "ANALYZE: {} total findings filtered to {} relevant findings using {} terms",
            allResults.sumOf { it.matches.size },
            filteredResults.sumOf { it.matches.size },
            searchTerms.size
        )

        return buildResponse(
            query,
            filteredResults,
            generatedRecipe = "ANALYZE strategy with terms: ${searchTerms.joinToString(", ")}"
        )
    }

    /**
     * Tier 1: Execute a single pre-built recipe and format the answer.
     */
    private fun executeAndFormat(
        recipe: Recipe,
        query: String,
        repo: IndexedRepository,
        catalogRecipeId: String? = null
    ): QueryResponse {
        val executionResults: List<RecipeResult>
        try {
            executionResults = recipeExecutionService.executeRecipe(recipe, repo)
        } catch (e: Exception) {
            log.error("Pre-built recipe execution failed: {}", e.message)
            return QueryResponse(
                answer = "Recipe execution failed: ${e.message}",
                error = "EXECUTION_FAILED"
            )
        }

        return buildResponse(query, executionResults,
            generatedRecipe = catalogRecipeId?.let { "Pre-built recipe: $it" })
    }

    /**
     * Tier 2: Execute multiple pre-built recipes and merge their results.
     */
    private fun executeComposedAndFormat(
        recipes: List<Recipe>,
        query: String,
        repo: IndexedRepository,
        catalogRecipeIds: List<String>
    ): QueryResponse {
        val allResults = mutableListOf<RecipeResult>()
        for (recipe in recipes) {
            try {
                allResults.addAll(recipeExecutionService.executeRecipe(recipe, repo))
            } catch (e: Exception) {
                log.warn("One of the composed recipes failed: {}", e.message)
            }
        }

        return buildResponse(query, allResults,
            generatedRecipe = "Composed pre-built recipes: ${catalogRecipeIds.joinToString(", ")}")
    }

    /**
     * Tier 3: Generate, compile, and execute an LLM-generated recipe (original flow).
     */
    private fun generateAndExecute(query: String, repo: IndexedRepository): QueryResponse {
        // Step 1: Generate recipe code from LLM
        var recipeCode: String
        try {
            recipeCode = recipeGeneratorService.generateRecipeCode(query, repo)
        } catch (e: Exception) {
            log.error("Failed to generate recipe: {}", e.message)
            return QueryResponse(
                answer = "Failed to generate analysis recipe: ${e.message}",
                error = "GENERATION_FAILED"
            )
        }

        // Step 2: Compile with retry
        var compiledRecipe: Recipe? = null
        var lastError: String? = null
        val errorHistory = mutableListOf<String>()

        for (attempt in 1..properties.maxRetries) {
            val result = recipeCompilerService.compile(recipeCode)
            if (result.success && result.compiledRecipeClass is Recipe) {
                compiledRecipe = result.compiledRecipeClass
                break
            }

            lastError = result.error
            errorHistory.add(lastError ?: "Unknown error")
            log.warn("Compilation attempt {}/{} failed: {}", attempt, properties.maxRetries, lastError)

            if (attempt < properties.maxRetries) {
                try {
                    recipeCode = recipeGeneratorService.fixRecipeCode(
                        originalCode = recipeCode,
                        error = lastError ?: "Unknown error",
                        attempt = attempt,
                        query = query,
                        previousErrors = errorHistory.dropLast(1)
                    )
                } catch (e: Exception) {
                    log.error("Failed to get fix from LLM: {}", e.message)
                    break
                }
            }
        }

        if (compiledRecipe == null) {
            return QueryResponse(
                answer = "Could not compile the analysis recipe after ${properties.maxRetries} attempts. " +
                    "Last error: $lastError",
                generatedRecipe = recipeCode,
                error = "COMPILATION_FAILED"
            )
        }

        // Step 3: Execute recipe against LSTs
        val executionResults: List<RecipeResult>
        try {
            executionResults = recipeExecutionService.executeRecipe(compiledRecipe, repo)
        } catch (e: Exception) {
            log.error("Recipe execution failed: {}", e.message)
            return QueryResponse(
                answer = "Recipe execution failed: ${e.message}",
                generatedRecipe = recipeCode,
                error = "EXECUTION_FAILED"
            )
        }

        return buildResponse(query, executionResults, generatedRecipe = recipeCode)
    }

    /**
     * Common: format findings into a human-readable answer via LLM.
     */
    private fun buildResponse(
        query: String,
        executionResults: List<RecipeResult>,
        generatedRecipe: String? = null
    ): QueryResponse {
        // Use only match descriptions (from SearchResult markers) for the LLM summary.
        // Raw diffs are noisy and cause the LLM to misinterpret findings as PR changes.
        val findings = executionResults.flatMap { result ->
            result.matches.map { "${result.filePath}: $it" }
        }

        val answer = try {
            recipeGeneratorService.formatAnswer(query, findings)
        } catch (e: Exception) {
            log.warn("Failed to format answer via LLM, using raw findings: {}", e.message)
            if (findings.isEmpty()) {
                "No results found for: \"$query\""
            } else {
                "## Findings\n\n${findings.joinToString("\n") { "- $it" }}"
            }
        }

        return QueryResponse(
            answer = answer,
            generatedRecipe = generatedRecipe,
            executionResults = executionResults
        )
    }
}
