package com.dsh.mobile

import com.dsh.mobile.data.ChatRow
import com.dsh.mobile.data.Role
import com.dsh.mobile.data.SessionSummary
import com.dsh.mobile.data.Wire
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WireTest {

    // ------------------------------------------------------------- scanning

    @Test
    fun parsesPairingUrl() {
        val payload = Wire.parsePairPayload("https://m.zhuquan.xyz/mobile/#pair=ABCD-EFGH")
        assertEquals("ABCDEFGH", payload?.code)
        assertEquals("https://m.zhuquan.xyz", payload?.base)
    }

    @Test
    fun parsesLanPairingUrlWithPort() {
        val payload = Wire.parsePairPayload("http://192.168.1.9:17731/mobile/#pair=abcd")
        assertEquals("ABCD", payload?.code)
        assertEquals("http://192.168.1.9:17731", payload?.base)
    }

    // 中继形态：路径里的 /m/<设备密钥> 必须保住 —— 丢掉它配对请求就会
    // 打到服务器根上（2026-09-25 真机用户扫码连不上的根因）。
    @Test
    fun parsesRelayPairingUrlKeepingDevicePath() {
        val payload = Wire.parsePairPayload("https://cn.zhuquan.xyz:8443/m/ab12cd34ef/mobile/?pair=ABCD-EFGH")
        assertEquals("ABCDEFGH", payload?.code)
        assertEquals("https://cn.zhuquan.xyz:8443/m/ab12cd34ef", payload?.base)
        assertEquals("https://cn.zhuquan.xyz:8443/m/ab12cd34ef/mobile/?pair=ABCD-EFGH", payload?.raw)
    }

    @Test
    fun parsesCloudflareRelayUrlKeepingDevicePath() {
        val payload = Wire.parsePairPayload("https://relay.zhuquan.xyz/m/ab12cd34ef/mobile/#pair=abcd")
        assertEquals("ABCD", payload?.code)
        assertEquals("https://relay.zhuquan.xyz/m/ab12cd34ef", payload?.base)
    }

    @Test
    fun alternateRelayBaseSwapsLinesOnlyForRelayHosts() {
        assertEquals(
            "https://relay.zhuquan.xyz/m/ab12cd34ef",
            Wire.alternateRelayBase("https://cn.zhuquan.xyz:8443/m/ab12cd34ef"),
        )
        assertEquals(
            "https://cn.zhuquan.xyz:8443/m/ab12cd34ef",
            Wire.alternateRelayBase("https://relay.zhuquan.xyz/m/ab12cd34ef"),
        )
        assertNull(Wire.alternateRelayBase("http://10.0.2.2:17731"))
        assertNull(Wire.alternateRelayBase("https://m.zhuquan.xyz"))
    }

    @Test
    fun parsesBareCode() {
        val payload = Wire.parsePairPayload(" abcd-EFGH ")
        assertEquals("ABCDEFGH", payload?.code)
        assertNull(payload?.base)
    }

    @Test
    fun rejectsGarbage() {
        assertNull(Wire.parsePairPayload("hello world"))
        assertNull(Wire.parsePairPayload(""))
    }

    // ------------------------------------------------------- model document

    @Test
    fun parsesProviderNetworkRoute() {
        val doc = JSONObject()
            .put(
                "providers",
                JSONArray()
                    .put(JSONObject().put("id", "xai").put("network", "proxy"))
                    .put(JSONObject().put("id", "wawa").put("network", "direct"))
                    .put(JSONObject().put("id", "plain"))
                    .put(JSONObject().put("id", "weird").put("network", "bogus")),
            )
            .put("items", JSONArray())
        val parsed = Wire.parseModelDoc(doc)
        assertEquals("proxy", parsed.providers.first { it.id == "xai" }.network)
        assertEquals("direct", parsed.providers.first { it.id == "wawa" }.network)
        assertEquals("", parsed.providers.first { it.id == "plain" }.network)
        assertEquals("", parsed.providers.first { it.id == "weird" }.network)
    }

    // --------------------------------------------------------------- chunks

    @Test
    fun chunkTextShapes() {
        assertEquals("hi", Wire.chunkText(JSONObject().put("chunk", "hi")))
        assertEquals("y", Wire.chunkText(JSONObject().put("chunk", JSONObject().put("text", "y"))))
        assertEquals("z", Wire.chunkText(JSONObject().put("chunk", JSONObject().put("delta", "z"))))
        assertEquals(
            "w",
            Wire.chunkText(JSONObject().put("chunk", JSONObject().put("delta", JSONObject().put("text", "w")))),
        )
        assertEquals("", Wire.chunkText(JSONObject()))
    }

    // ----------------------------------------------------------- extraction

    @Test
    fun extractTextShapes() {
        assertEquals("s", Wire.extractText("s"))
        assertEquals("m", Wire.extractText(JSONObject().put("message", JSONObject().put("text", "m"))))
        val content = JSONObject().put(
            "content",
            JSONArray().put(JSONObject().put("type", "text").put("text", "a")).put("b"),
        )
        assertEquals("a\nb", Wire.extractText(content))
    }

    // -------------------------------------------------------------- history

    @Test
    fun historyRowsAndRunningFlag() {
        val items = JSONArray()
            .put(
                JSONObject().put(
                    "event",
                    JSONObject().put("type", "user/message")
                        .put("data", JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "你好")))),
                ),
            )
            .put(JSONObject().put("event", JSONObject().put("type", "turn/start").put("data", JSONObject())))
        val page = JSONObject().put("items", items)
        val parsed = Wire.parseHistory(page)
        assertEquals(1, parsed.rows.size)
        assertEquals(Role.USER, parsed.rows[0].who)
        assertEquals("你好", parsed.rows[0].text)
        assertTrue(parsed.running)
    }

    @Test
    fun injectedContextIsNotRenderedAsUserText() {
        val items = JSONArray()
            .put(
                JSONObject().put(
                    "event",
                    JSONObject().put("type", "user/message")
                        .put("data", JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "Current runtime context. This snapshot supersedes earlier ones.")))),
                ),
            )
            .put(
                JSONObject().put(
                    "event",
                    JSONObject().put("type", "user/message")
                        .put("data", JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "<system-reminder>\nA skill is a reusable set…")))),
                ),
            )
            .put(
                JSONObject().put(
                    "event",
                    JSONObject().put("type", "user/message")
                        .put("data", JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "真正的用户消息")))),
                ),
            )
        val parsed = Wire.parseHistory(JSONObject().put("items", items))
        assertEquals(1, parsed.rows.size)
        assertEquals("真正的用户消息", parsed.rows[0].text)
    }

    // ---------------------------------------------------------- session files

    @Test
    fun parsesSessionFiles() {
        val items = JSONArray()
            .put(
                JSONObject().put("path", "pelican-bike-3d.html").put("name", "pelican-bike-3d.html")
                    .put("size", 24576L).put("mtime", 1758634231000L),
            )
            .put(JSONObject().put("path", "shot.png").put("size", 1024L))
        val parsed = Wire.parseSessionFiles(JSONObject().put("items", items))
        assertEquals(2, parsed.size)
        assertEquals("pelican-bike-3d.html", parsed[0].name)
        assertEquals(24576L, parsed[0].size)
        assertEquals("shot.png", parsed[1].name)
        assertEquals("shot.png", parsed[1].path)
    }

    @Test
    fun parsesEmptySessionFiles() {
        assertTrue(Wire.parseSessionFiles(JSONObject()).isEmpty())
        assertTrue(Wire.parseSessionFiles(JSONObject().put("items", JSONArray())).isEmpty())
    }

    @Test
    fun fileKindByName() {
        assertEquals("image", Wire.fileKind("a.PNG"))
        assertEquals("svg", Wire.fileKind("pelican.svg"))
        assertEquals("html", Wire.fileKind("index.html"))
        assertEquals("text", Wire.fileKind("notes.md"))
        assertEquals("other", Wire.fileKind("deck.pptx"))
        assertEquals("other", Wire.fileKind("noext"))
    }

    @Test
    fun formatsSizes() {
        assertEquals("0 B", Wire.formatSize(0))
        assertEquals("512 B", Wire.formatSize(512))
        assertEquals("1.5 KB", Wire.formatSize(1536))
        assertEquals("2.0 MB", Wire.formatSize(2L * 1024 * 1024))
    }

    // ---------------------------------------------------------- inbox (排队/插话)

    private fun inboxEvent(target: String, removed: Int, vararg texts: String): JSONObject {
        val inserted = JSONArray()
        texts.forEachIndexed { i, t ->
            inserted.put(
                JSONObject().put("id", "m$i")
                    .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", t))),
            )
        }
        return JSONObject().put(
            "event",
            JSONObject().put("type", "agent/inbox/spliced")
                .put(
                    "data",
                    JSONObject().put("target", target).put("start", 0)
                        .put("removedCount", removed).put("inserted", inserted),
                ),
        )
    }

    @Test
    fun queuedMessageShowsAsPendingRow() {
        val items = JSONArray()
            .put(JSONObject().put("event", JSONObject().put("type", "turn/start").put("data", JSONObject())))
            .put(inboxEvent("next-turn", 0, "跑完补个结论"))
        val parsed = Wire.parseHistory(JSONObject().put("items", items))
        assertEquals(Role.QUEUED, parsed.rows.last().who)
        assertEquals("跑完补个结论", parsed.rows.last().text)
        assertTrue(parsed.running)
    }

    @Test
    fun steeredMessageShowsAsPendingRow() {
        val items = JSONArray()
            .put(JSONObject().put("event", JSONObject().put("type", "turn/start").put("data", JSONObject())))
            .put(inboxEvent("next-step", 0, "直接输出"))
        val parsed = Wire.parseHistory(JSONObject().put("items", items))
        assertEquals(Role.STEER, parsed.rows.last().who)
        assertEquals("直接输出", parsed.rows.last().text)
    }

    @Test
    fun claimedInboxMessageDisappearsFromPending() {
        // 引擎接管时先发 removedCount=1 的 spliced，再把它作为 user/message 写进回合
        val items = JSONArray()
            .put(JSONObject().put("event", JSONObject().put("type", "turn/start").put("data", JSONObject())))
            .put(inboxEvent("next-step", 0, "直接输出"))
            .put(inboxEvent("next-step", 1))
            .put(
                JSONObject().put(
                    "event",
                    JSONObject().put("type", "user/message")
                        .put(
                            "data",
                            JSONObject().put("id", "m0")
                                .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "直接输出"))),
                        ),
                ),
            )
        val parsed = Wire.parseHistory(JSONObject().put("items", items))
        assertFalse(parsed.rows.any { it.who == Role.STEER })
        assertEquals(Role.USER, parsed.rows.last().who)
        assertEquals("直接输出", parsed.rows.last().text)
    }

    @Test
    fun historyFinishedTurnIsNotRunning() {
        val items = JSONArray()
            .put(JSONObject().put("event", JSONObject().put("type", "turn/start").put("data", JSONObject())))
            .put(
                JSONObject().put(
                    "event",
                    JSONObject().put("type", "assistant/message")
                        .put("data", JSONObject().put("message", JSONObject().put("text", "收到"))),
                ),
            )
            .put(JSONObject().put("event", JSONObject().put("type", "turn/end").put("data", JSONObject())))
        val parsed = Wire.parseHistory(JSONObject().put("items", items))
        assertEquals(1, parsed.rows.size)
        assertEquals(Role.ASSISTANT, parsed.rows[0].who)
        assertFalse(parsed.running)
    }

    @Test
    fun generatedImageNoteBecomesPreviewRow() {
        // 引擎的图片生成注记（真实形状）：正文提到 dsh-img-*.png → 追加一张图片卡行，
        // 手机端据此从电脑拉图预览/下载。
        val note = "🖼️ 模型调用内置「图片生成」工具生成了 1 张图片：\n" +
            "· 图 1（1312x1199）已保存到 C:\\Users\\zhuquan\\AppData\\Roaming\\DeepSeek\\dsh-home\\generated\\dsh-img-1790320587465-xkkyab.png\n" +
            "在手机 App 里可以直接预览或保存到手机。"
        val items = JSONArray()
            .put(
                JSONObject().put(
                    "event",
                    JSONObject().put("type", "assistant/message")
                        .put("data", JSONObject().put("message", JSONObject().put("text", note))),
                ),
            )
        val parsed = Wire.parseHistory(JSONObject().put("items", items))
        assertEquals(Role.ASSISTANT, parsed.rows[0].who)
        assertEquals(Role.GENERATED_IMAGE, parsed.rows[1].who)
        assertEquals("dsh-img-1790320587465-xkkyab.png", parsed.rows[1].text)
    }

    @Test
    fun generatedImageNamesCollectsUniqueProductNames() {
        assertTrue(Wire.generatedImageNames("这句话里没有图片").isEmpty())
        assertEquals(
            listOf("dsh-img-1790320587465-xkkyab.png", "dsh-img-99-b2.jpg"),
            Wire.generatedImageNames(
                "见 dsh-img-1790320587465-xkkyab.png（重复一次 dsh-img-1790320587465-xkkyab.png）与 dsh-img-99-b2.jpg",
            ),
        )
    }

    @Test
    fun historyRunningTurnExposesItsStartTime() {
        // 重进正在跑的会话：秒数要用 turn/start 的真实时间续，不能从 0 重数。
        val items = JSONArray()
            .put(
                JSONObject().put(
                    "event",
                    JSONObject().put("type", "turn/start").put("data", JSONObject())
                        .put("time", 1_790_000_100L),
                ),
            )
            .put(
                JSONObject().put(
                    "event",
                    JSONObject().put("type", "assistant/message").put("time", 1_790_000_140L)
                        .put("data", JSONObject().put("message", JSONObject().put("text", "…"))),
                ),
            )
        val parsed = Wire.parseHistory(JSONObject().put("items", items))
        assertTrue(parsed.running)
        assertEquals(1_790_000_100_000L, parsed.runningSince)
    }

    // -------------------------------------------------------------- sessions

    @Test
    fun reasoningIsFoldedOutOfTheAnswer() {
        val content = JSONArray()
            .put(JSONObject().put("type", "reasoning").put("text", "先想一下：1+1 需要进位吗…"))
            .put(JSONObject().put("type", "text").put("text", "等于 2。"))
        val items = JSONArray()
            .put(JSONObject().put("event", JSONObject().put("type", "step/start").put("data", JSONObject()).put("time", 1_790_000_000L)))
            .put(
                JSONObject().put(
                    "event",
                    JSONObject().put("type", "assistant/message")
                        .put("time", 1_790_000_012L)
                        .put("data", JSONObject().put("message", JSONObject().put("content", content))),
                ),
            )
        val parsed = Wire.parseHistory(JSONObject().put("items", items))
        assertEquals(2, parsed.rows.size)
        assertEquals(Role.REASONING, parsed.rows[0].who)
        assertEquals("先想一下：1+1 需要进位吗…", parsed.rows[0].text)
        assertEquals("思考 12 秒", parsed.rows[0].detail)
        assertEquals(Role.ASSISTANT, parsed.rows[1].who)
        assertEquals("等于 2。", parsed.rows[1].text)
    }

    @Test
    fun emptyAssistantReplyBecomesVisibleRow() {
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", ""))
        val items = JSONArray()
            .put(
                JSONObject().put(
                    "event",
                    JSONObject().put("type", "assistant/message")
                        .put("data", JSONObject().put("message", JSONObject().put("content", content))),
                ),
            )
        val parsed = Wire.parseHistory(JSONObject().put("items", items))
        assertEquals(1, parsed.rows.size)
        assertEquals(Role.EMPTY_REPLY, parsed.rows[0].who)
    }

    @Test
    fun emptyTextWithReasoningKeepsOnlyReasoningRow() {
        val content = JSONArray()
            .put(JSONObject().put("type", "reasoning").put("text", "想了很久"))
            .put(JSONObject().put("type", "text").put("text", ""))
        val items = JSONArray()
            .put(
                JSONObject().put(
                    "event",
                    JSONObject().put("type", "assistant/message")
                        .put("data", JSONObject().put("message", JSONObject().put("content", content))),
                ),
            )
        val parsed = Wire.parseHistory(JSONObject().put("items", items))
        assertEquals(1, parsed.rows.size)
        assertEquals(Role.REASONING, parsed.rows[0].who)
    }

    @Test
    fun emptyTextWithToolCallIsNotFlagged() {
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", ""))
            .put(JSONObject().put("type", "tool-call").put("id", "call_9").put("name", "pwsh").put("arguments", "{}"))
        val items = JSONArray()
            .put(
                JSONObject().put(
                    "event",
                    JSONObject().put("type", "assistant/message")
                        .put("data", JSONObject().put("message", JSONObject().put("content", content))),
                ),
            )
        val parsed = Wire.parseHistory(JSONObject().put("items", items))
        assertEquals(1, parsed.rows.size)
        assertEquals(Role.TOOL, parsed.rows[0].who)
    }

    @Test
    fun approvalEventsBecomeRows() {
        val items = JSONArray()
            .put(
                JSONObject().put(
                    "event",
                    JSONObject().put("type", "approval/asked")
                        .put("data", JSONObject().put("id", "ap_1").put("toolName", "pwsh")),
                ),
            )
            .put(
                JSONObject().put(
                    "event",
                    JSONObject().put("type", "approval/decided")
                        .put("data", JSONObject().put("id", "ap_1").put("outcome", "allowed-once")),
                ),
            )
        val parsed = Wire.parseHistory(JSONObject().put("items", items))
        assertEquals(2, parsed.rows.size)
        assertEquals(Role.APPROVAL, parsed.rows[0].who)
        assertEquals("等待你在电脑上审批 · 执行命令需要审批", parsed.rows[0].text)
        assertEquals(Role.NOTICE, parsed.rows[1].who)
        assertEquals("审批已处理：已批准（仅本次）", parsed.rows[1].text)
    }

    @Test
    fun parsesApprovalsPayload() {
        val doc = JSONObject()
            .put("ok", true)
            .put("complete", true)
            .put(
                "pending",
                JSONArray().put(
                    JSONObject().put("approvalId", "ap_1").put("sessionId", "s_1")
                        .put("kind", "command").put("title", "执行命令需要审批")
                        .put("status", "pending").put("openedAt", 1_790_000_000_000L),
                ),
            )
            .put(
                "recent",
                JSONArray().put(
                    JSONObject().put("approvalId", "ap_0").put("resolution", "rejected").put("closedAt", 1_790_000_100_000L),
                ),
            )
        val (pending, recent) = Wire.parseApprovals(doc)
        assertEquals(1, pending.size)
        assertEquals("ap_1", pending[0].approvalId)
        assertEquals("执行命令需要审批", pending[0].title)
        assertEquals(1, recent.size)
        assertEquals("已拒绝", Wire.approvalResolutionText(recent[0].resolution))
    }

    @Test
    fun approvalTitlesCoverToolFamilies() {
        assertEquals("执行命令需要审批", Wire.approvalTitleOf("pwsh"))
        assertEquals("执行命令需要审批", Wire.approvalTitleOf("Bash"))
        assertEquals("修改文件需要审批", Wire.approvalTitleOf("str-replace-editor"))
        assertEquals("联网访问需要审批", Wire.approvalTitleOf("web-fetch"))
        assertEquals("执行操作需要审批", Wire.approvalTitleOf("ralph"))
        assertEquals("已处理", Wire.approvalResolutionText("unknown"))
    }

    @Test
    fun toolCallIsNotCountedTwice() {
        val embedded = JSONArray().put(
            JSONObject().put("type", "tool-call").put("id", "call_1").put("name", "pwsh")
                .put("arguments", JSONObject().put("command", "echo hi").toString()),
        )
        val items = JSONArray()
            .put(
                JSONObject().put(
                    "event",
                    JSONObject().put("type", "assistant/message")
                        .put("data", JSONObject().put("message", JSONObject().put("content", embedded))),
                ),
            )
            .put(
                JSONObject().put(
                    "event",
                    JSONObject().put("type", "tool/call")
                        .put("data", JSONObject().put("callId", "call_1").put("name", "pwsh").put("arguments", "{\"command\":\"echo hi\"}")),
                ),
            )
        val parsed = Wire.parseHistory(JSONObject().put("items", items))
        assertEquals(1, parsed.rows.size)
        assertEquals(Role.TOOL, parsed.rows[0].who)
        assertEquals("调用 pwsh", parsed.rows[0].text)
        assertEquals("echo hi", parsed.rows[0].detail)
    }

    @Test
    fun findsSvgInsideToolArguments() {
        val args = JSONObject()
            .put("command", "\$svg = @'\n<svg xmlns=\"http://www.w3.org/2000/svg\"><circle r=\"4\"/></svg>\n'@")
            .toString()
        val decoded = Wire.decodeArgs(args)
        assertTrue(decoded.contains("\n<svg"))
        val artifact = Wire.findArtifact(decoded)
        assertEquals("svg", artifact?.kind)
        assertTrue(artifact!!.markup.endsWith("</svg>"))
    }

    @Test
    fun findsFencedHtmlAndIgnoresPlainText() {
        val fenced = "给你一个文件：\n```html\n<!doctype html><html><body>hi</body></html>\n```"
        assertEquals("html", Wire.findArtifact(fenced)?.kind)
        assertNull(Wire.findArtifact("普通的一段回复，没有画图"))
        assertNull(Wire.findArtifact(""))
    }

    @Test
    fun reasoningChunksAreNotReplyText() {
        val chunk = JSONObject().put("chunk", JSONObject().put("type", "reasoning").put("text", "嗯…"))
        assertTrue(Wire.chunkIsReasoning(chunk))
        val answer = JSONObject().put("chunk", JSONObject().put("type", "text").put("text", "答案"))
        assertFalse(Wire.chunkIsReasoning(answer))
    }

    @Test
    fun failedToolResultsAreDetectedInBothShapes() {
        val nested = JSONObject().put(
            "message",
            JSONObject().put(
                "content",
                JSONArray().put(
                    JSONObject().put("type", "tool-result")
                        .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "sandbox denied")))
                        .put("isError", true),
                ),
            ),
        )
        assertTrue(Wire.isFailedResult(nested))
        val ok = JSONObject().put(
            "message",
            JSONObject().put(
                "content",
                JSONArray().put(
                    JSONObject().put("type", "tool-result").put("isError", false),
                ),
            ),
        )
        assertFalse(Wire.isFailedResult(ok))
        assertTrue(Wire.isFailedResult(JSONObject().put("isError", true)))
        assertFalse(Wire.isFailedResult(JSONObject()))
    }

    // -------------------------------------------------------------- sessions

    @Test
    fun titleComesFromProjections() {
        val session = JSONObject()
            .put("sessionId", "deadbeef-cafe-4000-8000-000000000000")
            .put(
                "projections",
                JSONObject().put(
                    "values",
                    JSONObject().put("session.title", "异种钢激光焊"),
                ),
            )
        assertEquals("异种钢激光焊", Wire.titleOf(session))
    }

    @Test
    fun titleFallsBackToIdPrefix() {
        val session = JSONObject().put("sessionId", "deadbeef-cafe-4000-8000-000000000000")
        assertEquals("deadbeef", Wire.titleOf(session))
    }

    @Test
    fun epochSecondsBecomeMillis() {
        assertEquals(1_700_000_000_000L, Wire.millis(1_700_000_000L))
        assertEquals(1_700_000_000_000L, Wire.millis(1_700_000_000_000L))
    }

    // ------------------------------------------------------ turn failures

    @Test
    fun turnEndErrorExtractsProviderMessage() {
        // 真实载荷（2026-09-23 hyb/glm-5.3-flash 的"你好"被反测活拦截）
        val message = "400: {\"code\":\"content_anti_probe_blocking\",\"message\":\"反测活已拦截本次请求：短消息命中测活探针关键词（如 hi、你好等） (request id: 20260923180742515645600IjN73max)\",\"type\":\"one_hub_err\"}"
        val data = JSONObject().put("turn", 1).put(
            "reason",
            JSONObject().put("kind", "error").put("error", JSONObject().put("message", message)),
        )
        assertEquals(
            "请求失败：400 反测活已拦截本次请求：短消息命中测活探针关键词（如 hi、你好等）",
            Wire.turnEndError(data),
        )
    }

    @Test
    fun turnEndErrorIgnoresNormalEndings() {
        assertNull(Wire.turnEndError(JSONObject()))
        assertNull(Wire.turnEndError(JSONObject().put("reason", JSONObject().put("kind", "aborted"))))
        assertNull(Wire.turnEndError(JSONObject().put("reason", JSONObject().put("kind", "completed"))))
    }

    @Test
    fun turnEndErrorPassesThroughPlainMessages() {
        val data = JSONObject().put(
            "reason",
            JSONObject().put("kind", "error").put("error", JSONObject().put("message", "plain boom")),
        )
        assertEquals("请求失败：plain boom", Wire.turnEndError(data))
    }

    @Test
    fun parseHistoryEmitsErrorRow() {
        val event = JSONObject().put("type", "turn/end").put(
            "data",
            JSONObject().put(
                "reason",
                JSONObject().put("kind", "error").put("error", JSONObject().put("message", "boom")),
            ),
        )
        val parsed = Wire.parseHistory(JSONObject().put("items", JSONArray().put(JSONObject().put("event", event))))
        assertEquals(1, parsed.rows.count { it.who == Role.ERROR })
        assertTrue(parsed.rows.first { it.who == Role.ERROR }.text.startsWith("请求失败："))
    }

    // ------------------------------------------------------ truncated turns

    @Test
    fun turnEndTruncatedExplainsMaxTokens() {
        // 真实载荷（2026-09-23 皮卡丘会话：思考太长撞上输出上限，回合结束了却没有答案）
        val data = JSONObject().put("turn", 1).put("reason", JSONObject().put("kind", "max-tokens"))
        val message = Wire.turnEndTruncated(data)
        assertTrue(message != null)
        assertTrue(message!!.contains("输出达到长度上限"))
        assertTrue(message.contains("继续"))
    }

    @Test
    fun turnEndTruncatedIgnoresOtherEndings() {
        assertNull(Wire.turnEndTruncated(JSONObject()))
        assertNull(Wire.turnEndTruncated(JSONObject().put("reason", JSONObject().put("kind", "completed"))))
        assertNull(Wire.turnEndTruncated(JSONObject().put("reason", JSONObject().put("kind", "error"))))
    }

    @Test
    fun parseHistoryEmitsTruncatedRow() {
        val event = JSONObject().put("type", "turn/end").put(
            "data",
            JSONObject().put("turn", 1).put("reason", JSONObject().put("kind", "max-tokens")),
        )
        val parsed = Wire.parseHistory(JSONObject().put("items", JSONArray().put(JSONObject().put("event", event))))
        assertEquals(1, parsed.rows.count { it.who == Role.TRUNCATED })
    }

    // ------------------------------------------------ 执行记录折叠 / 时长

    @Test
    fun fmtDurationShapes() {
        assertEquals("45 秒", Wire.fmtDuration(45_000))
        assertEquals("2 分", Wire.fmtDuration(120_000))
        assertEquals("2 分 5 秒", Wire.fmtDuration(125_000))
        assertEquals("1 小时", Wire.fmtDuration(3_600_000))
        assertEquals("1 小时 5 分", Wire.fmtDuration(3_900_000))
    }

    @Test
    fun traceBlocksFoldRunOfSteps() {
        val rows = listOf(
            ChatRow(Role.USER, "hi", time = 1000),
            ChatRow(Role.REASONING, "think", time = 2000),
            ChatRow(Role.TOOL, "调用 pwsh", time = 3000),
            ChatRow(Role.TOOL, "工具返回", time = 4000),
            ChatRow(Role.ASSISTANT, "done", time = 20_000),
        )
        val blocks = Wire.traceBlocks(rows, endTime = 20_000)
        assertEquals(1, blocks.size)
        assertEquals(1, blocks[0].start)
        assertEquals(3, blocks[0].end)
        // 时长 = 首行 2s → 下一锚点 20s；步数 = 1 次调用
        assertEquals("执行 1 步 · 18 秒", blocks[0].label)
    }

    @Test
    fun traceBlocksExposeStepsDurationAndLastAction() {
        val rows = listOf(
            ChatRow(Role.REASONING, "think", time = 2000),
            ChatRow(Role.TOOL, "调用 pwsh", time = 3000),
            ChatRow(Role.TOOL, "工具返回", time = 4000),
            ChatRow(Role.TOOL, "调用 读取文件", time = 5000),
            ChatRow(Role.ASSISTANT, "done", time = 20_000),
        )
        val blocks = Wire.traceBlocks(rows, endTime = 20_000)
        assertEquals(1, blocks.size)
        assertEquals(2, blocks[0].steps)
        assertEquals("18 秒", blocks[0].durText)
        // 「当前：…」优先取最近的「调用 …」动作行（而不是「工具返回」结果行），展示为「运行 <名>」。
        assertEquals("运行 读取文件", blocks[0].lastAction)
    }

    @Test
    fun traceBlocksUseEndTimeForTail() {
        val rows = listOf(
            ChatRow(Role.USER, "go", time = 1000),
            ChatRow(Role.REASONING, "a", time = 2000),
            ChatRow(Role.TOOL, "调用 x", time = 3000),
        )
        val blocks = Wire.traceBlocks(rows, endTime = 62_000)
        assertEquals(1, blocks.size)
        assertEquals("执行 1 步 · 1 分", blocks[0].label)
    }

    @Test
    fun traceBlocksNeedAtLeastTwoRows() {
        val rows = listOf(
            ChatRow(Role.REASONING, "solo", time = 1000),
            ChatRow(Role.ASSISTANT, "answer", time = 2000),
        )
        assertTrue(Wire.traceBlocks(rows, 2000).isEmpty())
    }

    @Test
    fun traceBlocksSplitOnAnchors() {
        val rows = listOf(
            ChatRow(Role.TOOL, "调用 a", time = 1000),
            ChatRow(Role.TOOL, "工具返回", time = 2000),
            ChatRow(Role.ASSISTANT, "mid", time = 3000),
            ChatRow(Role.REASONING, "b", time = 4000),
            ChatRow(Role.TOOL, "调用 c", time = 5000),
            ChatRow(Role.ASSISTANT, "end", time = 9000),
        )
        val blocks = Wire.traceBlocks(rows, 9000)
        assertEquals(2, blocks.size)
        assertEquals(0, blocks[0].start)
        assertEquals(3, blocks[1].start)
    }

    @Test
    fun traceBlocksLabelThinkingOnlyGroup() {
        val rows = listOf(
            ChatRow(Role.REASONING, "a", time = 1000),
            ChatRow(Role.REASONING, "b", time = 2000),
            ChatRow(Role.ASSISTANT, "x", time = 12_000),
        )
        val blocks = Wire.traceBlocks(rows, 12_000)
        assertEquals(1, blocks.size)
        assertEquals("思考 2 段 · 11 秒", blocks[0].label)
    }

    @Test
    fun parseHistoryStampsRowTimesAndEndTime() {
        val t = 1_700_000_000_000L
        val message = JSONObject()
            .put("type", "assistant/message")
            .put("time", t)
            .put(
                "data",
                JSONObject().put(
                    "message",
                    JSONObject().put(
                        "content",
                        JSONArray().put(JSONObject().put("type", "text").put("text", "hello")),
                    ),
                ),
            )
        val parsed = Wire.parseHistory(JSONObject().put("items", JSONArray().put(JSONObject().put("event", message))))
        assertEquals(t, parsed.rows.first { it.who == Role.ASSISTANT }.time)
        assertEquals(t, parsed.endTime)
    }

    // ------------------------------------------------------------- 其它打磨

    @Test
    fun cleanTitleStripsMarkdownAndNewlines() {
        assertEquals("皮卡丘跳舞动画", Wire.cleanTitle("`皮卡丘跳舞动画`"))
        assertEquals("a b", Wire.cleanTitle("a\n  b"))
        assertEquals("标题", Wire.cleanTitle("  标题  "))
    }

    @Test
    fun parseSessionReadsFileCount() {
        val session = JSONObject().put("sessionId", "s1").put("producedFiles", 3)
        assertEquals(3, Wire.parseSession(session).fileCount)
        assertEquals(0, Wire.parseSession(JSONObject().put("sessionId", "s2")).fileCount)
    }

    // ------------------------------------------------------------- 搜索降级

    @Test
    fun filterSessionsByTitleMatchesCaseInsensitiveSubstring() {
        val items = listOf(
            SessionSummary("a", "修复公式渲染", 1, false, ""),
            SessionSummary("b", "Session Search Disabled", 2, false, ""),
            SessionSummary("c", "设置页改版", 3, false, ""),
        )
        assertEquals(listOf("a"), Wire.filterSessionsByTitle(items, "公式").map { it.sessionId })
        assertEquals(listOf("b"), Wire.filterSessionsByTitle(items, "session").map { it.sessionId })
        assertEquals(listOf("c"), Wire.filterSessionsByTitle(items, "设置").map { it.sessionId })
        assertEquals(listOf("a", "b", "c"), Wire.filterSessionsByTitle(items, "  ").map { it.sessionId })
        assertTrue(Wire.filterSessionsByTitle(items, "不存在的词").isEmpty())
    }

    // ------------------------------------------------------------- timeText / contextLabel

    @Test
    fun timeTextFollowsGroupedListConventions() {
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0): Long =
            java.time.LocalDateTime.of(y, mo, d, h, mi, s).atZone(zone).toInstant().toEpochMilli()
        val now = at(2026, 9, 26, 14, 40)
        assertEquals("刚刚", Wire.timeTextAt(now, at(2026, 9, 26, 14, 39, 50), zone))
        assertEquals("1 分钟前", Wire.timeTextAt(now, at(2026, 9, 26, 14, 39), zone))
        assertEquals("5 分钟前", Wire.timeTextAt(now, at(2026, 9, 26, 14, 35), zone))
        assertEquals("13:20", Wire.timeTextAt(now, at(2026, 9, 26, 13, 20), zone))
        assertEquals("昨天 23:10", Wire.timeTextAt(now, at(2026, 9, 25, 23, 10), zone))
        assertEquals("9月20日", Wire.timeTextAt(now, at(2026, 9, 20, 8, 5), zone))
        assertEquals("", Wire.timeTextAt(now, 0L, zone))
    }

    @Test
    fun contextLabelShortensWindowSizes() {
        assertEquals("1M", com.dsh.mobile.ui.contextLabel("1048576"))
        assertEquals("500K", com.dsh.mobile.ui.contextLabel("500000"))
        assertEquals("128K", com.dsh.mobile.ui.contextLabel("128000"))
        assertNull(com.dsh.mobile.ui.contextLabel("—"))
        assertNull(com.dsh.mobile.ui.contextLabel(""))
    }

    @Test
    fun previewTextStripsMarkdownNoise() {
        assertEquals("执行结果 桌面 8 秒正常启动", Wire.previewText("## 执行结果 **桌面 8 秒正常启动**"))
        assertEquals("见 文档 说明", Wire.previewText("见 [文档](https://example.com/x) 说明"))
        assertEquals("", Wire.previewText("   "))
    }

    // ---------------------------------------------------------- 图片消息

    @Test
    fun userMessageWithImageParsesAttachments() {
        val data = JSONObject()
            .put(
                "content",
                JSONArray()
                    .put(JSONObject().put("type", "text").put("text", "像这样一个软件设计界面，可以从哪几个方面优化"))
                    .put(
                        JSONObject().put("type", "image").put(
                            "attachment",
                            JSONObject()
                                .put("attachmentId", "sha256:abc123")
                                .put("mediaType", "image/jpeg")
                                .put("width", 718)
                                .put("height", 1600)
                                .put("name", "photo.jpg"),
                        ),
                    ),
            )
            .put("role", "user")
        val items = JSONArray().put(
            JSONObject().put(
                "event",
                JSONObject().put("type", "user/message").put("time", 1L).put("data", data),
            ),
        )
        val parsed = Wire.parseHistory(JSONObject().put("items", items))
        val row = parsed.rows.last { it.who == Role.USER }
        assertEquals("像这样一个软件设计界面，可以从哪几个方面优化", row.text)
        assertEquals(1, row.images.size)
        assertEquals("sha256:abc123", row.images[0].attachmentId)
        assertEquals(1600, row.images[0].height)
        assertEquals("photo.jpg", row.images[0].name)
    }

    @Test
    fun imageOnlyMessageStillShows() {
        val data = JSONObject()
            .put(
                "content",
                JSONArray().put(
                    JSONObject().put("type", "image").put(
                        "attachment",
                        JSONObject()
                            .put("attachmentId", "sha256:def456")
                            .put("mediaType", "image/png")
                            .put("name", "a.png"),
                    ),
                ),
            )
            .put("role", "user")
        val items = JSONArray().put(
            JSONObject().put(
                "event",
                JSONObject().put("type", "user/message").put("time", 2L).put("data", data),
            ),
        )
        val parsed = Wire.parseHistory(JSONObject().put("items", items))
        assertEquals(1, parsed.rows.count { it.who == Role.USER })
        assertEquals(1, parsed.rows.last { it.who == Role.USER }.images.size)
    }
}
