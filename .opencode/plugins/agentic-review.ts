import { Client } from "@modelcontextprotocol/sdk/client/index.js"
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js"

const MCP_URL = "http://127.0.0.1:64342/stream"
const MCP_RETRY_MS = 2000
const MCP_RETRY_MAX = 2
const MCP_FAILURE_COOLDOWN_MS = 30000
const RELEVANT_TOOLS = new Set(["edit", "write", "apply_patch", "bash"])

interface SessionState {
  sessionId: string
  mode: "legacy" | "v2"
  activeTurn: TurnState | null
  projectPath: string
}

interface TurnState {
  stepId: string
  agent: string | undefined
  model: string | undefined
  changedPaths: Set<string>
  toolCalls: ToolCallMeta[]
}

interface ToolCallMeta {
  callId: string
  tool: string
  changedPaths: string[]
  metadataJson: string | null
}

interface PluginContext {
  directory: string
  worktree?: string
}

interface PluginEventEnvelope {
  event: {
    type: string
    properties: Record<string, unknown>
  }
}

interface ToolExecuteAfterInput {
  tool: string
  sessionID: string
  callID: string
  args: Record<string, unknown>
}

function stepId(): string {
  return `turn-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
}

function textContent(content: unknown): string[] {
  if (!Array.isArray(content)) return []
  return content
    .filter((item): item is { type?: string; text?: string } => !!item && typeof item === "object")
    .filter((item) => item.type === "text" && typeof item.text === "string")
    .map((item) => item.text as string)
}

async function withRetry<T>(fn: () => Promise<T>, label: string): Promise<T> {
  let lastError: unknown
  for (let i = 0; i < MCP_RETRY_MAX; i++) {
    try {
      return await fn()
    } catch (err) {
      lastError = err
      if (i === MCP_RETRY_MAX - 1) throw err
      await new Promise((r) => setTimeout(r, MCP_RETRY_MS))
    }
  }
  throw lastError ?? new Error(`Failed: ${label}`)
}

class TurnSnapshotClient {
  private disabledUntil = 0

  private shouldSkip(): boolean {
    return Date.now() < this.disabledUntil
  }

  private backoff(): void {
    this.disabledUntil = Date.now() + MCP_FAILURE_COOLDOWN_MS
  }

  async callTool(name: string, args: Record<string, unknown>): Promise<Record<string, unknown>> {
    if (this.shouldSkip()) {
      throw new Error("MCP temporarily unavailable")
    }

    return await withRetry(async () => {
      const transport = new StreamableHTTPClientTransport(new URL(MCP_URL))
      const client = new Client(
        { name: "agentic-review", version: "0.0.1" },
        { capabilities: {} },
      )

      try {
        await client.connect(transport)
        const result = await client.callTool({ name, arguments: args })
        const texts = textContent(result.content)

        if (result.isError) {
          const errText = texts[0] ?? "unknown error"
          throw new Error(`MCP tool ${name} returned error: ${errText}`)
        }

        const text = texts[0] ?? "{}"
        return JSON.parse(text)
      } finally {
        try {
          await client.close()
        } catch {}
      }
    }, `MCP tool ${name}`).catch((err) => {
      this.backoff()
      throw err
    })
  }
}

export const AgenticReviewPlugin = async ({
  directory,
  worktree,
}: PluginContext) => {
  const client = new TurnSnapshotClient()

  const sessions = new Map<string, SessionState>()

  function ensureSession(sessionId: string): SessionState {
    let st = sessions.get(sessionId)
    if (!st) {
      st = { sessionId, mode: "legacy", activeTurn: null, projectPath: worktree || directory }
      sessions.set(sessionId, st)
    }
    return st
  }

  async function beginTurn(st: SessionState, agent?: string, model?: string) {
    const sId = stepId()

    st.activeTurn = {
      stepId: sId,
      agent,
      model,
      changedPaths: new Set(),
      toolCalls: [],
    }

    try {
      await client.callTool("review_turn_snapshot_begin", {
        sessionId: st.sessionId,
        stepId: sId,
        projectPath: st.projectPath,
        agent: agent ?? null,
        model: model ?? null,
      })
    } catch {
    }
  }

  async function endTurn(
    st: SessionState,
    status: string,
    changedPaths?: string[],
  ) {
    const turn = st.activeTurn
    if (!turn) return

    const paths = changedPaths ?? [...turn.changedPaths]

    st.activeTurn = null

    try {
      await client.callTool("review_turn_snapshot_end", {
        sessionId: st.sessionId,
        stepId: turn.stepId,
        status,
        projectPath: st.projectPath,
        changedPathsJson: JSON.stringify(paths),
        toolCallsJson: JSON.stringify(turn.toolCalls),
      })
    } catch {
    }
  }

  return {
    event: async ({ event }: PluginEventEnvelope) => {
      const { type, properties } = event

      if (type === "session.next.step.started") {
        const sessionId = properties.sessionID as string
        const st = ensureSession(sessionId)
        st.mode = "v2"

        const agent = properties.agent as string | undefined
        const modelInfo = properties.model as { id?: string; providerID?: string } | undefined
        const model = modelInfo?.id ?? modelInfo?.providerID

        await beginTurn(st, agent, model)
        return
      }

      if (type === "session.next.step.ended") {
        const sessionId = properties.sessionID as string
        const st = ensureSession(sessionId)
        st.mode = "v2"

        await endTurn(st, "completed")
        return
      }

      if (type === "session.next.step.failed") {
        const sessionId = properties.sessionID as string
        const st = ensureSession(sessionId)
        st.mode = "v2"

        await endTurn(st, "failed")
        return
      }

      if (type === "message.part.updated") {
        const part = properties.part as {
          sessionID: string
          type: string
          files?: string[]
        }
        const sessionId = part.sessionID
        const st = ensureSession(sessionId)

        if (st.mode === "v2") return

        if (part.type === "step-start") {
          if (!st.activeTurn) {
            await beginTurn(st, undefined, undefined)
          }
          return
        }

        if (part.type === "step-finish") {
          return
        }

        if (part.type === "patch" && part.files && st.activeTurn) {
          for (const f of part.files) {
            st.activeTurn.changedPaths.add(f)
          }
          return
        }
      }

      if (type === "session.idle") {
        const sessionId = properties.sessionID as string
        const st = sessions.get(sessionId)
        if (!st || st.mode === "v2") return
        if (st.activeTurn) {
          const paths = [...st.activeTurn.changedPaths]
          await endTurn(st, "completed", paths)
        }
      }
    },

    "tool.execute.after": async (input: ToolExecuteAfterInput) => {
      const { tool, sessionID, callID, args } = input

      if (!RELEVANT_TOOLS.has(tool)) return

      const st = sessions.get(sessionID)
      if (!st?.activeTurn) return

      const paths: string[] = []

      if (typeof args.filePath === "string") paths.push(args.filePath)
      if (typeof args.file_path === "string") paths.push(args.file_path)
      if (typeof args.newPath === "string") paths.push(args.newPath)
      if (typeof args.oldPath === "string") paths.push(args.oldPath)

      for (const p of paths) {
        st.activeTurn.changedPaths.add(p)
      }

      st.activeTurn.toolCalls.push({
        callId: callID,
        tool,
        changedPaths: paths,
        metadataJson: null,
      })
    },
  }
}
