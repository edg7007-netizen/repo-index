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
        val className = extractClassName(kotlinCode)

        return """
            |import org.openrewrite.*
            |import org.openrewrite.java.*
            |import org.openrewrite.java.tree.*
            |import org.openrewrite.kotlin.tree.*
            |import org.openrewrite.marker.SearchResult
            |
            |$kotlinCode
            |
            |$className()
        """.trimMargin()
    }

    private fun extractClassName(code: String): String {
        val classPattern = Regex("class\\s+(\\w+)\\s*[:(]")
        val match = classPattern.find(code)
        return match?.groupValues?.get(1)
            ?: throw IllegalArgumentException("Could not find class name in generated code")
    }
}
