package com.repoindex.recipe

import org.openrewrite.ExecutionContext
import org.openrewrite.Recipe
import org.openrewrite.TreeVisitor
import org.openrewrite.java.JavaIsoVisitor
import org.openrewrite.java.tree.J
import org.openrewrite.marker.SearchResult

class FindInterfacesRecipe : Recipe() {
    override fun getDisplayName() = "Find Interfaces"
    override fun getDescription() = "Finds all interface declarations in the codebase."

    override fun getVisitor(): TreeVisitor<*, ExecutionContext> {
        return object : JavaIsoVisitor<ExecutionContext>() {
            override fun visitClassDeclaration(
                classDecl: J.ClassDeclaration,
                p: ExecutionContext
            ): J.ClassDeclaration {
                val cd = super.visitClassDeclaration(classDecl, p)!!
                if (cd.kind == J.ClassDeclaration.Kind.Type.Interface) {
                    return SearchResult.found(cd, "Interface: ${cd.simpleName}") as J.ClassDeclaration
                }
                return cd
            }
        }
    }
}
