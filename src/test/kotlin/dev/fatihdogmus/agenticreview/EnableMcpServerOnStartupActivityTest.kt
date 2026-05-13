package dev.fatihdogmus.agenticreview

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class EnableMcpServerOnStartupActivityTest {
    private val project by projectFixture()

    @Test
    fun constantIsDefined() {
        assertThat(EnableMcpServerOnStartupActivity.ENABLE_MCP_SERVER_BY_DEFAULT_PROPERTY)
            .isEqualTo("local.review.enable.mcp.by.default")
    }

    @Test
    fun executeReturnsEarlyWhenPropertyNotSet() = runBlocking {
        val previous = System.getProperty(EnableMcpServerOnStartupActivity.ENABLE_MCP_SERVER_BY_DEFAULT_PROPERTY)
        try {
            System.clearProperty(EnableMcpServerOnStartupActivity.ENABLE_MCP_SERVER_BY_DEFAULT_PROPERTY)
            EnableMcpServerOnStartupActivity().execute(project)
        } finally {
            if (previous != null) {
                System.setProperty(EnableMcpServerOnStartupActivity.ENABLE_MCP_SERVER_BY_DEFAULT_PROPERTY, previous)
            } else {
                System.clearProperty(EnableMcpServerOnStartupActivity.ENABLE_MCP_SERVER_BY_DEFAULT_PROPERTY)
            }
        }
    }

    @Test
    fun executeReturnsEarlyWhenPropertyIsFalse() = runBlocking {
        val previous = System.getProperty(EnableMcpServerOnStartupActivity.ENABLE_MCP_SERVER_BY_DEFAULT_PROPERTY)
        try {
            System.setProperty(EnableMcpServerOnStartupActivity.ENABLE_MCP_SERVER_BY_DEFAULT_PROPERTY, "false")
            EnableMcpServerOnStartupActivity().execute(project)
        } finally {
            if (previous != null) {
                System.setProperty(EnableMcpServerOnStartupActivity.ENABLE_MCP_SERVER_BY_DEFAULT_PROPERTY, previous)
            } else {
                System.clearProperty(EnableMcpServerOnStartupActivity.ENABLE_MCP_SERVER_BY_DEFAULT_PROPERTY)
            }
        }
    }

    @Test
    fun activityIsProjectActivity() {
        assertThat(EnableMcpServerOnStartupActivity()).isInstanceOf(com.intellij.openapi.startup.ProjectActivity::class.java)
    }

    @Test
    fun executeStartsServiceWhenPropertyIsTrue() = runBlocking {
        val previous = System.getProperty(EnableMcpServerOnStartupActivity.ENABLE_MCP_SERVER_BY_DEFAULT_PROPERTY)
        try {
            System.setProperty(EnableMcpServerOnStartupActivity.ENABLE_MCP_SERVER_BY_DEFAULT_PROPERTY, "true")
            EnableMcpServerOnStartupActivity().execute(project)
        } finally {
            if (previous != null) {
                System.setProperty(EnableMcpServerOnStartupActivity.ENABLE_MCP_SERVER_BY_DEFAULT_PROPERTY, previous)
            } else {
                System.clearProperty(EnableMcpServerOnStartupActivity.ENABLE_MCP_SERVER_BY_DEFAULT_PROPERTY)
            }
        }
    }
}
