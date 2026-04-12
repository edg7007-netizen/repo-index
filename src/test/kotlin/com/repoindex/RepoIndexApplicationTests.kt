package com.repoindex

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@TestPropertySource(properties = [
    "spring.ai.ollama.chat.enabled=false"
])
class RepoIndexApplicationTests {

    @Test
    fun contextLoads() {
    }
}
