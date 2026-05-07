package dev.agentreview.intellij.vcs

import com.intellij.openapi.project.Project

class GitRepositoryResolver(private val project: Project) {
    fun resolveRepositoryRoot(): String {
        val basePath = project.basePath ?: error("Project base path missing")
        return GitCommandFallback(basePath).run("rev-parse", "--show-toplevel").trim()
    }
}
