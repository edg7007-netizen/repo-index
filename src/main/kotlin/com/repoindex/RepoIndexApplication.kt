package com.repoindex

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class RepoIndexApplication

fun main(args: Array<String>) {
    runApplication<RepoIndexApplication>(*args)
}
