package com.repoindex.service

import com.repoindex.model.IndexedRepository
import com.repoindex.model.RecipeResult
import org.openrewrite.InMemoryExecutionContext
import org.openrewrite.Recipe
import org.openrewrite.Tree
import org.openrewrite.TreeVisitor
import org.openrewrite.internal.InMemoryLargeSourceSet
import org.openrewrite.marker.SearchResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RecipeExecutionService {

    private val log = LoggerFactory.getLogger(javaClass)

    fun executeRecipe(recipe: Recipe, repo: IndexedRepository): List<RecipeResult> {
        log.info("Executing recipe '{}' against {} source files", recipe.name, repo.sourceFiles.size)

        val ctx = InMemoryExecutionContext { t ->
            log.warn("Execution error: {}", t.message)
        }

        val recipeResults = mutableListOf<RecipeResult>()

        try {
            val sourceSet = InMemoryLargeSourceSet(repo.sourceFiles)
            val recipeRun = recipe.run(sourceSet, ctx)
            val resultList = recipeRun.changeset.allResults

            for (result in resultList) {
                val filePath = result.before?.sourcePath?.toString()
                    ?: result.after?.sourcePath?.toString()
                    ?: "unknown"
                val matches = mutableListOf<String>()
                val changes = mutableListOf<String>()

                // Collect SearchResult markers from ALL nodes in the AST tree,
                // not just the top-level SourceFile markers. SearchResult.found()
                // adds markers to individual AST nodes (e.g., J.ClassDeclaration),
                // so we must walk the full tree to find them.
                val afterFile = result.after
                if (afterFile != null) {
                    val collector = object : TreeVisitor<Tree, InMemoryExecutionContext>() {
                        override fun preVisit(tree: Tree, ctx: InMemoryExecutionContext): Tree? {
                            tree.markers.findAll(SearchResult::class.java).forEach { marker ->
                                marker.description?.let { matches.add(it) }
                            }
                            return super.preVisit(tree, ctx)
                        }
                    }
                    collector.visit(afterFile, ctx)
                }

                // Capture diff text
                val diff = result.diff()
                if (diff.isNotEmpty()) {
                    changes.add(diff)
                }

                if (matches.isNotEmpty() || changes.isNotEmpty()) {
                    recipeResults.add(RecipeResult(filePath = filePath, matches = matches, changes = changes))
                }
            }

            // Also collect messages from the execution context
            val messages = ctx.getMessage<Any>("finding")
            if (messages != null) {
                recipeResults.add(RecipeResult(
                    filePath = "context",
                    matches = listOf(messages.toString())
                ))
            }
        } catch (e: Exception) {
            log.error("Recipe execution failed: {}", e.message)
            recipeResults.add(RecipeResult(
                filePath = "error",
                matches = listOf("Execution error: ${e.message}")
            ))
        }

        log.info("Recipe produced {} results", recipeResults.size)
        return recipeResults
    }
}
