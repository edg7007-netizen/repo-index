package com.repoindex.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "repoindex")
data class RepoIndexProperties(
    val maxRetries: Int = 3,
    val index: IndexProperties = IndexProperties()
) {
    data class IndexProperties(
        val maxFileSizeKb: Int = 500,
        val supportedExtensions: List<String> = listOf(".java", ".kt", ".kts", ".scala")
    )
}
