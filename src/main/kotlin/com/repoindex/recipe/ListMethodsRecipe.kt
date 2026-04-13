package com.repoindex.recipe

import org.openrewrite.ExecutionContext
import org.openrewrite.Recipe
import org.openrewrite.TreeVisitor
import org.openrewrite.java.JavaIsoVisitor
import org.openrewrite.java.tree.J
import org.openrewrite.marker.SearchResult

class ListMethodsRecipe : Recipe() {
    override fun getDisplayName() = "List Methods"
    override fun getDescription() = "Lists all method declarations in the codebase."

    override fun getVisitor(): TreeVisitor<*, ExecutionContext> {
        return object : JavaIsoVisitor<ExecutionContext>() {
            override fun visitMethodDeclaration(
                method: J.MethodDeclaration,
                p: ExecutionContext
            ): J.MethodDeclaration {
                val md = super.visitMethodDeclaration(method, p)!!
                val params = md.parameters
                    .filterIsInstance<J.VariableDeclarations>()
                    .joinToString(", ") { vd ->
                        vd.typeExpression?.toString() ?: "?"
                    }
                val returnType = md.returnTypeExpression?.toString() ?: "void"
                return SearchResult.found(md, "Method: ${md.simpleName}($params): $returnType") as J.MethodDeclaration
            }
        }
    }
}
