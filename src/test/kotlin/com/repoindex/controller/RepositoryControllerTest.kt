package com.repoindex.controller

import com.repoindex.service.RepositoryIndexService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.nio.file.Files
import java.nio.file.Path

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = [
    "spring.ai.ollama.chat.enabled=false"
])
class RepositoryControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var repositoryIndexService: RepositoryIndexService

    @Test
    fun `POST repos indexes a repository`(@TempDir tempDir: Path) {
        val javaFile = tempDir.resolve("Hello.java")
        Files.writeString(javaFile, "public class Hello {}")

        mockMvc.perform(
            post("/api/repos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"path":"${tempDir.toAbsolutePath()}"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").exists())
            .andExpect(jsonPath("$.fileCount").value(1))
    }

    @Test
    fun `GET repos returns list`() {
        mockMvc.perform(get("/api/repos"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
    }

    @Test
    fun `DELETE nonexistent repo returns 404`() {
        mockMvc.perform(delete("/api/repos/nonexistent"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST repos with invalid path returns 400`() {
        mockMvc.perform(
            post("/api/repos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"path":"/invalid/path/that/doesnt/exist"}""")
        )
            .andExpect(status().isBadRequest)
    }
}
