package com.repoindex.controller

import com.repoindex.model.QueryRequest
import com.repoindex.model.QueryResponse
import com.repoindex.service.QueryOrchestrator
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/query")
class QueryController(
    private val queryOrchestrator: QueryOrchestrator
) {

    @PostMapping
    fun query(@RequestBody request: QueryRequest): ResponseEntity<QueryResponse> {
        val response = queryOrchestrator.processQuery(request.repositoryId, request.query)
        return if (response.error != null) {
            ResponseEntity.badRequest().body(response)
        } else {
            ResponseEntity.ok(response)
        }
    }
}
