package com.repoindex.config

import com.repoindex.service.AnalysisService
import com.repoindex.service.RepositoryIndexService
import jakarta.annotation.PostConstruct
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(RepoIndexProperties::class)
class AppConfig(
    private val repositoryIndexService: RepositoryIndexService,
    private val analysisService: AnalysisService
) {
    @PostConstruct
    fun wireServices() {
        repositoryIndexService.setAnalysisService(analysisService)
    }
}
