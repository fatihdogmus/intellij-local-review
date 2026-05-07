package dev.fatihdogmus.localreview

import com.intellij.mcpserver.impl.McpServerService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class EnableMcpServerOnStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (System.getProperty(ENABLE_MCP_SERVER_BY_DEFAULT_PROPERTY)?.toBoolean() != true) return

        val service = McpServerService.getInstance()
        if (!service.isRunning) {
            service.start()
        }
    }

    companion object {
        const val ENABLE_MCP_SERVER_BY_DEFAULT_PROPERTY: String = "local.review.enable.mcp.by.default"
    }
}
