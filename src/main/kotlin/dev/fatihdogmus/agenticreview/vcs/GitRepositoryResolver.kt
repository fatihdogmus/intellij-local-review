package dev.fatihdogmus.agenticreview.vcs

import com.intellij.openapi.project.Project
import git4idea.repo.GitRepositoryManager

class GitRepositoryResolver(private val project: Project) {
    fun resolveRepositoryRoot(): String {
        val repository = GitRepositoryManager.getInstance(project).repositories.firstOrNull()
        if (repository != null) {
            return repository.root.path
        }
        return project.basePath ?: error("Project base path missing")
    }
}
