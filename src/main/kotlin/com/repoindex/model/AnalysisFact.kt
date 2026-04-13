package com.repoindex.model

import java.time.Instant

/**
 * A pre-computed, structured fact extracted from a repository during indexing.
 * Facts are produced by running the standard recipe suite automatically on index,
 * and are used for fast cross-repo queries and report generation.
 */
data class AnalysisFact(
    val repositoryId: String,
    val recipeId: String,
    val filePath: String,
    val factType: FactType,
    val description: String,
    val analyzedAt: Instant = Instant.now()
) {
    enum class FactType {
        CLASS_DECLARATION,
        METHOD_DECLARATION,
        FIELD_DECLARATION,
        ANNOTATION_USAGE,
        IMPORT_STATEMENT,
        INTERFACE_DECLARATION,
        INHERITANCE_RELATION,
        METHOD_CALL,
        ENDPOINT_MAPPING
    }
}
