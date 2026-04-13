package com.repoindex.recipe

import org.openrewrite.Recipe
import org.springframework.stereotype.Component

/**
 * Registry of pre-built, compile-time-verified Recipe instances.
 * Each entry has metadata used by the QueryClassifierService to match user queries.
 */
@Component
class RecipeCatalog {

    data class CatalogEntry(
        val id: String,
        val displayName: String,
        val description: String,
        val keywords: List<String>,
        val recipe: Recipe
    )

    val entries: List<CatalogEntry> = listOf(
        CatalogEntry(
            id = "list-classes",
            displayName = "List Classes",
            description = "Lists all class, interface, enum, and record declarations. " +
                "Use for: counting classes, listing types, finding class names, how many classes.",
            keywords = listOf("class", "classes", "types", "count classes", "list classes", "how many classes",
                "class declarations", "what classes", "show classes", "enumerate classes"),
            recipe = ListClassesRecipe()
        ),
        CatalogEntry(
            id = "list-methods",
            displayName = "List Methods",
            description = "Lists all method declarations with parameter types and return types. " +
                "Use for: counting methods, listing functions, finding method signatures.",
            keywords = listOf("method", "methods", "functions", "count methods", "list methods",
                "how many methods", "method declarations", "what methods", "function signatures"),
            recipe = ListMethodsRecipe()
        ),
        CatalogEntry(
            id = "find-annotations",
            displayName = "Find Annotations",
            description = "Finds all annotations used across the codebase. " +
                "Use for: listing annotations, finding @Service, @Controller, @Bean, etc.",
            keywords = listOf("annotation", "annotations", "decorator", "@", "find annotations",
                "list annotations", "what annotations", "spring annotations"),
            recipe = FindAnnotationsRecipe()
        ),
        CatalogEntry(
            id = "list-imports",
            displayName = "List Imports",
            description = "Lists all import statements in the codebase. " +
                "Use for: finding dependencies, what libraries are imported, dependency analysis.",
            keywords = listOf("import", "imports", "dependencies", "libraries", "list imports",
                "what imports", "what dependencies", "used libraries"),
            recipe = ListImportsRecipe()
        ),
        CatalogEntry(
            id = "find-interfaces",
            displayName = "Find Interfaces",
            description = "Finds all interface declarations. " +
                "Use for: listing interfaces, finding contracts, API boundaries.",
            keywords = listOf("interface", "interfaces", "contracts", "find interfaces",
                "list interfaces", "what interfaces", "how many interfaces"),
            recipe = FindInterfacesRecipe()
        ),
        CatalogEntry(
            id = "find-inheritance",
            displayName = "Find Inheritance",
            description = "Finds class inheritance hierarchies — what extends or implements what. " +
                "Use for: inheritance analysis, finding subclasses, class hierarchy, extends, implements.",
            keywords = listOf("extends", "implements", "inheritance", "hierarchy", "subclass",
                "superclass", "parent class", "child class", "class hierarchy", "find inheritance"),
            recipe = FindInheritanceRecipe()
        ),
        CatalogEntry(
            id = "find-fields",
            displayName = "Find Fields",
            description = "Finds all field declarations (member variables) in classes. " +
                "Use for: listing fields, finding properties, member variables.",
            keywords = listOf("field", "fields", "properties", "member variables", "variables",
                "find fields", "list fields", "what fields", "attributes"),
            recipe = FindFieldsRecipe()
        ),
        CatalogEntry(
            id = "find-method-calls",
            displayName = "Find Method Calls",
            description = "Finds all method invocations/calls in the codebase. " +
                "Use for: finding where methods are called, call analysis, usage tracking.",
            keywords = listOf("calls", "invocations", "method calls", "find calls",
                "where is called", "who calls", "usage", "call sites"),
            recipe = FindMethodCallsRecipe()
        ),
        CatalogEntry(
            id = "find-endpoints",
            displayName = "Find Endpoints",
            description = "Finds Spring MVC REST endpoint mappings (@GetMapping, @PostMapping, " +
                "@PutMapping, @DeleteMapping, @PatchMapping, @RequestMapping). " +
                "Use for: listing endpoints, finding API routes, REST controllers, HTTP mappings.",
            keywords = listOf("endpoint", "endpoints", "API", "apis", "routes", "REST",
                "controller", "mapping", "GetMapping", "PostMapping", "RequestMapping",
                "URL", "URLs", "paths", "HTTP", "web services", "what endpoints",
                "list endpoints", "find endpoints", "API endpoints"),
            recipe = FindEndpointsRecipe()
        )
    )

    /**
     * Returns a formatted description of all catalog entries for inclusion in LLM prompts.
     */
    fun toCatalogDescription(): String {
        return entries.joinToString("\n") { entry ->
            "- ID: ${entry.id} | Name: ${entry.displayName} | Description: ${entry.description}"
        }
    }

    fun findById(id: String): CatalogEntry? = entries.find { it.id == id }
}
