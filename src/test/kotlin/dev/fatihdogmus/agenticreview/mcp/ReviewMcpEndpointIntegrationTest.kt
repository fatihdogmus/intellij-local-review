package dev.fatihdogmus.agenticreview.mcp

import com.intellij.mcpserver.McpToolFilter
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import dev.fatihdogmus.agenticreview.ReviewManagerService
import dev.fatihdogmus.agenticreview.mcp.CommentContextResult
import dev.fatihdogmus.agenticreview.mcp.CommentListResult
import dev.fatihdogmus.agenticreview.mcp.MutationResult
import dev.fatihdogmus.agenticreview.mcp.ReviewDetails
import dev.fatihdogmus.agenticreview.mcp.ReviewListResult
import dev.fatihdogmus.agenticreview.mcp.ReviewResult
import dev.fatihdogmus.agenticreview.model.CommentAnchor
import dev.fatihdogmus.agenticreview.model.CommentStatus
import dev.fatihdogmus.agenticreview.model.DiffSide
import dev.fatihdogmus.agenticreview.model.Review
import dev.fatihdogmus.agenticreview.model.ReviewComment
import dev.fatihdogmus.agenticreview.model.ReviewStatus
import dev.fatihdogmus.agenticreview.model.ReviewTarget
import dev.fatihdogmus.agenticreview.model.ReviewTargetType
import dev.fatihdogmus.agenticreview.persistence.ReviewStateService
import dev.fatihdogmus.agenticreview.snapshot.TurnSnapshotListResult
import dev.fatihdogmus.agenticreview.snapshot.TurnSnapshotResult
import dev.fatihdogmus.agenticreview.snapshot.TurnSnapshotService
import dev.fatihdogmus.agenticreview.vcs.ChangedFile
import dev.fatihdogmus.agenticreview.vcs.ChangedFileStatus
import dev.fatihdogmus.agenticreview.vcs.ReviewContent
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.header
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class ReviewMcpEndpointIntegrationTest {
    private val project by projectFixture(openAfterCreation = true, openProjectTask = OpenProjectTask { createModule = false })
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setUp() {
        val manager = ReviewManagerService.getInstance(project)
        val stateService = ReviewStateService.getInstance(project)
        stateService.reviews().map { it.id }.forEach(stateService::removeReview)
        TurnSnapshotService.getInstance(project).clearAll(notify = false)
        manager.hasUncommittedChangesSupplier = { false }
        manager.uncommittedChangesLoader = { emptyList() }
        manager.repositoryRootResolver = { project.basePath!! }
        manager.currentHeadHashSupplier = { "head-1" }
        manager.openDefaultReview()
    }

    @Test
    fun reviewToolsAreCallableOverAuthorizedMcpEndpoint() {
        runBlocking {
            withConnection { client ->
                val tools = client.listTools(ListToolsRequest(), null)
                assertThat(tools.tools.map { it.name }).contains("review_list_reviews", "review_get_review", "review_turn_snapshot_begin")
            }
        }
    }

    @Test
    fun reviewListReviewsReturnsWorkspaceReviewsOverRealEndpoint() {
        val commitReview = seededCommitReview("endpoint-list")
        ReviewStateService.getInstance(project).addReview(commitReview)

        runBlocking {
            withConnection { client ->
                val result = client.callTool("review_list_reviews", emptyMap<String, Any>(), emptyMap<String, Any>(), null)
                assertSuccessful(result)
                val decoded = json.decodeFromString<ReviewListResult>(result.text())
                assertThat(decoded.reviews).hasSize(2)
                assertThat(decoded.reviews.map { it.type }).contains(ReviewTargetType.UNCOMMITTED, ReviewTargetType.COMMIT)

                val filtered = client.callTool("review_list_reviews", mapOf("status" to "OPEN"), emptyMap<String, Any>(), null)
                assertSuccessful(filtered)
                val filteredDecoded = json.decodeFromString<ReviewListResult>(filtered.text())
                assertThat(filteredDecoded.reviews).hasSize(2)
            }
        }
    }

    @Test
    fun reviewGetReviewAndUnresolvedCommentsWorkOverRealEndpoint() {
        val manager = ReviewManagerService.getInstance(project)
        val review = seededCommitReview("endpoint-review")
        ReviewStateService.getInstance(project).addReview(review)
        manager.selectReview(review.id)
        manager.addComment(review.id, sampleChangedFile("src/Foo.kt"), DiffSide.RIGHT, 2, "open comment")
        manager.addComment(review.id, sampleChangedFile("src/Foo.kt"), DiffSide.RIGHT, 3, "resolved comment")
        val resolvedId = manager.findReview(review.id)!!.comments.last().id
        manager.markCommentResolved(resolvedId)

        runBlocking {
            withConnection { client ->
                val reviewResult = client.callTool("review_get_review", mapOf("reviewId" to review.id, "includeResolved" to true), emptyMap<String, Any>(), null)
                assertSuccessful(reviewResult)
                val decodedReview = json.decodeFromString<ReviewResult>(reviewResult.text())
                assertThat(decodedReview.review.id).isEqualTo(review.id)
                assertThat(decodedReview.review.openCommentCount).isEqualTo(1)
                assertThat(decodedReview.review.resolvedCommentCount).isEqualTo(1)

                val unresolved = client.callTool("review_list_unresolved_comments", mapOf("reviewId" to review.id), emptyMap<String, Any>(), null)
                assertSuccessful(unresolved)
                val decodedComments = json.decodeFromString<CommentListResult>(unresolved.text())
                assertThat(decodedComments.comments).singleElement().extracting("body").isEqualTo("open comment")
            }
        }
    }

    @Test
    fun reviewGetCommentContextAndResolveCommentWorkOverRealEndpoint() {
        val manager = ReviewManagerService.getInstance(project)
        val review = seededCommitReview("endpoint-comment-context")
        ReviewStateService.getInstance(project).addReview(review)
        manager.addComment(review.id, sampleChangedFile("src/Foo.kt"), DiffSide.RIGHT, 2, "needs fix")
        val comment = manager.findReview(review.id)!!.comments.single()

        runBlocking {
            withConnection { client ->
                val contextResult = client.callTool("review_get_comment_context", mapOf("commentId" to comment.id), emptyMap<String, Any>(), null)
                assertSuccessful(contextResult)
                val decodedContext = json.decodeFromString<CommentContextResult>(contextResult.text())
                assertThat(decodedContext.comment.id).isEqualTo(comment.id)
                assertThat(decodedContext.review.id).isEqualTo(review.id)

                val resolveResult = client.callTool(
                    "review_mark_comment_resolved",
                    mapOf("commentId" to comment.id, "message" to "implemented", "agentName" to "test-agent", "runId" to "run-1"),
                    emptyMap<String, Any>(),
                    null,
                )
                assertSuccessful(resolveResult)
                val decodedMutation = json.decodeFromString<MutationResult>(resolveResult.text())
                assertThat(decodedMutation.ok).isTrue()
                assertThat(manager.findReview(review.id)!!.comments.single().status).isEqualTo(CommentStatus.RESOLVED)
            }
        }
    }

    @Test
    fun reviewExportAndErrorBranchesWorkOverRealEndpoint() {
        val review = seededCommitReview("endpoint-export")
        ReviewStateService.getInstance(project).addReview(review)

        runBlocking {
            withConnection { client ->
                val markdown = client.callTool("review_export", mapOf("reviewId" to review.id, "format" to "markdown"), emptyMap<String, Any>(), null)
                assertSuccessful(markdown)
                assertThat(markdown.text()).contains("Agentic Review")

                val invalid = client.callTool("review_export", mapOf("reviewId" to review.id, "format" to "yaml"), emptyMap<String, Any>(), null)
                assertThat(invalid.isError).isTrue()
                assertThat(invalid.text()).contains("Unsupported export format")

                val missingComment = client.callTool("review_get_comment_context", mapOf("commentId" to "missing"), emptyMap<String, Any>(), null)
                assertThat(missingComment.isError).isTrue()
                assertThat(missingComment.text()).contains("Comment not found")
            }
        }
    }

    @Test
    fun reviewSelectorsAndProjectSpecificErrorsWorkOverRealEndpoint() {
        val manager = ReviewManagerService.getInstance(project)
        val stateService = ReviewStateService.getInstance(project)
        val review1 = seededCommitReview("selector-1", commitHash = "abc123def456", title = "Review one")
        val review2 = seededCommitReview("selector-2", commitHash = "abc123999999", title = "Review two")
        stateService.addReview(review1)
        stateService.addReview(review2)
        manager.selectReview(review1.id)

        runBlocking {
            withConnection { client ->
                val latest = client.callTool("review_get_review", mapOf("selector" to "latest", "includeComments" to false), emptyMap<String, Any>(), null)
                assertSuccessful(latest)
                val latestDecoded = json.decodeFromString<ReviewResult>(latest.text())
                assertThat(latestDecoded.review.id).isNotBlank()

                val current = client.callTool("review_get_review", mapOf("selector" to "current", "includeComments" to false), emptyMap<String, Any>(), null)
                assertSuccessful(current)
                val currentDecoded = json.decodeFromString<ReviewResult>(current.text())
                assertThat(currentDecoded.review.id).isEqualTo(review1.id)

                val byCommit = client.callTool("review_get_review", mapOf("selector" to "commit:abc123def"), emptyMap<String, Any>(), null)
                assertSuccessful(byCommit)
                val byCommitDecoded = json.decodeFromString<ReviewResult>(byCommit.text())
                assertThat(byCommitDecoded.review.id).isEqualTo(review1.id)

                val ambiguous = client.callTool("review_get_review", mapOf("selector" to "commit:abc123"), emptyMap<String, Any>(), null)
                assertThat(ambiguous.isError).isTrue()
                assertThat(ambiguous.text()).contains("Multiple reviews match selector")

                val unsupported = client.callTool("review_get_review", mapOf("selector" to "weird"), emptyMap<String, Any>(), null)
                assertThat(unsupported.isError).isTrue()
                assertThat(unsupported.text()).contains("Unsupported review selector")

                val blankCommit = client.callTool("review_get_review", mapOf("selector" to "commit:   "), emptyMap<String, Any>(), null)
                assertThat(blankCommit.isError).isTrue()
                assertThat(blankCommit.text()).contains("Commit selector must include hash")

                val missingCommit = client.callTool("review_get_review", mapOf("selector" to "commit:fffffff"), emptyMap<String, Any>(), null)
                assertThat(missingCommit.isError).isTrue()
                assertThat(missingCommit.text()).contains("No review found for commit selector")
            }
        }
    }

    @Test
    fun reviewCurrentLatestAndUncommittedMissingBranchesWorkOverRealEndpoint() {
        val manager = ReviewManagerService.getInstance(project)
        val stateService = ReviewStateService.getInstance(project)

        stateService.reviews().map { it.id }.forEach(stateService::removeReview)
        manager.selectReview(null)

        runBlocking {
            withConnection { client ->
                val noCurrent = client.callTool("review_get_review", mapOf("selector" to "current"), emptyMap<String, Any>(), null)
                assertThat(noCurrent.isError).isTrue()
                assertThat(noCurrent.text()).contains("No current review selected")

                val noLatest = client.callTool("review_get_review", mapOf("selector" to "latest"), emptyMap<String, Any>(), null)
                assertThat(noLatest.isError).isTrue()
                assertThat(noLatest.text()).contains("No reviews found")

                val noLatestOpen = client.callTool("review_get_review", mapOf("selector" to "latest-open"), emptyMap<String, Any>(), null)
                assertThat(noLatestOpen.isError).isTrue()
                assertThat(noLatestOpen.text()).contains("No open reviews found")

                val noUncommitted = client.callTool("review_get_review", mapOf("selector" to "uncommitted"), emptyMap<String, Any>(), null)
                assertThat(noUncommitted.isError).isTrue()
                assertThat(noUncommitted.text()).contains("No uncommitted review found")
            }
        }
    }

    @Test
    fun reviewMarkCommentResolvedUsesDefaultAgentNameOverRealEndpoint() {
        val manager = ReviewManagerService.getInstance(project)
        val review = seededCommitReview("default-agent")
        ReviewStateService.getInstance(project).addReview(review)
        manager.addComment(review.id, sampleChangedFile("src/Foo.kt"), DiffSide.RIGHT, 2, "needs fix")
        val comment = manager.findReview(review.id)!!.comments.single()

        runBlocking {
            withConnection { client ->
                val resolveResult = client.callTool(
                    "review_mark_comment_resolved",
                    mapOf("commentId" to comment.id, "message" to "implemented"),
                    emptyMap<String, Any>(),
                    null,
                )
                assertSuccessful(resolveResult)
                val decoded = json.decodeFromString<MutationResult>(resolveResult.text())
                assertThat(decoded.ok).isTrue()
                assertThat(manager.findReview(review.id)!!.comments.single().agentMetadata!!.addressedBy).isEqualTo("test-client")
            }
        }
    }

    @Test
    fun turnSnapshotParsingErrorBranchesWorkOverRealEndpoint() {
        runBlocking {
            withConnection { client ->
                val begin = client.callTool(
                    "review_turn_snapshot_begin",
                    mapOf("sessionId" to "session-invalid", "stepId" to "step-invalid", "projectPath" to project.basePath!!),
                    emptyMap<String, Any>(),
                    null,
                )
                assertSuccessful(begin)

                val invalidPayloads = client.callTool(
                    "review_turn_snapshot_end",
                    mapOf(
                        "sessionId" to "session-invalid",
                        "stepId" to "step-invalid",
                        "status" to "failed",
                        "changedPathsJson" to "not-a-json-array",
                        "toolCallsJson" to "not-a-json-array",
                    ),
                    emptyMap<String, Any>(),
                    null,
                )
                assertSuccessful(invalidPayloads)
                val decoded = json.decodeFromString<TurnSnapshotResult>(invalidPayloads.text())
                assertThat(decoded.ok).isTrue()

                val listed = client.callTool("review_list_turn_snapshots", emptyMap<String, Any>(), emptyMap<String, Any>(), null)
                assertSuccessful(listed)
                val listDecoded = json.decodeFromString<TurnSnapshotListResult>(listed.text())
                assertThat(listDecoded.turns.single().status).isEqualTo("failed")
                assertThat(listDecoded.turns.single().changedFileCount).isEqualTo(0)
            }
        }
    }

    @Test
    fun turnSnapshotLifecycleWorksOverRealEndpoint() {
        runBlocking {
            withConnection { client ->
                val begin = client.callTool(
                    "review_turn_snapshot_begin",
                    mapOf("sessionId" to "session-1", "stepId" to "step-1", "projectPath" to project.basePath!!),
                    emptyMap<String, Any>(),
                    null,
                )
                assertSuccessful(begin)
                val beginDecoded = json.decodeFromString<TurnSnapshotResult>(begin.text())
                assertThat(beginDecoded.ok).isTrue()

                val end = client.callTool(
                    "review_turn_snapshot_end",
                    mapOf("sessionId" to "session-1", "stepId" to "step-1", "status" to "completed", "changedPathsJson" to "[\"src/Foo.kt\"]"),
                    emptyMap<String, Any>(),
                    null,
                )
                assertSuccessful(end)
                val endDecoded = json.decodeFromString<TurnSnapshotResult>(end.text())
                assertThat(endDecoded.turnId).isEqualTo(beginDecoded.turnId)

                val listed = client.callTool("review_list_turn_snapshots", emptyMap<String, Any>(), emptyMap<String, Any>(), null)
                assertSuccessful(listed)
                val listDecoded = json.decodeFromString<TurnSnapshotListResult>(listed.text())
                assertThat(listDecoded.turns).hasSize(1)
                assertThat(listDecoded.turns.single().sessionId).isEqualTo("session-1")
            }
        }
    }

    private suspend fun <T> withConnection(action: suspend (Client) -> T): T {
        var result: Result<T>? = null
        val server = ApplicationManager.getApplication().getService(McpServerService::class.java)
        val options = McpServerService.McpSessionOptions(
            McpServerService.AskCommandExecutionMode.DONT_ASK,
            McpToolFilter.AllowAll,
            "test-agent",
        )

        suspend fun exerciseSession(
            @Suppress("UNUSED_PARAMETER") scope: kotlinx.coroutines.CoroutineScope,
            port: Int,
            authHeader: String,
            authToken: String,
        ) {
            val httpClient = HttpClient(CIO) {
                install(SSE)
            }
            try {
                val client = Client(Implementation("test-client", "1.0.0"), ClientOptions())
                val transport = StreamableHttpClientTransport(httpClient, "http://127.0.0.1:$port/stream", requestBuilder = {
                    header(IJ_MCP_SERVER_PROJECT_PATH, project.basePath!!)
                    header(authHeader, authToken)
                })
                try {
                    client.connect(transport)
                    result = runCatching { action(client) }
                } finally {
                    transport.close()
                }
            } finally {
                httpClient.close()
            }
        }

        server.authorizedSession(options, ::exerciseSession)
        return result?.getOrThrow() ?: error("Authorized MCP session finished without producing a result")
    }

    private fun assertSuccessful(result: CallToolResult) {
        assertThat(result.isError)
            .withFailMessage("MCP tool returned error content: %s", result.content)
            .isFalse()
    }

    private fun CallToolResult.text(): String = (content.single() as io.modelcontextprotocol.kotlin.sdk.types.TextContent).text

    private fun seededCommitReview(suffix: String, commitHash: String = "abc123def456", title: String = "Endpoint review"): Review = Review(
        id = "review-mcp-endpoint-$suffix",
        title = title,
        target = ReviewTarget(type = ReviewTargetType.COMMIT, commitHash = commitHash, parentHash = "def456abc123", subject = "Fix user lookup"),
        repositoryRoot = project.basePath!!,
        createdAt = "2026-05-07T14:20:00+03:00",
        updatedAt = "2026-05-07T14:20:00+03:00",
        status = ReviewStatus.OPEN,
    )

    private fun sampleChangedFile(path: String): ChangedFile = ChangedFile(
        filePath = path,
        status = ChangedFileStatus.MODIFIED,
        beforeContent = ReviewContent("zero\none\nold-three\nfour", "before", path),
        afterContent = ReviewContent("one\ntwo\nthree\nfour", "after", path),
    )
}
