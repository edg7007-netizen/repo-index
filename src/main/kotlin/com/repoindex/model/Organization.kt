package com.repoindex.model

import java.time.Instant
import java.util.UUID

data class Organization(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val createdAt: Instant = Instant.now(),
    val repositoryIds: MutableSet<String> = mutableSetOf()
) {
    fun toSummary(repos: List<RepositorySummary>) = OrganizationSummary(
        id = id,
        name = name,
        description = description,
        createdAt = createdAt,
        repositoryCount = repositoryIds.size,
        repositories = repos
    )
}

data class OrganizationSummary(
    val id: String,
    val name: String,
    val description: String,
    val createdAt: Instant,
    val repositoryCount: Int,
    val repositories: List<RepositorySummary> = emptyList()
)

data class CreateOrganizationRequest(
    val name: String,
    val description: String = ""
)

data class AddRepoToOrgRequest(
    val repositoryId: String
)
