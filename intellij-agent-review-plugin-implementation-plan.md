# IntelliJ Local Review Plugin — Implementation Plan

## 0. Purpose

Build an IntelliJ IDEA plugin that provides a local PR-like review workflow for humans and coding agents.

The plugin creates a dedicated **Review** tool window where the user can review diffs, add persistent comments, and expose those comments to agents through MCP. Agents can then read unresolved comments, implement fixes, and mark comments as addressed.

This plugin should **not** modify or depend on the IntelliJ Commit tool window. It should reuse IntelliJ Platform APIs for tool windows, Git/VCS data, persistence, and diff rendering.

---

## 1. Core Requirements

### 1.1 Review Tool Window

Create a new IntelliJ tool window named:

```text
Review
```

The tool window should behave like a dedicated local PR review panel.

It should support two review modes:

1. **Uncommitted Changes Review**
   - Default mode.
   - Similar to reviewing changes in the Commit window, but without using the Commit window.
   - Shows changed files from the working tree.
   - Allows inline or line-based comments on changed files.

2. **Committed Changes Review**
   - User can select one or more commits from IDEA's Git Log / Git window and start a review.
   - For committed reviews, each comment must be able to point to the specific commit being reviewed.
   - MVP may support reviewing a single selected commit first.
   - Multi-commit/range review can be added after the single-commit workflow is stable.

### 1.2 Persistent Review Comments

The review system should behave like a lightweight PR system.

Users should be able to:

- See existing reviews.
- Reopen previous reviews.
- See comments they added.
- Add comments to a file and line.
- Mark comments resolved/unresolved.
- Track whether an agent addressed a comment.

Each comment must include:

- UUID.
- Review ID.
- File path.
- Line anchor.
- Optional commit hash.
- Comment body.
- Status.
- Created/updated timestamps.
- Enough context for an agent to understand the comment.

### 1.3 MCP Access

The plugin should expose review data to agents through MCP.

Agents should be able to:

- List reviews.
- Get a specific review.
- List unresolved comments.
- Get a comment with file/diff context.
- Mark a comment as addressed.
- Optionally mark a comment as resolved, depending on a setting.

The preferred approach is to integrate with IntelliJ's built-in MCP Server support where possible. A standalone local MCP server should be a fallback only.

---

## 2. Research Links for the Implementation Agent

Before implementing, fetch and read these resources.

### IntelliJ Plugin Development

```text
https://plugins.jetbrains.com/docs/intellij/developing-plugins.html
https://plugins.jetbrains.com/docs/intellij/creating-plugin-project.html
https://plugins.jetbrains.com/docs/intellij/using-kotlin.html
https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html
https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html
https://github.com/JetBrains/intellij-sdk-code-samples
https://github.com/JetBrains/intellij-platform-plugin-template
```

### Tool Windows

```text
https://plugins.jetbrains.com/docs/intellij/tool-windows.html
https://plugins.jetbrains.com/docs/intellij/tool-window.html
```

### Diff APIs

```text
https://github.com/JetBrains/intellij-community/blob/master/platform/diff-api/src/com/intellij/diff/DiffManager.java
https://github.com/JetBrains/intellij-community/tree/master/platform/diff-api
https://github.com/JetBrains/intellij-community/tree/master/platform/diff-impl
```

### Persistence

```text
https://plugins.jetbrains.com/docs/intellij/persisting-state-of-components.html
```

### VCS / Git Integration

```text
https://plugins.jetbrains.com/docs/intellij/vcs-integration-for-plugins.html
https://github.com/JetBrains/intellij-community/tree/master/plugins/git4idea
https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/resources/META-INF/plugin.xml
```

### IntelliJ Platform Gradle Plugin

```text
https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
https://github.com/JetBrains/intellij-platform-gradle-plugin
```

### MCP

```text
https://www.jetbrains.com/help/idea/mcp-server.html
https://github.com/JetBrains/mcp-server-plugin/blob/master/README.md
https://modelcontextprotocol.io/specification/2025-11-25
https://modelcontextprotocol.io/specification/2025-06-18/basic/transports
https://modelcontextprotocol.io/specification/2025-06-18/server/tools
https://github.com/modelcontextprotocol/kotlin-sdk
```

---

## 3. Recommended Technology Stack

Use:

```text
Language: Kotlin
Build system: Gradle Kotlin DSL
Plugin build system: IntelliJ Platform Gradle Plugin 2.x
Target IDE: IntelliJ IDEA 2025.2+ if using built-in MCP Server
Java target: Java 21
UI framework: IntelliJ Platform Swing UI
Diff rendering: IntelliJ DiffManager / DiffRequestPanel / SimpleDiffRequest
Persistence: Project-level PersistentStateComponent
Serialization: kotlinx.serialization JSON
VCS: IntelliJ VCS APIs + Git4Idea APIs where possible
MCP: JetBrains built-in MCP Server extension points if possible
```

Avoid:

```text
Custom diff rendering
Patching the Commit tool window
Depending heavily on internal IntelliJ implementation classes
Global background servers without user opt-in
Letting agents silently resolve comments by default
```

---

## 4. Project Bootstrap

### 4.1 Create the Plugin Project

Preferred bootstrap:

```bash
git clone https://github.com/JetBrains/intellij-platform-plugin-template agent-review-plugin
cd agent-review-plugin
```

Alternative:

```text
IntelliJ IDEA → File → New → Project → IDE Plugin
```

Use Kotlin.

### 4.2 Suggested Plugin Coordinates

Use one of these package names:

```text
dev.agentreview.intellij
```

or, for internal/company usage:

```text
tr.gov.tubitak.bilgem.yte.agentreview
```

The rest of this plan uses:

```text
dev.agentreview.intellij
```

---

## 5. Project Structure

Create the following structure:

```text
agent-review-plugin/
├─ build.gradle.kts
├─ gradle.properties
├─ settings.gradle.kts
├─ README.md
├─ src/
│  ├─ main/
│  │  ├─ kotlin/
│  │  │  └─ dev/agentreview/intellij/
│  │  │     ├─ ReviewToolWindowFactory.kt
│  │  │     ├─ actions/
│  │  │     │  ├─ StartUncommittedReviewAction.kt
│  │  │     │  ├─ StartReviewFromGitLogAction.kt
│  │  │     │  ├─ StartReviewFromCommitHashAction.kt
│  │  │     │  ├─ AddReviewCommentAction.kt
│  │  │     │  ├─ ExportReviewJsonAction.kt
│  │  │     │  ├─ ExportReviewXmlAction.kt
│  │  │     │  └─ CopyAgentPromptAction.kt
│  │  │     ├─ diff/
│  │  │     │  ├─ ReviewDiffPanel.kt
│  │  │     │  ├─ DiffRequestBuilder.kt
│  │  │     │  ├─ DiffAnchorExtractor.kt
│  │  │     │  ├─ DiffLineNavigator.kt
│  │  │     │  └─ DiffContextExtractor.kt
│  │  │     ├─ model/
│  │  │     │  ├─ Review.kt
│  │  │     │  ├─ ReviewTarget.kt
│  │  │     │  ├─ ReviewComment.kt
│  │  │     │  ├─ CommentAnchor.kt
│  │  │     │  ├─ ReviewStatus.kt
│  │  │     │  ├─ CommentStatus.kt
│  │  │     │  ├─ CommentSeverity.kt
│  │  │     │  └─ DiffSide.kt
│  │  │     ├─ persistence/
│  │  │     │  ├─ ReviewState.kt
│  │  │     │  └─ ReviewStateService.kt
│  │  │     ├─ vcs/
│  │  │     │  ├─ ChangedFile.kt
│  │  │     │  ├─ ChangedFileStatus.kt
│  │  │     │  ├─ ReviewContent.kt
│  │  │     │  ├─ GitRepositoryResolver.kt
│  │  │     │  ├─ UncommittedChangesProvider.kt
│  │  │     │  ├─ CommitChangesProvider.kt
│  │  │     │  └─ GitCommandFallback.kt
│  │  │     ├─ mcp/
│  │  │     │  ├─ ReviewMcpToolRegistrar.kt
│  │  │     │  ├─ ListReviewsTool.kt
│  │  │     │  ├─ GetReviewTool.kt
│  │  │     │  ├─ ListUnresolvedCommentsTool.kt
│  │  │     │  ├─ GetCommentContextTool.kt
│  │  │     │  ├─ MarkCommentAddressedTool.kt
│  │  │     │  ├─ MarkCommentResolvedTool.kt
│  │  │     │  └─ ExportReviewTool.kt
│  │  │     ├─ export/
│  │  │     │  ├─ ReviewJsonExporter.kt
│  │  │     │  ├─ ReviewXmlExporter.kt
│  │  │     │  └─ AgentPromptBuilder.kt
│  │  │     └─ ui/
│  │  │        ├─ ReviewToolWindowPanel.kt
│  │  │        ├─ ReviewListPanel.kt
│  │  │        ├─ ChangedFilesPanel.kt
│  │  │        ├─ CommentsPanel.kt
│  │  │        ├─ CommentDetailPanel.kt
│  │  │        ├─ AddCommentDialog.kt
│  │  │        └─ NewReviewDialog.kt
│  │  └─ resources/
│  │     ├─ META-INF/
│  │     │  └─ plugin.xml
│  │     └─ icons/
│  │        └─ review.svg
│  └─ test/
│     └─ kotlin/
└─ docs/
   └─ mcp-agent-usage.md
```

---

## 6. Gradle Setup

### 6.1 `build.gradle.kts`

The agent should update versions to current stable compatible versions.

Example structure:

```kotlin
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "REPLACE_WITH_CURRENT_KOTLIN_VERSION"
    id("org.jetbrains.intellij.platform") version "REPLACE_WITH_CURRENT_INTELLIJ_PLATFORM_GRADLE_PLUGIN_VERSION"
    id("org.jetbrains.kotlin.plugin.serialization") version "REPLACE_WITH_CURRENT_KOTLIN_VERSION"
}

group = "dev.agentreview"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2025.2")
        bundledPlugin("Git4Idea")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:REPLACE_WITH_CURRENT_VERSION")
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "21"
    }
}
```

If targeting an older IntelliJ version, update:

```kotlin
intellijIdea("2025.2")
```

to the desired supported version and verify MCP availability separately.

### 6.2 `gradle.properties`

Example:

```properties
pluginGroup=dev.agentreview
pluginName=Local Review
pluginRepositoryUrl=
pluginVersion=0.1.0-SNAPSHOT

pluginSinceBuild=252
pluginUntilBuild=

platformType=IC
platformVersion=2025.2
```

---

## 7. `plugin.xml`

Create:

```xml
<idea-plugin>
    <id>dev.agentreview.intellij</id>
    <name>Local Review</name>
    <vendor>Internal</vendor>

    <description>
        Local PR-like review workflow for IntelliJ IDEA with persistent review comments and MCP access for coding agents.
    </description>

    <depends>com.intellij.modules.platform</depends>
    <depends>com.intellij.modules.vcs</depends>
    <depends>Git4Idea</depends>

    <extensions defaultExtensionNs="com.intellij">
        <toolWindow
            id="Review"
            anchor="left"
            factoryClass="dev.agentreview.intellij.ReviewToolWindowFactory"
            icon="/icons/review.svg"/>
    </extensions>

    <actions>
        <group
            id="LocalReview.Actions"
            text="Local Review"
            popup="true">

            <action
                id="LocalReview.StartUncommittedReview"
                class="dev.agentreview.intellij.actions.StartUncommittedReviewAction"
                text="Start Review of Uncommitted Changes"/>

            <action
                id="LocalReview.StartReviewFromGitLog"
                class="dev.agentreview.intellij.actions.StartReviewFromGitLogAction"
                text="Start Review from Selected Commit(s)"/>

            <action
                id="LocalReview.StartReviewFromCommitHash"
                class="dev.agentreview.intellij.actions.StartReviewFromCommitHashAction"
                text="Start Review from Commit Hash..."/>

            <action
                id="LocalReview.AddComment"
                class="dev.agentreview.intellij.actions.AddReviewCommentAction"
                text="Add Review Comment"/>

            <action
                id="LocalReview.ExportJson"
                class="dev.agentreview.intellij.actions.ExportReviewJsonAction"
                text="Export Review as JSON"/>

            <action
                id="LocalReview.ExportXml"
                class="dev.agentreview.intellij.actions.ExportReviewXmlAction"
                text="Export Review as XML"/>

            <action
                id="LocalReview.CopyAgentPrompt"
                class="dev.agentreview.intellij.actions.CopyAgentPromptAction"
                text="Copy Agent Prompt"/>
        </group>
    </actions>
</idea-plugin>
```

Later, register actions into more specific IntelliJ menus/toolbars after the MVP works.

---

## 8. UI Design

### 8.1 Tool Window Layout

The `Review` tool window should have this layout:

```text
Review Tool Window
├─ Top Toolbar
│  ├─ New Review: Uncommitted Changes
│  ├─ New Review: Commit Hash...
│  ├─ Refresh
│  ├─ Export JSON
│  ├─ Export XML
│  └─ Copy Agent Prompt
│
├─ Main Split
│  ├─ Left Panel
│  │  ├─ Reviews list
│  │  ├─ Review status filter: Open / Closed / All
│  │  └─ Changed files tree for selected review
│  │
│  └─ Center Panel
│     └─ Embedded IntelliJ diff viewer
│
└─ Bottom or Right Panel
   ├─ Comments for selected review
   ├─ Comments for selected file
   ├─ Unresolved comments
   └─ Comment detail / resolve controls
```

### 8.2 Empty State

If there are no reviews, show:

```text
No reviews yet.

[Review Uncommitted Changes]
[Review Commit Hash...]
```

### 8.3 Review List

Each review row should show:

```text
Title
Target type: UNCOMMITTED / COMMIT / COMMIT_RANGE
Open comments count
Updated timestamp
Status: OPEN / CLOSED / ARCHIVED
```

Example:

```text
Uncommitted changes — 2026-05-07 14:20
UNCOMMITTED · 4 open comments · updated 14:31
```

Example:

```text
abc1234 Fix user lookup
COMMIT · 2 open comments · updated yesterday
```

### 8.4 Changed Files Tree

Show changed files for the selected review.

Example:

```text
src/main/kotlin/Foo.kt
src/main/kotlin/Bar.kt
README.md
```

When the user selects a file:

1. Build a diff request for that file.
2. Show the diff in the center panel.
3. Load comments for the selected file.
4. Highlight or list comments.

### 8.5 Comments Panel

Show comments grouped by file and line.

Each comment should show:

```text
Line 47 · MUST_FIX · OPEN
Avoid !! here. Return a typed error or handle null explicitly.
[Mark Addressed] [Resolve] [Reopen] [Edit]
```

Statuses should be visually distinct:

```text
OPEN
ADDRESSED
RESOLVED
WONT_FIX
STALE
```

Recommended status semantics:

- `OPEN`: Human added the comment and it still needs work.
- `ADDRESSED`: Agent claims it implemented the requested change.
- `RESOLVED`: Human accepted the fix.
- `WONT_FIX`: Human decided not to fix.
- `STALE`: The anchor could not be matched after code changed.

---

## 9. Review Modes

## 9.1 Uncommitted Changes Review

Default review mode.

User flow:

```text
Review tool window → New Review: Uncommitted Changes
```

The plugin should:

1. Detect Git repositories in the project.
2. Collect uncommitted changes.
3. Create a `Review` with target type `UNCOMMITTED`.
4. List changed files.
5. Show diffs against `HEAD`.
6. Allow comments on changed files.

Initial behavior:

```text
baseRef = HEAD
headRef = WORKTREE
```

Review target example:

```json
{
  "type": "UNCOMMITTED",
  "baseRef": "HEAD",
  "headRef": "WORKTREE",
  "changelistId": null
}
```

For MVP, if the working tree changes after review creation, it is acceptable to rebuild the diff from the current working tree. Comments should still persist. If a comment anchor no longer matches, mark it as `STALE`.

For v2, add patch snapshots so uncommitted reviews can preserve historical diffs exactly.

## 9.2 Committed Changes Review

User flow A:

```text
Git Log → select commit → Start Review
```

User flow B fallback:

```text
Review tool window → New Review: Commit Hash... → enter commit hash
```

The plugin should:

1. Resolve the selected commit.
2. Get the first parent commit.
3. List files changed in the commit.
4. Show diff from parent to selected commit.
5. Create a `Review` with target type `COMMIT`.
6. Store `commitHash` in review and comment anchors.

Review target example:

```json
{
  "type": "COMMIT",
  "commitHash": "abc123def456",
  "parentHash": "def456abc123",
  "subject": "Fix user lookup"
}
```

For merge commits:

MVP options:

- Show a clear error: "Merge commit review is not supported yet."
- Or review against first parent only.

Recommended MVP: review against first parent only, with an indicator in the UI.

## 9.3 Multiple Selected Commits

MVP behavior:

- If multiple commits are selected, create one review per commit.

Reason:

- The user explicitly wants committed comments to point to a specific commit.
- One review per commit keeps anchors simpler.

v2 behavior:

- Add `COMMIT_RANGE` review:
  - base = parent of oldest selected commit or merge-base
  - head = newest selected commit

Range target example:

```json
{
  "type": "COMMIT_RANGE",
  "baseRef": "abc123",
  "headRef": "def456"
}
```

---

## 10. Data Model

Use Kotlin data classes and persist them via IntelliJ persistent state. Use kotlinx.serialization for JSON export.

### 10.1 `Review`

```kotlin
@Serializable
data class Review(
    val id: String,
    var title: String,
    val target: ReviewTarget,
    val repositoryRoot: String,
    val createdAt: String,
    var updatedAt: String,
    var status: ReviewStatus = ReviewStatus.OPEN,
    val comments: MutableList<ReviewComment> = mutableListOf()
)
```

### 10.2 `ReviewTarget`

Use a serializable sealed hierarchy or a simpler enum-backed class if IntelliJ XML persistence has issues with sealed classes.

Preferred domain model:

```kotlin
@Serializable
sealed class ReviewTarget {
    @Serializable
    data class Uncommitted(
        val baseRef: String = "HEAD",
        val headRef: String = "WORKTREE",
        val changelistId: String? = null
    ) : ReviewTarget()

    @Serializable
    data class Commit(
        val commitHash: String,
        val parentHash: String?,
        val subject: String?
    ) : ReviewTarget()

    @Serializable
    data class CommitRange(
        val baseRef: String,
        val headRef: String
    ) : ReviewTarget()
}
```

If sealed class persistence becomes painful, use:

```kotlin
@Serializable
data class ReviewTarget(
    val type: ReviewTargetType,
    val baseRef: String? = null,
    val headRef: String? = null,
    val commitHash: String? = null,
    val parentHash: String? = null,
    val subject: String? = null,
    val changelistId: String? = null
)

@Serializable
enum class ReviewTargetType {
    UNCOMMITTED,
    COMMIT,
    COMMIT_RANGE
}
```

### 10.3 `ReviewComment`

```kotlin
@Serializable
data class ReviewComment(
    val id: String,
    val reviewId: String,
    val filePath: String,
    var anchor: CommentAnchor,
    var body: String,
    var severity: CommentSeverity = CommentSeverity.MUST_FIX,
    var status: CommentStatus = CommentStatus.OPEN,
    val createdAt: String,
    var updatedAt: String,
    var author: String? = null,
    var agentMetadata: AgentMetadata? = null
)
```

### 10.4 `CommentAnchor`

Do not store only a line number. Store line number plus context.

```kotlin
@Serializable
data class CommentAnchor(
    val side: DiffSide,
    val oldLine: Int? = null,
    val newLine: Int? = null,
    val hunkHeader: String? = null,
    val selectedText: String? = null,
    val beforeContext: List<String> = emptyList(),
    val afterContext: List<String> = emptyList(),
    val commitHash: String? = null
)
```

### 10.5 `AgentMetadata`

```kotlin
@Serializable
data class AgentMetadata(
    val addressedBy: String? = null,
    val addressedAt: String? = null,
    val message: String? = null,
    val runId: String? = null
)
```

### 10.6 Enums

```kotlin
@Serializable
enum class ReviewStatus {
    OPEN,
    CLOSED,
    ARCHIVED
}

@Serializable
enum class CommentStatus {
    OPEN,
    ADDRESSED,
    RESOLVED,
    WONT_FIX,
    STALE
}

@Serializable
enum class CommentSeverity {
    NOTE,
    QUESTION,
    SHOULD_FIX,
    MUST_FIX
}

@Serializable
enum class DiffSide {
    LEFT,
    RIGHT
}
```

---

## 11. Persistence

Use a project-level service implementing `PersistentStateComponent`.

### 11.1 Service

```kotlin
@State(
    name = "LocalReviewState",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
@Service(Service.Level.PROJECT)
class ReviewStateService : PersistentStateComponent<ReviewState> {
    private var state = ReviewState()

    override fun getState(): ReviewState = state

    override fun loadState(state: ReviewState) {
        this.state = state
    }

    fun reviews(): List<Review> = state.reviews

    fun addReview(review: Review) {
        state.reviews.add(review)
    }

    fun findReview(reviewId: String): Review? {
        return state.reviews.firstOrNull { it.id == reviewId }
    }
}
```

### 11.2 State

```kotlin
data class ReviewState(
    var schemaVersion: Int = 1,
    var reviews: MutableList<Review> = mutableListOf()
)
```

### 11.3 Requirements

Persistence must satisfy:

```text
Reviews survive IDE restart.
Comments survive IDE restart.
Comment statuses survive IDE restart.
Every review has a stable UUID.
Every comment has a stable UUID.
Deleting a review requires confirmation.
Exporting does not mutate state.
```

### 11.4 Optional File-Based Sync

Add optional export/sync to:

```text
.agent-review/reviews.json
```

This should be opt-in because review comments may contain sensitive internal information.

---

## 12. Diff Rendering

## 12.1 Use IntelliJ DiffManager

Create an embedded diff panel inside the Review tool window.

Example:

```kotlin
class ReviewDiffPanel(
    private val project: Project,
    private val parentDisposable: Disposable
) {
    private val requestPanel = DiffManager.getInstance()
        .createRequestPanel(project, parentDisposable, null)

    val component: JComponent
        get() = requestPanel.component

    fun showDiff(request: DiffRequest) {
        requestPanel.setRequest(request)
    }
}
```

### 12.2 Build Diff Requests

For two text revisions:

```kotlin
class DiffRequestBuilder(
    private val project: Project
) {
    fun buildTextDiff(
        title: String,
        beforeText: String,
        afterText: String,
        beforeTitle: String,
        afterTitle: String
    ): DiffRequest {
        val factory = DiffContentFactory.getInstance()

        val beforeContent = factory.create(project, beforeText)
        val afterContent = factory.create(project, afterText)

        return SimpleDiffRequest(
            title,
            beforeContent,
            afterContent,
            beforeTitle,
            afterTitle
        )
    }
}
```

For files, prefer `VirtualFile` / `DocumentContent` when possible so syntax highlighting works.

### 12.3 MVP Comment Anchoring

If extracting the exact selected diff line from the embedded diff viewer is difficult, implement this MVP:

1. User selects a changed file.
2. User clicks **Add Comment**.
3. Dialog asks for:
   - side: old/new
   - line number
   - comment body
   - severity
4. Plugin stores comment.

This is acceptable for the first milestone because it proves the review model, persistence, export, and MCP.

### 12.4 Improved Comment Anchoring

After MVP:

- Detect current caret line in the right-side editor.
- Extract selected text if there is a selection.
- Store nearby context lines.
- Store hunk header if available.
- Show gutter marker or inlay for comments.

### 12.5 Re-Anchoring

When opening a review:

1. Try exact line number.
2. Check whether `selectedText` still matches.
3. If not, search nearby lines.
4. If still not found, mark comment `STALE`.
5. Keep stale comments visible.

---

## 13. VCS and Git Implementation

## 13.1 `ChangedFile`

```kotlin
data class ChangedFile(
    val filePath: String,
    val status: ChangedFileStatus,
    val beforeContent: ReviewContent?,
    val afterContent: ReviewContent?
)
```

### 13.2 `ChangedFileStatus`

```kotlin
enum class ChangedFileStatus {
    ADDED,
    MODIFIED,
    DELETED,
    RENAMED,
    COPIED,
    UNKNOWN
}
```

### 13.3 `ReviewContent`

```kotlin
data class ReviewContent(
    val text: String,
    val revisionTitle: String,
    val filePath: String
)
```

## 13.4 Uncommitted Changes Provider

Create:

```kotlin
class UncommittedChangesProvider(
    private val project: Project
)
```

Responsibilities:

- Find Git repositories in project.
- List uncommitted local changes.
- Return `ChangedFile` objects.
- For each file:
  - before content = content at `HEAD`
  - after content = working tree content

Implementation notes:

- First try IntelliJ VCS APIs:
  - `ChangeListManager`
  - `Change`
  - `ContentRevision`
- If IntelliJ APIs do not provide the needed content cleanly, use a Git command fallback.

Git fallback examples:

```bash
git diff --name-status HEAD
git show HEAD:path/to/file
cat path/to/file
```

Handle added/deleted files:

```text
ADDED: beforeContent = null, afterContent = working tree content
DELETED: beforeContent = HEAD content, afterContent = null
MODIFIED: both before and after
```

## 13.5 Commit Changes Provider

Create:

```kotlin
class CommitChangesProvider(
    private val project: Project
)
```

Responsibilities:

- Given `commitHash`, resolve parent.
- List changed files.
- Load before content from parent.
- Load after content from commit.

Git fallback examples:

```bash
git rev-parse <commit>^
git diff --name-status <parent> <commit>
git show <parent>:path/to/file
git show <commit>:path/to/file
```

For single commit review:

```text
base = first parent
head = selected commit
```

For merge commits:

```text
MVP: compare against first parent
```

## 13.6 Git Log Integration

Desired action:

```text
Git Log → right-click commit → Start Review
```

Implementation steps:

1. Create `StartReviewFromGitLogAction`.
2. Register it.
3. In `update()`, enable only if selected commits are available.
4. In `actionPerformed()`, extract selected commit hash(es).
5. Create review(s).
6. Open Review tool window.
7. Select the created review.

Fallback if selected commit extraction is difficult:

```text
Review → New Review from Commit Hash...
```

The fallback must be part of v1. Git Log integration can be v1 if straightforward or v1.1 if not.

---

## 14. Review Creation Workflows

## 14.1 Start Review of Uncommitted Changes

Pseudo-flow:

```kotlin
fun startUncommittedReview(project: Project) {
    val changesProvider = UncommittedChangesProvider(project)
    val changedFiles = changesProvider.getChangedFiles()

    val review = Review(
        id = UUID.randomUUID().toString(),
        title = "Uncommitted changes — ${nowForDisplay()}",
        target = ReviewTarget.Uncommitted(),
        repositoryRoot = resolveRepositoryRoot(project),
        createdAt = nowIso(),
        updatedAt = nowIso()
    )

    reviewStateService.addReview(review)
    reviewUi.openAndSelect(review.id)
}
```

Changed files can be computed lazily instead of stored inside `Review`.

## 14.2 Start Review from Commit

Pseudo-flow:

```kotlin
fun startCommitReview(project: Project, commitHash: String) {
    val provider = CommitChangesProvider(project)
    val metadata = provider.getCommitMetadata(commitHash)

    val review = Review(
        id = UUID.randomUUID().toString(),
        title = "${metadata.shortHash} ${metadata.subject}",
        target = ReviewTarget.Commit(
            commitHash = metadata.hash,
            parentHash = metadata.firstParentHash,
            subject = metadata.subject
        ),
        repositoryRoot = metadata.repositoryRoot,
        createdAt = nowIso(),
        updatedAt = nowIso()
    )

    reviewStateService.addReview(review)
    reviewUi.openAndSelect(review.id)
}
```

---

## 15. Comment Workflows

## 15.1 Add Comment

User flow:

```text
Select review → select file → select line or enter line → Add Comment
```

The plugin should create:

```kotlin
ReviewComment(
    id = UUID.randomUUID().toString(),
    reviewId = review.id,
    filePath = selectedFile.filePath,
    anchor = CommentAnchor(
        side = DiffSide.RIGHT,
        newLine = selectedLine,
        selectedText = selectedText,
        beforeContext = beforeContext,
        afterContext = afterContext,
        commitHash = review.target.commitHashIfAny()
    ),
    body = dialog.commentBody,
    severity = dialog.severity,
    status = CommentStatus.OPEN,
    createdAt = nowIso(),
    updatedAt = nowIso()
)
```

Then:

1. Add comment to review.
2. Persist state.
3. Refresh comments panel.
4. Optionally refresh diff markers.

## 15.2 Edit Comment

Allow editing:

```text
body
severity
status
```

Do not change UUID.

Update `updatedAt`.

## 15.3 Resolve Comment

Human action:

```text
Mark Resolved
```

Sets:

```text
status = RESOLVED
```

## 15.4 Mark Addressed

Agent or human action:

```text
Mark Addressed
```

Sets:

```text
status = ADDRESSED
agentMetadata.addressedAt = now
agentMetadata.message = optional message
```

## 15.5 Reopen Comment

Human action:

```text
Reopen
```

Sets:

```text
status = OPEN
```

---

## 16. Export Formats

## 16.1 JSON Export

Export one review as:

```json
{
  "schemaVersion": 1,
  "review": {
    "id": "uuid",
    "title": "Review abc123",
    "status": "OPEN",
    "target": {
      "type": "COMMIT",
      "commitHash": "abc123",
      "parentHash": "def456",
      "subject": "Fix user lookup"
    },
    "repositoryRoot": "/path/to/repo",
    "createdAt": "2026-05-07T14:20:00+03:00",
    "updatedAt": "2026-05-07T14:31:00+03:00"
  },
  "comments": [
    {
      "id": "uuid",
      "reviewId": "uuid",
      "filePath": "src/main/kotlin/Foo.kt",
      "side": "RIGHT",
      "newLine": 47,
      "oldLine": null,
      "status": "OPEN",
      "severity": "MUST_FIX",
      "body": "Avoid !! here. Return a typed error or handle null explicitly.",
      "anchor": {
        "selectedText": "repo.find(id)!!",
        "hunkHeader": "@@ -40,8 +44,10 @@",
        "beforeContext": [
          "fun findUser(id: UserId): User {"
        ],
        "afterContext": [
          "}"
        ],
        "commitHash": "abc123"
      }
    }
  ]
}
```

## 16.2 XML Export

Export one review as:

```xml
<reviewExport schemaVersion="1">
  <review id="uuid" status="OPEN">
    <title>Review abc123</title>
    <target type="COMMIT" commitHash="abc123" parentHash="def456">
      <subject>Fix user lookup</subject>
    </target>
    <repositoryRoot>/path/to/repo</repositoryRoot>
    <createdAt>2026-05-07T14:20:00+03:00</createdAt>
    <updatedAt>2026-05-07T14:31:00+03:00</updatedAt>
  </review>

  <comments>
    <comment id="uuid" reviewId="uuid" filePath="src/main/kotlin/Foo.kt" status="OPEN" severity="MUST_FIX">
      <anchor side="RIGHT" newLine="47" commitHash="abc123">
        <selectedText>repo.find(id)!!</selectedText>
        <hunkHeader>@@ -40,8 +44,10 @@</hunkHeader>
        <beforeContext>
          <line>fun findUser(id: UserId): User {</line>
        </beforeContext>
        <afterContext>
          <line>}</line>
        </afterContext>
      </anchor>
      <body>Avoid !! here. Return a typed error or handle null explicitly.</body>
    </comment>
  </comments>
</reviewExport>
```

## 16.3 Agent Prompt Export

Add an action:

```text
Copy Agent Prompt
```

Prompt template:

```text
You are fixing review comments from the IntelliJ Local Review plugin.

Review selection:
- Only address comments with status OPEN unless told otherwise.
- Preserve existing behavior unless a comment explicitly asks for behavior change.
- Add or update tests when appropriate.
- After implementing a comment, mark it ADDRESSED through MCP if MCP is available.
- Do not mark comments RESOLVED unless explicitly allowed.

Review JSON:
<insert exported review JSON here>
```

---

## 17. MCP Design

## 17.1 Preferred MCP Implementation

Prefer integration through IntelliJ's built-in MCP Server support.

Desired architecture:

```text
Local Review plugin
    └─ registers custom MCP tools
          └─ exposed through JetBrains built-in MCP Server
                └─ agent calls tools
```

The implementation agent should inspect JetBrains' MCP Server plugin README and source to find the extension point for custom tools.

If the extension point is available and stable enough, implement tools there.

## 17.2 Fallback MCP Implementation

If built-in MCP tool registration is not viable:

- Implement a standalone local MCP server using the official Kotlin MCP SDK.
- Bind to `127.0.0.1` only.
- Require explicit user enablement in plugin settings.
- Use a random local port or configurable port.
- Use an auth token if using Streamable HTTP.
- Never expose the server publicly.
- Stop the server when the project closes.

Recommended fallback transport:

```text
Streamable HTTP on localhost
```

Optional fallback:

```text
stdio is harder from inside an IDE plugin and should not be the first fallback
```

## 17.3 MCP Review Selectors

All MCP tools should support either:

```json
{
  "reviewId": "uuid"
}
```

or:

```json
{
  "selector": "latest-open"
}
```

Supported selectors:

```text
latest
latest-open
current
uncommitted
commit:<hash>
```

Resolution behavior:

1. Exact `reviewId` wins.
2. `current` means currently selected review in the Review tool window.
3. `commit:<hash>` finds review for commit.
4. `uncommitted` returns latest open uncommitted review.
5. `latest-open` returns most recently updated open review.
6. If ambiguous, return an error with candidate reviews.

## 17.4 MCP Tools

### Tool: `review_list_reviews`

Purpose:

```text
List known reviews.
```

Input:

```json
{
  "status": "OPEN"
}
```

Output:

```json
{
  "reviews": [
    {
      "id": "uuid",
      "title": "Uncommitted changes — 2026-05-07 14:20",
      "type": "UNCOMMITTED",
      "status": "OPEN",
      "openCommentCount": 3,
      "addressedCommentCount": 1,
      "resolvedCommentCount": 0,
      "repositoryRoot": "/path/to/repo",
      "updatedAt": "2026-05-07T14:31:00+03:00"
    }
  ]
}
```

### Tool: `review_get_review`

Purpose:

```text
Get a review and optionally include comments.
```

Input:

```json
{
  "reviewId": "uuid",
  "includeResolved": false,
  "includeComments": true
}
```

Output:

```json
{
  "review": {
    "id": "uuid",
    "title": "Review abc123",
    "target": {
      "type": "COMMIT",
      "commitHash": "abc123"
    },
    "comments": []
  }
}
```

### Tool: `review_list_unresolved_comments`

Purpose:

```text
List comments that an agent should work on.
```

Input:

```json
{
  "reviewId": "uuid"
}
```

Output:

```json
{
  "comments": [
    {
      "id": "uuid",
      "reviewId": "uuid",
      "filePath": "src/main/kotlin/Foo.kt",
      "line": 47,
      "side": "RIGHT",
      "body": "Avoid !! here.",
      "severity": "MUST_FIX",
      "status": "OPEN",
      "selectedText": "repo.find(id)!!"
    }
  ]
}
```

Include comments with status:

```text
OPEN
```

Do not include by default:

```text
ADDRESSED
RESOLVED
WONT_FIX
STALE
```

### Tool: `review_get_comment_context`

Purpose:

```text
Get full context for one comment.
```

Input:

```json
{
  "commentId": "uuid"
}
```

Output:

```json
{
  "comment": {
    "id": "uuid",
    "reviewId": "uuid",
    "filePath": "src/main/kotlin/Foo.kt",
    "body": "Avoid !! here.",
    "status": "OPEN",
    "severity": "MUST_FIX"
  },
  "anchor": {
    "side": "RIGHT",
    "newLine": 47,
    "oldLine": null,
    "selectedText": "repo.find(id)!!",
    "beforeContext": [],
    "afterContext": [],
    "hunkHeader": "@@ -40,8 +44,10 @@",
    "commitHash": "abc123"
  },
  "review": {
    "id": "uuid",
    "target": {
      "type": "COMMIT",
      "commitHash": "abc123"
    },
    "repositoryRoot": "/path/to/repo"
  }
}
```

### Tool: `review_mark_comment_addressed`

Purpose:

```text
Let an agent say it implemented a requested change.
```

Input:

```json
{
  "commentId": "uuid",
  "message": "Replaced !! with explicit null handling.",
  "agentName": "opencode",
  "runId": "optional-run-id"
}
```

Output:

```json
{
  "ok": true,
  "commentId": "uuid",
  "newStatus": "ADDRESSED"
}
```

### Tool: `review_mark_comment_resolved`

Purpose:

```text
Resolve a comment.
```

Default setting:

```text
Disabled for agents.
```

If disabled, return:

```json
{
  "ok": false,
  "error": "Agents are not allowed to mark comments resolved. Mark the comment ADDRESSED instead."
}
```

If enabled:

Input:

```json
{
  "commentId": "uuid"
}
```

Output:

```json
{
  "ok": true,
  "commentId": "uuid",
  "newStatus": "RESOLVED"
}
```

### Tool: `review_export`

Purpose:

```text
Export a review through MCP.
```

Input:

```json
{
  "reviewId": "uuid",
  "format": "json"
}
```

Output:

```json
{
  "format": "json",
  "content": "{...}"
}
```

---

## 18. Settings

Add plugin settings later, after core MVP.

Settings:

```text
MCP enabled: true/false
Allow agents to mark comments resolved: false by default
Export directory: optional path
Store shared review file in .agent-review/reviews.json: false by default
Default review mode: uncommitted
Default comment severity: MUST_FIX
```

---

## 19. Milestones

## Milestone 1 — Plugin Skeleton

Deliverables:

- Gradle/Kotlin IntelliJ plugin builds.
- Plugin runs with `./gradlew runIde`.
- `Review` tool window appears.
- Empty state renders.
- Basic toolbar actions exist but may be disabled.

Acceptance criteria:

```text
./gradlew build succeeds.
./gradlew runIde starts an IDE.
Review tool window is visible.
No startup exceptions.
```

---

## Milestone 2 — Uncommitted Review

Deliverables:

- Start review from uncommitted changes.
- Show review in review list.
- Show changed files.
- Select file and display diff using embedded IntelliJ diff viewer.
- Persist review metadata.

Acceptance criteria:

```text
Modify a file.
Click Review → New Review: Uncommitted Changes.
Changed file appears.
Selecting file shows diff.
Restart IDE.
Review still appears.
```

---

## Milestone 3 — Persistent Comments

Deliverables:

- Add comment to selected file.
- Store UUID per comment.
- Store status, severity, timestamps.
- Show comments in comments panel.
- Mark comment resolved.
- Reopen comment.
- Export JSON.

Acceptance criteria:

```text
Add a comment.
Restart IDE.
Comment still exists.
Comment UUID is stable.
Resolve comment.
Restart IDE.
Resolved status remains.
Export JSON includes review and comments.
```

---

## Milestone 4 — Committed Review

Deliverables:

- Start review from commit hash.
- Show changed files in commit.
- Show diff parent → commit.
- Store commit hash in review.
- Store commit hash in comment anchor.

Acceptance criteria:

```text
Enter a commit hash.
Review is created.
Changed files appear.
Diff is shown.
Add comment.
Export includes commitHash.
```

---

## Milestone 5 — Git Log Integration

Deliverables:

- Add action to Git Log context menu if possible.
- Start review from selected commit.
- If multiple commits selected, create one review per commit.

Acceptance criteria:

```text
Open Git Log.
Right-click a commit.
Start Review action is available.
Review opens in Review tool window.
```

If Git Log context integration is too costly, document it and keep the commit hash fallback.

---

## Milestone 6 — MCP Tools

Deliverables:

- MCP integration through JetBrains MCP Server if possible.
- Expose tools:
  - `review_list_reviews`
  - `review_get_review`
  - `review_list_unresolved_comments`
  - `review_get_comment_context`
  - `review_mark_comment_addressed`
  - `review_mark_comment_resolved`
  - `review_export`

Acceptance criteria:

```text
An MCP client can list reviews.
An MCP client can fetch unresolved comments.
An MCP client can mark a comment ADDRESSED.
Review UI reflects the updated status.
```

---

## Milestone 7 — Agent Workflow Polish

Deliverables:

- Copy Agent Prompt action.
- Export JSON action.
- Export XML action.
- Review selectors:
  - latest
  - latest-open
  - current
  - uncommitted
  - commit:<hash>
- Stale anchor detection.

Acceptance criteria:

```text
User creates review.
User adds comments.
User asks agent to read comments through MCP or exported JSON.
Agent implements changes.
Agent marks comments ADDRESSED.
User verifies and marks comments RESOLVED.
```

---

## 20. Testing Plan

### 20.1 Manual Test Matrix

Test on a small Git project.

Cases:

```text
Modified file
Added file
Deleted file
Renamed file
Uncommitted review
Single commit review
Merge commit review
Comment persistence after restart
JSON export
XML export
MCP list reviews
MCP list unresolved comments
MCP mark addressed
```

### 20.2 Unit Tests

Add tests for:

```text
Review state model
Comment status transitions
Review selector resolution
JSON export
XML export
Stale anchor detection
```

### 20.3 Integration Tests

If feasible:

```text
Create temporary Git repo.
Make commit.
Modify file.
Create review.
Load changed files.
Verify diff content.
```

---

## 21. Non-Goals for v1

Do not implement these in v1:

```text
Cloud sync
GitHub/GitLab PR sync
A full custom diff viewer
Code review approvals
CI integration
Multi-user collaboration
Complex threaded comments
Code suggestions as patches
```

These can be v2+ features.

---

## 22. Recommended v2 Features

After v1:

1. **Patch snapshots for uncommitted reviews**
   - Preserve exact diff state at review creation time.

2. **Inline visual comment markers**
   - Gutter icons.
   - Inlay comment blocks.
   - Click marker to open comment.

3. **Threaded comments**
   - Human-agent back-and-forth per comment.

4. **Agent run history**
   - Track which agent addressed which comment and when.

5. **Review categories**
   - bug
   - readability
   - security
   - performance
   - tests
   - architecture

6. **Batch actions**
   - Mark all addressed as resolved.
   - Reopen all stale.
   - Export only open comments.

7. **Import/export with GitHub or GitLab**
   - Import PR comments.
   - Export local comments as PR review comments.

8. **Agent-specific prompt templates**
   - opencode
   - Claude Code
   - Codex
   - JetBrains AI Assistant

---

## 23. Implementation Cautions

### 23.1 Do Not Build a Diff Engine

Use IntelliJ's diff APIs. The plugin is a review/comment layer, not a diff renderer.

### 23.2 Avoid Commit Tool Window Coupling

The plugin should not depend on the Commit window implementation.

### 23.3 Prefer Public APIs

Avoid internal IntelliJ classes unless absolutely necessary.

If an internal API is required:

- isolate it behind a small adapter,
- document the IDE versions tested,
- expect it to break.

### 23.4 Keep Agent Permissions Conservative

Default:

```text
Agents can read reviews.
Agents can read comments.
Agents can mark comments ADDRESSED.
Agents cannot mark comments RESOLVED.
```

### 23.5 Protect MCP Access

If using a standalone MCP server:

```text
Bind only to 127.0.0.1.
Require explicit user enablement.
Use an auth token if HTTP transport is used.
Do not expose project data over the network.
```

---

## 24. Definition of Done for v1

The plugin is v1-complete when:

```text
Review tool window exists.
User can review uncommitted changes.
User can review a commit by commit hash.
User can add persistent comments.
Each review has a UUID.
Each comment has a UUID.
Comments have OPEN, ADDRESSED, RESOLVED, WONT_FIX, and STALE statuses.
JSON export works.
XML export works.
MCP can list reviews.
MCP can list unresolved comments.
MCP can mark comments ADDRESSED.
User can manually resolve comments.
Plugin survives IDE restart with reviews/comments intact.
```

---

## 25. Suggested First Implementation Order

Use this exact order:

```text
1. Bootstrap plugin project.
2. Add Review tool window.
3. Add persistent state service.
4. Add review/comment data models.
5. Add New Review: Uncommitted Changes action.
6. List changed files.
7. Embed IntelliJ diff viewer.
8. Add manual line-based comments.
9. Persist comments.
10. Add JSON export.
11. Add committed review by commit hash.
12. Add XML export.
13. Add MCP read tools.
14. Add MCP mark addressed tool.
15. Add Git Log context action.
16. Improve line anchoring.
17. Add stale detection.
18. Add UI polish.
```

This order avoids getting blocked by difficult Git Log or exact diff caret APIs before the core workflow is proven.

---

## 26. Example Agent Task Prompt

Use this prompt when giving the plan to an implementation agent:

```text
Implement the IntelliJ Local Review plugin according to this markdown file.

Start with Milestone 1 and Milestone 2 only:
- bootstrap the Kotlin IntelliJ plugin project,
- create the Review tool window,
- create persistent Review/ReviewComment models,
- add "Start Review of Uncommitted Changes",
- list changed files,
- show selected file diff using IntelliJ DiffManager.

Do not implement MCP yet.
Do not implement Git Log integration yet.
Use TODOs and small interfaces where future milestones will connect.
Keep the implementation incremental and runnable after each milestone.
```

---

## 27. Summary

The plugin should be a thin, reliable layer on top of IntelliJ's existing capabilities:

```text
IntelliJ Git/VCS APIs
        ↓
Changed files and revisions
        ↓
IntelliJ DiffManager embedded in Review tool window
        ↓
Persistent local PR-style comments
        ↓
JSON/XML export and MCP tools
        ↓
Agent reads comments, implements fixes, marks addressed
```

The safest MVP is:

```text
Review uncommitted changes
+ embedded diff viewer
+ persistent line comments
+ JSON export
```

Then extend to:

```text
committed review
+ MCP access
+ Git Log integration
+ better inline annotations
```
