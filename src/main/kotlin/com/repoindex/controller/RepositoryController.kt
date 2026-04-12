package com.repoindex.controller

import com.repoindex.model.IndexRequest
import com.repoindex.model.RepositorySummary
import com.repoindex.service.RepositoryIndexService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/repos")
class RepositoryController(
    private val repositoryIndexService: RepositoryIndexService
) {

    @PostMapping
    fun indexRepository(@RequestBody request: IndexRequest): ResponseEntity<RepositorySummary> {
        return try {
            val summary = repositoryIndexService.indexRepository(request.path)
            ResponseEntity.status(HttpStatus.CREATED).body(summary)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    @GetMapping
    fun listRepositories(): List<RepositorySummary> {
        return repositoryIndexService.listRepositories()
    }

    @GetMapping("/{id}")
    fun getRepository(@PathVariable id: String): ResponseEntity<RepositorySummary> {
        val repo = repositoryIndexService.getRepository(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(repo.toSummary())
    }

    @DeleteMapping("/{id}")
    fun removeRepository(@PathVariable id: String): ResponseEntity<Void> {
        return if (repositoryIndexService.removeRepository(id)) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }
}
