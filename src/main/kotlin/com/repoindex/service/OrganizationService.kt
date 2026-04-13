package com.repoindex.service

import com.repoindex.model.Organization
import com.repoindex.model.OrganizationSummary
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class OrganizationService(
    private val repositoryIndexService: RepositoryIndexService
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val organizations = ConcurrentHashMap<String, Organization>()

    fun createOrganization(name: String, description: String = ""): OrganizationSummary {
        val org = Organization(name = name, description = description)
        organizations[org.id] = org
        log.info("Created organization '{}' ({})", name, org.id)
        return toSummary(org)
    }

    fun getOrganization(id: String): Organization? = organizations[id]

    fun listOrganizations(): List<OrganizationSummary> = organizations.values.map { toSummary(it) }

    fun removeOrganization(id: String): Boolean {
        val removed = organizations.remove(id) != null
        if (removed) log.info("Removed organization {}", id)
        return removed
    }

    fun addRepository(orgId: String, repoId: String): OrganizationSummary? {
        val org = organizations[orgId] ?: return null
        val repo = repositoryIndexService.getRepository(repoId) ?: return null
        org.repositoryIds.add(repoId)
        log.info("Added repository '{}' to organization '{}'", repo.name, org.name)
        return toSummary(org)
    }

    fun removeRepository(orgId: String, repoId: String): OrganizationSummary? {
        val org = organizations[orgId] ?: return null
        org.repositoryIds.remove(repoId)
        log.info("Removed repository {} from organization '{}'", repoId, org.name)
        return toSummary(org)
    }

    fun getOrganizationRepositoryIds(orgId: String): Set<String> {
        return organizations[orgId]?.repositoryIds?.toSet() ?: emptySet()
    }

    private fun toSummary(org: Organization): OrganizationSummary {
        val repos = org.repositoryIds.mapNotNull { repositoryIndexService.getRepository(it)?.toSummary() }
        return org.toSummary(repos)
    }
}
