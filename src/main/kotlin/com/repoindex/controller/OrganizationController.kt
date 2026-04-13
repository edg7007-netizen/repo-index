package com.repoindex.controller

import com.repoindex.model.AddRepoToOrgRequest
import com.repoindex.model.CreateOrganizationRequest
import com.repoindex.model.OrganizationSummary
import com.repoindex.service.AnalysisService
import com.repoindex.service.OrganizationService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/orgs")
class OrganizationController(
    private val organizationService: OrganizationService,
    private val analysisService: AnalysisService
) {

    @PostMapping
    fun createOrganization(@RequestBody request: CreateOrganizationRequest): ResponseEntity<OrganizationSummary> {
        val summary = organizationService.createOrganization(request.name, request.description)
        return ResponseEntity.status(HttpStatus.CREATED).body(summary)
    }

    @GetMapping
    fun listOrganizations(): List<OrganizationSummary> {
        return organizationService.listOrganizations()
    }

    @GetMapping("/{id}")
    fun getOrganization(@PathVariable id: String): ResponseEntity<OrganizationSummary> {
        val org = organizationService.getOrganization(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(organizationService.listOrganizations().find { it.id == id })
    }

    @DeleteMapping("/{id}")
    fun removeOrganization(@PathVariable id: String): ResponseEntity<Void> {
        return if (organizationService.removeOrganization(id)) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/{orgId}/repos")
    fun addRepository(
        @PathVariable orgId: String,
        @RequestBody request: AddRepoToOrgRequest
    ): ResponseEntity<OrganizationSummary> {
        val summary = organizationService.addRepository(orgId, request.repositoryId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(summary)
    }

    @DeleteMapping("/{orgId}/repos/{repoId}")
    fun removeRepository(
        @PathVariable orgId: String,
        @PathVariable repoId: String
    ): ResponseEntity<OrganizationSummary> {
        val summary = organizationService.removeRepository(orgId, repoId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(summary)
    }

    @GetMapping("/{orgId}/capabilities")
    fun getCapabilities(@PathVariable orgId: String): ResponseEntity<Map<String, Any>> {
        val repoIds = organizationService.getOrganizationRepositoryIds(orgId)
        if (repoIds.isEmpty()) {
            return ResponseEntity.ok(mapOf("message" to "No repositories in this organization"))
        }
        return ResponseEntity.ok(analysisService.getCapabilitySummary(repoIds))
    }
}
