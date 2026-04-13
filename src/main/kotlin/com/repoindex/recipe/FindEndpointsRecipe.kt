package com.repoindex.recipe

import org.openrewrite.ExecutionContext
import org.openrewrite.Recipe
import org.openrewrite.TreeVisitor
import org.openrewrite.java.JavaIsoVisitor
import org.openrewrite.java.tree.J
import org.openrewrite.marker.SearchResult

class FindEndpointsRecipe : Recipe() {
    override fun getDisplayName() = "Find Endpoints"
    override fun getDescription() = "Finds Spring MVC endpoint mapping annotations and extracts HTTP method, path, and handler."

    override fun getVisitor(): TreeVisitor<*, ExecutionContext> {
        return object : JavaIsoVisitor<ExecutionContext>() {
            override fun visitAnnotation(
                annotation: J.Annotation,
                p: ExecutionContext
            ): J.Annotation {
                val a = super.visitAnnotation(annotation, p)!!
                val annotationName = a.annotationType.toString()

                val httpMethod = MAPPING_ANNOTATIONS[annotationName] ?: return a

                val path = extractPath(a)
                val handlerMethod = findEnclosingMethodName()
                val description = buildString {
                    append("Endpoint: $httpMethod")
                    if (path.isNotEmpty()) append(" $path")
                    if (handlerMethod != null) append(" → $handlerMethod()")
                }

                return SearchResult.found(a, description) as J.Annotation
            }

            private fun extractPath(annotation: J.Annotation): String {
                val args = annotation.arguments ?: return ""
                for (arg in args) {
                    when (arg) {
                        is J.Literal -> return arg.valueSource ?: arg.value?.toString() ?: ""
                        is J.Assignment -> {
                            val variableName = arg.variable.toString()
                            if (variableName == "value" || variableName == "path") {
                                return when (val value = arg.assignment) {
                                    is J.Literal -> value.valueSource ?: value.value?.toString() ?: ""
                                    is J.NewArray -> value.initializer
                                        ?.filterIsInstance<J.Literal>()
                                        ?.joinToString(", ") { it.valueSource ?: it.value?.toString() ?: "" }
                                        ?: ""
                                    else -> value.toString()
                                }
                            }
                        }
                    }
                }
                return ""
            }

            private fun findEnclosingMethodName(): String? {
                var c = cursor.parentTreeCursor
                while (true) {
                    val value = c.getValue<Any>()
                    if (value is J.MethodDeclaration) {
                        return value.simpleName
                    }
                    if (value is J.CompilationUnit) {
                        break
                    }
                    c = c.parentTreeCursor
                }
                return null
            }
        }
    }

    companion object {
        private val MAPPING_ANNOTATIONS = mapOf(
            "GetMapping" to "GET",
            "PostMapping" to "POST",
            "PutMapping" to "PUT",
            "DeleteMapping" to "DELETE",
            "PatchMapping" to "PATCH",
            "RequestMapping" to "REQUEST",
            // Fully-qualified variants
            "org.springframework.web.bind.annotation.GetMapping" to "GET",
            "org.springframework.web.bind.annotation.PostMapping" to "POST",
            "org.springframework.web.bind.annotation.PutMapping" to "PUT",
            "org.springframework.web.bind.annotation.DeleteMapping" to "DELETE",
            "org.springframework.web.bind.annotation.PatchMapping" to "PATCH",
            "org.springframework.web.bind.annotation.RequestMapping" to "REQUEST"
        )
    }
}
