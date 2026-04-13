package com.repoindex.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import javax.script.ScriptEngineManager
import javax.script.ScriptException

@Service
class RecipeCompilerService {

    private val log = LoggerFactory.getLogger(javaClass)

    data class CompilationResult(
        val success: Boolean,
        val compiledRecipeClass: Any? = null,
        val error: String? = null
    )

    fun compile(kotlinCode: String): CompilationResult {
        log.info("Compiling generated recipe code ({} chars)", kotlinCode.length)
        log.debug("Generated code:\n{}", kotlinCode)

        // Validate the generated code before attempting compilation
        val validationError = validateGeneratedCode(kotlinCode)
        if (validationError != null) {
            log.warn("Generated code validation failed: {}", validationError)
            return CompilationResult(success = false, error = validationError)
        }

        return try {
            val engine = ScriptEngineManager().getEngineByExtension("kts")
                ?: return CompilationResult(
                    success = false,
                    error = "Kotlin scripting engine not available"
                )

            // Wrap the code in a script that returns the recipe instance
            val script = buildScript(kotlinCode)

            val result = engine.eval(script)
            if (result is org.openrewrite.Recipe) {
                CompilationResult(success = true, compiledRecipeClass = result)
            } else {
                CompilationResult(
                    success = false,
                    error = "Script did not return a Recipe instance, got: ${result?.javaClass?.name ?: "null"}"
                )
            }
        } catch (e: ScriptException) {
            log.warn("Recipe compilation failed: {}", e.message)
            CompilationResult(success = false, error = e.message ?: "Unknown compilation error")
        } catch (e: Exception) {
            log.error("Unexpected error during compilation", e)
            CompilationResult(success = false, error = e.message ?: "Unexpected error")
        }
    }

    private fun buildScript(kotlinCode: String): String {
        // The generated code should define a Recipe class.
        // We wrap it in a script that instantiates it.
        val cleanedCode = sanitizeGeneratedCode(kotlinCode)
        val className = extractClassName(cleanedCode)
            ?: error("Could not find class name — this should not happen after validation")

        return """
            |import org.openrewrite.*
            |import org.openrewrite.java.*
            |import org.openrewrite.java.tree.*
            |import org.openrewrite.kotlin.tree.*
            |import org.openrewrite.marker.SearchResult
            |
            |$cleanedCode
            |
            |$className()
        """.trimMargin()
    }

    /**
     * Removes package declarations (invalid in .kts scripts) and duplicate imports
     * that are already provided by the script wrapper.
     */
    private fun sanitizeGeneratedCode(code: String): String {
        val providedImports = setOf(
            "import org.openrewrite.*",
            "import org.openrewrite.java.*",
            "import org.openrewrite.java.tree.*",
            "import org.openrewrite.kotlin.tree.*",
            "import org.openrewrite.marker.SearchResult"
        )

        return code.lines()
            .filter { line ->
                val trimmed = line.trim()
                // Remove package declarations (invalid in .kts)
                !trimmed.startsWith("package ") &&
                    // Remove duplicate wildcard/exact imports already in the wrapper
                    trimmed !in providedImports
            }
            .joinToString("\n")
            .trim()
    }

    /**
     * Validates that generated code looks like a plausible Recipe class before attempting compilation.
     * Returns an error message if invalid, null if valid.
     */
    private fun validateGeneratedCode(code: String): String? {
        val trimmed = code.trim()
        if (trimmed.length < 50) {
            return "Generated code is too short (${trimmed.length} chars) to be a valid Recipe class"
        }
        if (!trimmed.contains("class ")) {
            return "Generated code does not contain a class definition"
        }
        if (!trimmed.contains("Recipe")) {
            return "Generated code does not reference Recipe — expected a Recipe subclass"
        }
        // Check for class name extractability
        if (extractClassName(trimmed) == null) {
            return "Could not find a class declaration (expected 'class Name : ...' or 'class Name(...)')"
        }
        return null
    }

    private fun extractClassName(code: String): String? {
        val classPattern = Regex("class\\s+(\\w+)\\s*[:(]")
        val match = classPattern.find(code)
        return match?.groupValues?.get(1)
    }
}
