package com.repoindex.controller

import com.repoindex.model.ChatMessage
import com.repoindex.model.IndexRequest
import com.repoindex.service.QueryOrchestrator
import com.repoindex.service.RepositoryIndexService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import java.util.concurrent.ConcurrentHashMap

@Controller
class ChatController(
    private val repositoryIndexService: RepositoryIndexService,
    private val queryOrchestrator: QueryOrchestrator
) {
    private val chatHistories = ConcurrentHashMap<String, MutableList<ChatMessage>>()

    @GetMapping("/")
    fun index(model: Model): String {
        model.addAttribute("repos", repositoryIndexService.listRepositories())
        model.addAttribute("messages", emptyList<ChatMessage>())
        return "index"
    }

    @PostMapping("/chat/index-repo")
    fun indexRepo(@RequestParam path: String, model: Model): String {
        return try {
            repositoryIndexService.indexRepository(path)
            model.addAttribute("repos", repositoryIndexService.listRepositories())
            "fragments/repo-selector :: repo-list"
        } catch (e: Exception) {
            model.addAttribute("error", "Failed to index: ${e.message}")
            "fragments/error :: error-message"
        }
    }

    @PostMapping("/chat/send")
    fun sendMessage(
        @RequestParam repositoryId: String,
        @RequestParam query: String,
        model: Model
    ): String {
        val sessionId = repositoryId // simplified: one chat per repo
        val history = chatHistories.getOrPut(sessionId) { mutableListOf() }

        // Add user message
        val userMessage = ChatMessage(role = ChatMessage.Role.USER, content = query)
        history.add(userMessage)

        // Process query
        val response = queryOrchestrator.processQuery(repositoryId, query)

        // Add assistant message
        val assistantMessage = ChatMessage(role = ChatMessage.Role.ASSISTANT, content = response.answer)
        history.add(assistantMessage)

        model.addAttribute("userMessage", userMessage)
        model.addAttribute("assistantMessage", assistantMessage)
        model.addAttribute("generatedRecipe", response.generatedRecipe)

        return "fragments/chat-messages :: message-pair"
    }
}
