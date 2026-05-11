import { Client } from "@modelcontextprotocol/sdk/client/index.js"
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js"

const MCP_URL = "http://127.0.0.1:64342/stream"
const MCP_RETRY_MS = 2000
const MCP_RETRY_MAX = 15
const LEGACY_FLUSH_MS = 500
const RELEVANT_TOOLS = new Set(["edit", "write", "apply_patch", "bash"])

interface SessionState {
  sessionId: string
  mode: "legacy" | "v2"
  activeTurn: TurnState | null
  pendingFinishes: number
}

interface TurnState {
  stepId: string
  agent: string | undefined
  model: string | undefined
  changedPaths: Set<string>
  toolCalls: ToolCallMeta[]
  flushTimer: ReturnType<typeof setTimeout> | null
}

interface ToolCallMeta {
  callId: string
  tool: string
  changedPaths: string[]
  metadataJson: string | null
}

function stepId(): string {
  return `turn-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
}

async function withRetry<T>(fn: () => Promise<T>, label: string): Promise<T> {
  for (let i = 0; i < MCP_RETRY_MAX; i++) {
    try {
      return await fn()
    } catch (err) {
      if (i === MCP_RETRY_MAX - 1) throw err
      console.warn(`[agentic-review] ${label} failed, retrying in ${MCP_RETRY_MS}ms:`, err)
      await new Promise((r) => setTimeout(r, MCP_RETRY_MS))
    }
  }
  throw new Error("unreachable")
}

class TurnSnapshotClient {
  private client: Client | null = null
  private connected = false

  async connect(): Promise<boolean> {
    try {
      const transport = new StreamableHTTPClientTransport(new URL(MCP_URL))
      this.client = new Client(
        { name: "agentic-review", version: "0.0.1" },
        { capabilities: {} },
      )
      await this.client.connect(transport)
      this.connected = true
      return true
    } catch (err) {
      console.warn(
        "[agentic-review] MCP server not available at",
        MCP_URL,
        "- turn snapshots disabled",
      )
      return false
    }
  }

  isConnected(): boolean {
    return this.connected
  }

  async callTool(name: string, args: Record<string, unknown>): Promise<Record<string, unknown>> {
    if (!this.connected || !this.client) {
      throw new Error("MCP client not connected")
    }

    const result = await this.client.callTool({ name, arguments: args })

    if (result.isError) {
      const errText = result.content.find((c) => c.type === "text")?.text ?? "unknown error"
      throw new Error(`MCP tool ${name} returned error: ${errText}`)
    }

    const text = result.content.find((c) => c.type === "text")?.text ?? "{}"
    return JSON.parse(text)
  }

  async close(): Promise<void> {
    if (this.client) {
      try {
        await this.client.close()
      } catch {}
    }
    this.connected = false
  }
}

export const AgenticReviewPlugin = async ({
  directory,
  worktree,
}) => {
  const client = new TurnSnapshotClient()
  const connected = await withRetry(() => client.connect(), "MCP connect")

  if (!connected) {
    return {
      event: undefined,
      "tool.execute.after": undefined,
    }
  }

  const sessions = new Map<string, SessionState>()

  function ensureSession(sessionId: string): SessionState {
    let st = sessions.get(sessionId)
    if (!st) {
      st = { sessionId, mode: "legacy", activeTurn: null, pendingFinishes: 0 }
      sessions.set(sessionId, st)
    }
    return st
  }

  async function beginTurn(st: SessionState, agent?: string, model?: string) {
    const sId = stepId()
    const projectPath = worktree || directory

    st.activeTurn = {
      stepId: sId,
      agent,
      model,
      changedPaths: new Set(),
      toolCalls: [],
      flushTimer: null,
    }

    try {
      await client.callTool("review_turn_snapshot_begin", {
        sessionId: st.sessionId,
        stepId: sId,
        projectPath,
        agent: agent ?? null,
        model: model ?? null,
      })
    } catch (err) {
      console.warn("[agentic-review] beginTurn failed:", err)
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

    if (turn.flushTimer) {
      clearTimeout(turn.flushTimer)
      turn.flushTimer = null
    }

    st.activeTurn = null

    try {
      await client.callTool("review_turn_snapshot_end", {
        sessionId: st.sessionId,
        stepId: turn.stepId,
        status,
        changedPathsJson: JSON.stringify(paths),
        toolCallsJson: JSON.stringify(turn.toolCalls),
      })
    } catch (err) {
      console.warn("[agentic-review] endTurn failed:", err)
    }
  }

  return {
    event: async ({ event }) => {
      const { type, properties } = event as {
        type: string
        properties: Record<string, unknown>
      }

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
          st.pendingFinishes = 0
          await beginTurn(st, undefined, undefined)
          return
        }

        if (part.type === "step-finish") {
          st.pendingFinishes++
          return
        }

        if (part.type === "patch" && part.files && st.activeTurn) {
          for (const f of part.files) {
            st.activeTurn.changedPaths.add(f)
          }

          if (st.pendingFinishes > 0) {
            st.pendingFinishes--
            const turn = st.activeTurn!
            const paths = [...turn.changedPaths]

            if (turn.flushTimer) {
              clearTimeout(turn.flushTimer)
            }

            if (st.pendingFinishes === 0) {
              await endTurn(st, "completed", paths)
            } else {
              turn.flushTimer = setTimeout(async () => {
                const currentTurn = st.activeTurn
                if (currentTurn && currentTurn === turn) {
                  await endTurn(st, "completed", paths)
                }
              }, LEGACY_FLUSH_MS)
            }
          }
          return
        }
      }

      if (type === "session.idle") {
        const sessionId = properties.sessionID as string
        const st = sessions.get(sessionId)
        if (!st || st.mode === "v2") return

        const turn = st.activeTurn
        if (turn && st.pendingFinishes > 0) {
          st.pendingFinishes = 0
          const paths = [...turn.changedPaths]

          if (turn.flushTimer) {
            clearTimeout(turn.flushTimer)
            turn.flushTimer = null
          }

          await endTurn(st, "completed", paths)
        }
      }
    },

    "tool.execute.after": async (input) => {
      const { tool, sessionID, callID, args } = input as {
        tool: string
        sessionID: string
        callID: string
        args: Record<string, unknown>
      }

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
