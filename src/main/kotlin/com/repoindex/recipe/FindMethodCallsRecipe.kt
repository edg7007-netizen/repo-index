package com.repoindex.recipe

import org.openrewrite.ExecutionContext
import org.openrewrite.Recipe
import org.openrewrite.TreeVisitor
import org.openrewrite.java.JavaIsoVisitor
import org.openrewrite.java.tree.J
import org.openrewrite.marker.SearchResult

class FindMethodCallsRecipe : Recipe() {
    override fun getDisplayName() = "Find Method Calls"
    override fun getDescription() = "Finds all method invocations in the codebase."

    override fun getVisitor(): TreeVisitor<*, ExecutionContext> {
        return object : JavaIsoVisitor<ExecutionContext>() {
            override fun visitMethodInvocation(
                method: J.MethodInvocation,
                p: ExecutionContext
            ): J.MethodInvocation {
                val mi = super.visitMethodInvocation(method, p)!!
                val select = mi.select?.toString()?.let { "$it." } ?: ""
                val args = mi.arguments.joinToString(", ") { it.toString().take(30) }
                return SearchResult.found(mi, "Call: $select${mi.simpleName}($args)") as J.MethodInvocation
            }
        }
    }
}
