package com.repoindex.recipe

import org.openrewrite.ExecutionContext
import org.openrewrite.Recipe
import org.openrewrite.TreeVisitor
import org.openrewrite.java.JavaIsoVisitor
import org.openrewrite.java.tree.J
import org.openrewrite.marker.SearchResult

class ListImportsRecipe : Recipe() {
    override fun getDisplayName() = "List Imports"
    override fun getDescription() = "Lists all import statements in the codebase."

    override fun getVisitor(): TreeVisitor<*, ExecutionContext> {
        return object : JavaIsoVisitor<ExecutionContext>() {
            override fun visitImport(
                import_: J.Import,
                p: ExecutionContext
            ): J.Import {
                val i = super.visitImport(import_, p)!!
                val qualifiedName = i.qualid.toString()
                val prefix = if (i.isStatic) "static " else ""
                return SearchResult.found(i, "Import: ${prefix}$qualifiedName") as J.Import
            }
        }
    }
}
