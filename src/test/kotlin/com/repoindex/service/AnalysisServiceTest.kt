package com.repoindex.service

import com.repoindex.config.RepoIndexProperties
import com.repoindex.model.AnalysisFact.FactType
import com.repoindex.recipe.RecipeCatalog
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class AnalysisServiceTest {

    private val properties = RepoIndexProperties()
    private lateinit var repositoryIndexService: RepositoryIndexService
    private lateinit var recipeExecutionService: RecipeExecutionService
    private lateinit var recipeCatalog: RecipeCatalog
    private lateinit var analysisService: AnalysisService

    @BeforeEach
    fun setUp() {
        repositoryIndexService = RepositoryIndexService(properties)
        recipeExecutionService = RecipeExecutionService()
        recipeCatalog = RecipeCatalog()
        analysisService = AnalysisService(recipeExecutionService, recipeCatalog)
    }

    @Test
    fun `analyze repository produces facts`(@TempDir tempDir: Path) {
        val javaFile = tempDir.resolve("Hello.java")
        Files.writeString(javaFile, """
            package com.example;
            
            public class Hello {
                private String name;
                
                public void greet() {
                    System.out.println("Hello " + name);
                }
            }
        """.trimIndent())

        val repoSummary = repositoryIndexService.indexRepository(tempDir.toString())
        val repo = repositoryIndexService.getRepository(repoSummary.id)!!

        analysisService.analyzeRepository(repo)

        val facts = analysisService.getFactsForRepository(repo.id)
        assertTrue(facts.isNotEmpty(), "Expected at least some facts from analysis")

        // Should find the class
        val classFacts = facts.filter { it.factType == FactType.CLASS_DECLARATION }
        assertTrue(classFacts.isNotEmpty(), "Expected at least one class fact")
        assertTrue(classFacts.any { it.description.contains("Hello") })

        // Should find the method
        val methodFacts = facts.filter { it.factType == FactType.METHOD_DECLARATION }
        assertTrue(methodFacts.isNotEmpty(), "Expected at least one method fact")
    }

    @Test
    fun `search facts by terms`(@TempDir tempDir: Path) {
        val javaFile = tempDir.resolve("Service.java")
        Files.writeString(javaFile, """
            package com.example;
            
            public class PaymentService {
                public void processPayment() {}
                public void refundPayment() {}
            }
        """.trimIndent())

        val repoSummary = repositoryIndexService.indexRepository(tempDir.toString())
        val repo = repositoryIndexService.getRepository(repoSummary.id)!!

        analysisService.analyzeRepository(repo)

        val results = analysisService.searchFacts(
            repoIds = listOf(repo.id),
            searchTerms = listOf("Payment")
        )
        assertTrue(results.isNotEmpty(), "Expected facts matching 'Payment'")
    }

    @Test
    fun `search facts by type filter`(@TempDir tempDir: Path) {
        val javaFile = tempDir.resolve("App.java")
        Files.writeString(javaFile, """
            public class App {
                public static void main(String[] args) {}
            }
        """.trimIndent())

        val repoSummary = repositoryIndexService.indexRepository(tempDir.toString())
        val repo = repositoryIndexService.getRepository(repoSummary.id)!!

        analysisService.analyzeRepository(repo)

        val classFacts = analysisService.searchFacts(
            repoIds = listOf(repo.id),
            searchTerms = emptyList(),
            factTypes = setOf(FactType.CLASS_DECLARATION)
        )
        assertTrue(classFacts.all { it.factType == FactType.CLASS_DECLARATION })
    }

    @Test
    fun `remove analysis clears facts`(@TempDir tempDir: Path) {
        val javaFile = tempDir.resolve("Foo.java")
        Files.writeString(javaFile, "public class Foo {}")

        val repoSummary = repositoryIndexService.indexRepository(tempDir.toString())
        val repo = repositoryIndexService.getRepository(repoSummary.id)!!

        analysisService.analyzeRepository(repo)
        assertTrue(analysisService.getFactsForRepository(repo.id).isNotEmpty())

        analysisService.removeAnalysis(repo.id)
        assertTrue(analysisService.getFactsForRepository(repo.id).isEmpty())
    }

    @Test
    fun `cross-repo facts aggregation`(@TempDir tempDir: Path) {
        // Repo 1
        val dir1 = tempDir.resolve("repo1")
        Files.createDirectories(dir1)
        Files.writeString(dir1.resolve("A.java"), "public class A {}")
        val repo1Summary = repositoryIndexService.indexRepository(dir1.toString())
        val repo1 = repositoryIndexService.getRepository(repo1Summary.id)!!
        analysisService.analyzeRepository(repo1)

        // Repo 2
        val dir2 = tempDir.resolve("repo2")
        Files.createDirectories(dir2)
        Files.writeString(dir2.resolve("B.java"), "public class B {}")
        val repo2Summary = repositoryIndexService.indexRepository(dir2.toString())
        val repo2 = repositoryIndexService.getRepository(repo2Summary.id)!!
        analysisService.analyzeRepository(repo2)

        // Cross-repo query
        val allFacts = analysisService.getFactsForRepositories(listOf(repo1.id, repo2.id))
        assertTrue(allFacts.size >= 2, "Expected facts from both repos")

        val repoIds = allFacts.map { it.repositoryId }.toSet()
        assertEquals(2, repoIds.size, "Expected facts from 2 different repos")
    }

    @Test
    fun `capability summary across repos`(@TempDir tempDir: Path) {
        val dir1 = tempDir.resolve("svc1")
        Files.createDirectories(dir1)
        Files.writeString(dir1.resolve("Controller.java"), """
            public class Controller {
                public void handleRequest() {}
            }
        """.trimIndent())
        val repo1 = repositoryIndexService.getRepository(
            repositoryIndexService.indexRepository(dir1.toString()).id
        )!!
        analysisService.analyzeRepository(repo1)

        val summary = analysisService.getCapabilitySummary(listOf(repo1.id))

        assertTrue(summary.containsKey("totalFacts"))
        assertTrue(summary.containsKey("classesByRepo"))
        assertTrue(summary.containsKey("factBreakdown"))
        assertTrue((summary["totalFacts"] as Int) > 0)
    }
}
