package com.repoindex.service

import com.repoindex.config.RepoIndexProperties
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class RepositoryIndexServiceTest {

    private val properties = RepoIndexProperties()
    private val service = RepositoryIndexService(properties)

    @Test
    fun `index repository with Java files`(@TempDir tempDir: Path) {
        val javaFile = tempDir.resolve("HelloWorld.java")
        Files.writeString(javaFile, """
            package com.example;
            
            public class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello, World!");
                }
            }
        """.trimIndent())

        val summary = service.indexRepository(tempDir.toString())

        assertEquals("Java", summary.languageBreakdown.keys.first())
        assertEquals(1, summary.languageBreakdown["Java"])
        assertEquals(1, summary.fileCount)
        assertNotNull(summary.id)
    }

    @Test
    fun `index repository with Kotlin files`(@TempDir tempDir: Path) {
        val ktFile = tempDir.resolve("Main.kt")
        Files.writeString(ktFile, """
            package com.example

            fun main() {
                println("Hello!")
            }
        """.trimIndent())

        val summary = service.indexRepository(tempDir.toString())

        assertTrue(summary.languageBreakdown.containsKey("Kotlin"))
        assertEquals(1, summary.languageBreakdown["Kotlin"])
    }

    @Test
    fun `list and remove repositories`(@TempDir tempDir: Path) {
        val javaFile = tempDir.resolve("App.java")
        Files.writeString(javaFile, "public class App {}")

        val summary = service.indexRepository(tempDir.toString())
        assertEquals(1, service.listRepositories().size)

        assertTrue(service.removeRepository(summary.id))
        assertEquals(0, service.listRepositories().size)
    }

    @Test
    fun `reject invalid path`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.indexRepository("/nonexistent/path/that/does/not/exist")
        }
    }

    @Test
    fun `skip files in build directories`(@TempDir tempDir: Path) {
        val srcFile = tempDir.resolve("Main.java")
        Files.writeString(srcFile, "public class Main {}")

        val buildDir = tempDir.resolve("build/generated")
        Files.createDirectories(buildDir)
        Files.writeString(buildDir.resolve("Gen.java"), "public class Gen {}")

        val summary = service.indexRepository(tempDir.toString())

        assertEquals(1, summary.fileCount)
    }
}
