package com.repoindex.recipe

import org.openrewrite.ExecutionContext
import org.openrewrite.Recipe
import org.openrewrite.TreeVisitor
import org.openrewrite.java.JavaIsoVisitor
import org.openrewrite.java.tree.J
import org.openrewrite.marker.SearchResult

class FindInheritanceRecipe : Recipe() {
    override fun getDisplayName() = "Find Inheritance"
    override fun getDescription() = "Finds all classes and the types they extend or implement."

    override fun getVisitor(): TreeVisitor<*, ExecutionContext> {
        return object : JavaIsoVisitor<ExecutionContext>() {
            override fun visitClassDeclaration(
                classDecl: J.ClassDeclaration,
                p: ExecutionContext
            ): J.ClassDeclaration {
                val cd = super.visitClassDeclaration(classDecl, p)!!
                val extendsType = cd.extends?.toString()
                val implementsList = cd.implements?.joinToString(", ") { it.toString() }
                if (extendsType != null || implementsList != null) {
                    val parts = mutableListOf<String>()
                    if (extendsType != null) parts.add("extends $extendsType")
                    if (implementsList != null) parts.add("implements $implementsList")
                    return SearchResult.found(cd, "Class ${cd.simpleName} ${parts.joinToString(", ")}") as J.ClassDeclaration
                }
                return cd
            }
        }
    }
}
