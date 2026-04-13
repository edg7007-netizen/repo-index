package com.repoindex.recipe

import org.openrewrite.ExecutionContext
import org.openrewrite.Recipe
import org.openrewrite.TreeVisitor
import org.openrewrite.java.JavaIsoVisitor
import org.openrewrite.java.tree.J
import org.openrewrite.marker.SearchResult

class FindFieldsRecipe : Recipe() {
    override fun getDisplayName() = "Find Fields"
    override fun getDescription() = "Finds all field declarations in the codebase."

    override fun getVisitor(): TreeVisitor<*, ExecutionContext> {
        return object : JavaIsoVisitor<ExecutionContext>() {
            override fun visitVariableDeclarations(
                multiVariable: J.VariableDeclarations,
                p: ExecutionContext
            ): J.VariableDeclarations {
                val vd = super.visitVariableDeclarations(multiVariable, p)!!
                // Only report fields (direct children of a class body)
                val parent = cursor.parentTreeCursor.getValue<Any>()
                if (parent is J.Block) {
                    val grandparent = cursor.parentTreeCursor.parentTreeCursor.getValue<Any>()
                    if (grandparent is J.ClassDeclaration) {
                        val type = vd.typeExpression?.toString() ?: "?"
                        val names = vd.variables.joinToString(", ") { it.simpleName }
                        return SearchResult.found(vd, "Field: $type $names") as J.VariableDeclarations
                    }
                }
                return vd
            }
        }
    }
}
