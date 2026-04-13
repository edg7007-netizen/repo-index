package com.repoindex.recipe

import org.openrewrite.ExecutionContext
import org.openrewrite.Recipe
import org.openrewrite.TreeVisitor
import org.openrewrite.java.JavaIsoVisitor
import org.openrewrite.java.tree.J
import org.openrewrite.marker.SearchResult

class ListClassesRecipe : Recipe() {
    override fun getDisplayName() = "List Classes"
    override fun getDescription() = "Lists all class declarations in the codebase."

    override fun getVisitor(): TreeVisitor<*, ExecutionContext> {
        return object : JavaIsoVisitor<ExecutionContext>() {
            override fun visitClassDeclaration(
                classDecl: J.ClassDeclaration,
                p: ExecutionContext
            ): J.ClassDeclaration {
                val cd = super.visitClassDeclaration(classDecl, p)!!
                val kind = classDecl.kind.name.lowercase()
                return SearchResult.found(cd, "Class: ${cd.simpleName} ($kind)") as J.ClassDeclaration
            }
        }
    }
}
