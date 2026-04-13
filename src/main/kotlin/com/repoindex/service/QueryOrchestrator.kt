package com.repoindex.service

import com.repoindex.config.RepoIndexProperties
import com.repoindex.llm.RecipeGeneratorService
import com.repoindex.model.QueryResponse
import com.repoindex.model.RecipeResult
import org.openrewrite.Recipe
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class QueryOrchestrator(
    private val repositoryIndexService: RepositoryIndexService,
    private val recipeGeneratorService: RecipeGeneratorService,
    private val recipeCompilerService: RecipeCompilerService,
    private val recipeExecutionService: RecipeExecutionService,
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

        for (attempt in 1..properties.maxRetries) {
            val result = recipeCompilerService.compile(recipeCode)
            if (result.success && result.compiledRecipeClass is Recipe) {
                compiledRecipe = result.compiledRecipeClass
                break
            }

            lastError = result.error
            log.warn("Compilation attempt {}/{} failed: {}", attempt, properties.maxRetries, lastError)

            if (attempt < properties.maxRetries) {
                try {
                    recipeCode = recipeGeneratorService.fixRecipeCode(recipeCode, lastError ?: "Unknown error", attempt)
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

        // Step 4: Format answer using LLM
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
            generatedRecipe = recipeCode,
            executionResults = executionResults
        )
    }
}
