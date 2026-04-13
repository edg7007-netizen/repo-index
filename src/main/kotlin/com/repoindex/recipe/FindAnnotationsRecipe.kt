package com.repoindex.recipe

import org.openrewrite.ExecutionContext
import org.openrewrite.Recipe
import org.openrewrite.TreeVisitor
import org.openrewrite.java.JavaIsoVisitor
import org.openrewrite.java.tree.J
import org.openrewrite.marker.SearchResult

class FindAnnotationsRecipe : Recipe() {
    override fun getDisplayName() = "Find Annotations"
    override fun getDescription() = "Finds all annotations used in the codebase."

    override fun getVisitor(): TreeVisitor<*, ExecutionContext> {
        return object : JavaIsoVisitor<ExecutionContext>() {
            override fun visitAnnotation(
                annotation: J.Annotation,
                p: ExecutionContext
            ): J.Annotation {
                val a = super.visitAnnotation(annotation, p)!!
                val name = a.annotationType.toString()
                return SearchResult.found(a, "Annotation: @$name") as J.Annotation
            }
        }
    }
}
