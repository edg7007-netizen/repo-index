package com.repoindex.service

import com.repoindex.config.RepoIndexProperties
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class OrganizationServiceTest {

    private val properties = RepoIndexProperties()
    private lateinit var repositoryIndexService: RepositoryIndexService
    private lateinit var organizationService: OrganizationService

    @BeforeEach
    fun setUp() {
        repositoryIndexService = RepositoryIndexService(properties)
        organizationService = OrganizationService(repositoryIndexService)
    }

    @Test
    fun `create and list organizations`() {
        val summary = organizationService.createOrganization("TestOrg", "A test org")

        assertEquals("TestOrg", summary.name)
        assertEquals("A test org", summary.description)
        assertEquals(0, summary.repositoryCount)
        assertNotNull(summary.id)

        val orgs = organizationService.listOrganizations()
        assertEquals(1, orgs.size)
        assertEquals("TestOrg", orgs.first().name)
    }

    @Test
    fun `add and remove repository from organization`(@TempDir tempDir: Path) {
        val javaFile = tempDir.resolve("Hello.java")
        Files.writeString(javaFile, "public class Hello {}")
        val repoSummary = repositoryIndexService.indexRepository(tempDir.toString())

        val orgSummary = organizationService.createOrganization("MyOrg")
        val updated = organizationService.addRepository(orgSummary.id, repoSummary.id)

        assertNotNull(updated)
        assertEquals(1, updated!!.repositoryCount)
        assertEquals(repoSummary.id, updated.repositories.first().id)

        // Remove
        val afterRemove = organizationService.removeRepository(orgSummary.id, repoSummary.id)
        assertNotNull(afterRemove)
        assertEquals(0, afterRemove!!.repositoryCount)
    }

    @Test
    fun `add repository to nonexistent org returns null`() {
        assertNull(organizationService.addRepository("nonexistent", "repo-id"))
    }

    @Test
    fun `add nonexistent repository returns null`() {
        val org = organizationService.createOrganization("TestOrg")
        assertNull(organizationService.addRepository(org.id, "nonexistent-repo"))
    }

    @Test
    fun `remove organization`() {
        val org = organizationService.createOrganization("ToDelete")
        assertTrue(organizationService.removeOrganization(org.id))
        assertFalse(organizationService.removeOrganization(org.id))
        assertTrue(organizationService.listOrganizations().isEmpty())
    }

    @Test
    fun `get organization repository ids`(@TempDir tempDir: Path) {
        val javaFile = tempDir.resolve("App.java")
        Files.writeString(javaFile, "public class App {}")
        val repoSummary = repositoryIndexService.indexRepository(tempDir.toString())

        val org = organizationService.createOrganization("OrgWithRepos")
        organizationService.addRepository(org.id, repoSummary.id)

        val repoIds = organizationService.getOrganizationRepositoryIds(org.id)
        assertEquals(1, repoIds.size)
        assertTrue(repoIds.contains(repoSummary.id))
    }

    @Test
    fun `get repository ids from nonexistent org returns empty`() {
        assertTrue(organizationService.getOrganizationRepositoryIds("nonexistent").isEmpty())
    }
}
