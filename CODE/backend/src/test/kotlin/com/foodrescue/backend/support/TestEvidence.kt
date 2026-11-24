package com.foodrescue.backend.support

import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.Optional
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

enum class EvidenceStatus { PASSED, FAILED, SKIPPED }

sealed interface EvidenceStep {
    val timestamp: Instant

    data class HttpStep(
        override val timestamp: Instant,
        val method: String,
        val path: String,
        val authenticated: Boolean,
        val requestSummary: String?,
        val statusCode: Int,
        val responseSummary: String?,
        val durationMs: Long
    ) : EvidenceStep

    data class DbStep(
        override val timestamp: Instant,
        val sql: String,
        val result: String
    ) : EvidenceStep

    data class MailStep(
        override val timestamp: Instant,
        val recipient: String,
        val purpose: String
    ) : EvidenceStep
}

data class ExtractedTestSource(
    val fileHeader: String?,
    val methodSource: String?,
    val fileRelativePath: String?,
    val startLine: Int?,
    val assertionCount: Int
)

data class TestCaseRecord(
    val className: String,
    val simpleClassName: String,
    val methodName: String,
    val displayName: String,
    val declaringClassName: String,
    val startedAt: Instant,
    var durationMs: Long = 0,
    var status: EvidenceStatus = EvidenceStatus.PASSED,
    var reasonOrError: String? = null,
    var stackTrace: String? = null,
    val source: ExtractedTestSource,
    val steps: MutableList<EvidenceStep> = Collections.synchronizedList(mutableListOf())
)

object TestEvidenceCollector {
    val records = CopyOnWriteArrayList<TestCaseRecord>()
    private val activeCase = AtomicReference<TestCaseRecord?>()
    private val prettyMapper: ObjectMapper = JsonMapper.builder()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .build()

    private val secretKeyRegex = Regex(
        """("(?:password|passwordValue|newPassword|currentPassword|accessToken|refreshToken|token|apiKey|checksumKey|secret)"\s*:\s*")([^"]*)(")""",
        RegexOption.IGNORE_CASE
    )

    fun startCase(record: TestCaseRecord) {
        records.add(record)
        activeCase.set(record)
    }

    fun finishCase(
        status: EvidenceStatus,
        durationMs: Long? = null,
        reasonOrError: String? = null,
        stackTrace: String? = null
    ): TestCaseRecord? {
        val current = activeCase.getAndSet(null) ?: return null
        if (durationMs != null) current.durationMs = durationMs
        current.status = status
        current.reasonOrError = reasonOrError
        current.stackTrace = stackTrace
        return current
    }

    fun addSkippedCase(record: TestCaseRecord) {
        records.add(record)
    }

    fun currentCase(): TestCaseRecord? = activeCase.get()

    fun recordHttp(
        method: String,
        path: String,
        authenticated: Boolean,
        requestBody: String?,
        statusCode: Int,
        responseBody: String?,
        durationMs: Long,
        contentType: String? = null
    ) {
        val current = activeCase.get() ?: return
        val reqSummary = summarizePayload(requestBody, null, maxLen = 1200)
        val resSummary = summarizePayload(responseBody, contentType, maxLen = 1800)
        val step = EvidenceStep.HttpStep(
            timestamp = Instant.now(),
            method = method,
            path = path,
            authenticated = authenticated,
            requestSummary = reqSummary,
            statusCode = statusCode,
            responseSummary = resSummary,
            durationMs = durationMs
        )
        current.steps.add(step)

        val authLabel = if (authenticated) "auth=Bearer" else "auth=none"
        val resPreview = oneLinePreview(resSummary, 160)
        println("  [HTTP] $method $path ($authLabel) -> $statusCode (${durationMs}ms)${if (resPreview != null) " | $resPreview" else ""}")
    }

    fun recordBinaryHttp(
        method: String,
        path: String,
        authenticated: Boolean,
        requestSummary: String?,
        statusCode: Int,
        responseSizeBytes: Int,
        contentType: String?,
        durationMs: Long
    ) {
        val current = activeCase.get() ?: return
        val resSummary = "<binary ${responseSizeBytes} bytes${if (!contentType.isNullOrBlank()) ", $contentType" else ""}>"
        val step = EvidenceStep.HttpStep(
            timestamp = Instant.now(),
            method = method,
            path = path,
            authenticated = authenticated,
            requestSummary = requestSummary,
            statusCode = statusCode,
            responseSummary = resSummary,
            durationMs = durationMs
        )
        current.steps.add(step)

        val authLabel = if (authenticated) "auth=Bearer" else "auth=none"
        println("  [HTTP] $method $path ($authLabel) -> $statusCode (${durationMs}ms) | $resSummary")
    }

    fun recordDb(sql: String, result: Any?) {
        val current = activeCase.get() ?: return
        val cleanSql = sql.replace(Regex("\\s+"), " ").trim()
        val resultStr = result?.toString() ?: "null"
        current.steps.add(EvidenceStep.DbStep(Instant.now(), cleanSql, resultStr))
        println("  [DB]   $cleanSql -> $resultStr")
    }

    fun recordMail(email: String, purpose: String) {
        val current = activeCase.get() ?: return
        current.steps.add(EvidenceStep.MailStep(Instant.now(), email, purpose))
        println("  [MAIL] $purpose -> $email")
    }

    private fun summarizePayload(raw: String?, contentType: String?, maxLen: Int): String? {
        if (raw.isNullOrBlank()) return null
        val ct = contentType?.lowercase().orEmpty()
        if (ct.contains("javascript") || ct.contains("text/css") || ct.contains("text/html") || raw.length > 20000) {
            return "<${if (ct.isNotBlank()) ct.substringBefore(';') else "text"} ${raw.length} chars>"
        }
        val masked = maskSecrets(raw.trim())
        val formatted = if ((masked.startsWith("{") && masked.endsWith("}")) || (masked.startsWith("[") && masked.endsWith("]"))) {
            try {
                val tree = prettyMapper.readTree(masked)
                prettyMapper.writeValueAsString(tree)
            } catch (_: Exception) {
                masked
            }
        } else {
            masked
        }
        return if (formatted.length > maxLen) {
            formatted.take(maxLen) + "\n... [truncated ${formatted.length - maxLen} chars]"
        } else {
            formatted
        }
    }

    private fun maskSecrets(text: String): String {
        return secretKeyRegex.replace(text) { m ->
            val value = m.groupValues[2]
            val maskedVal = if (value.length <= 6) "***" else "${value.take(4)}***(${value.length}ch)"
            "${m.groupValues[1]}$maskedVal${m.groupValues[3]}"
        }
    }

    private fun oneLinePreview(text: String?, maxLen: Int): String? {
        if (text.isNullOrBlank()) return null
        val single = text.replace(Regex("\\s+"), " ").trim()
        return if (single.length > maxLen) single.take(maxLen) + "..." else single
    }
}

object TestSourceExtractor {
    private val testRoot = Path.of("src", "test", "kotlin")
    private val classFileCache = mutableMapOf<String, Path?>()
    private val fileLinesCache = mutableMapOf<Path, List<String>>()

    @Synchronized
    fun extract(declaringClassName: String, methodName: String): ExtractedTestSource {
        val file = findSourceFile(declaringClassName) ?: return ExtractedTestSource(null, null, null, null, 0)
        val lines = fileLinesCache.getOrPut(file) { Files.readAllLines(file, StandardCharsets.UTF_8) }
        val header = lines.take(3)
            .firstOrNull { it.trim().startsWith("//") }
            ?.trim()
            ?.removePrefix("//")
            ?.trim()

        val relPath = try {
            Path.of("").toAbsolutePath().relativize(file.toAbsolutePath()).toString().replace('\\', '/')
        } catch (_: Exception) {
            file.toString().replace('\\', '/')
        }

        val cleanMethodName = methodName.substringBefore('(')
        val matchIdx = findMethodLine(lines, cleanMethodName)
        if (matchIdx < 0) {
            return ExtractedTestSource(header, null, relPath, null, 0)
        }

        var startIdx = matchIdx
        while (startIdx > 0 && lines[startIdx - 1].trimStart().startsWith("@")) {
            startIdx--
        }

        val snippet = extractBlock(lines, startIdx, matchIdx)
        val assertionCount = Regex("""\bassert[A-Z]\w*\b|\bassertField\b""").findAll(snippet).count()
        return ExtractedTestSource(header, snippet, relPath, startIdx + 1, assertionCount)
    }

    private fun findSourceFile(className: String): Path? {
        return classFileCache.getOrPut(className) {
            val simpleName = className.substringAfterLast('.')
            val direct = testRoot.resolve(className.replace('.', '/') + ".kt")
            if (Files.isRegularFile(direct)) return@getOrPut direct
            if (!Files.exists(testRoot)) return@getOrPut null
            val classDeclRegex = Regex("""\bclass\s+$simpleName\b""")
            Files.walk(testRoot).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                    .filter { path ->
                        val text = Files.readString(path, StandardCharsets.UTF_8)
                        classDeclRegex.containsMatchIn(text)
                    }
                    .findFirst()
                    .orElse(null)
            }
        }
    }

    private fun findMethodLine(lines: List<String>, methodName: String): Int {
        val escaped = Regex.escape(methodName)
        val pattern = Regex("""\bfun\s+(`$escaped`|$escaped)\s*\(""")
        return lines.indexOfFirst { pattern.containsMatchIn(it) }
    }

    private fun extractBlock(lines: List<String>, startIdx: Int, sigIdx: Int): String {
        val sb = StringBuilder()
        var depth = 0
        var opened = false
        var inBlockComment = false
        var inTripleQuote = false

        for (i in startIdx until lines.size) {
            val line = lines[i]
            if (i > startIdx) sb.append('\n')
            sb.append(line)
            if (i < sigIdx) continue

            var j = 0
            var inString = false
            var inChar = false
            while (j < line.length) {
                if (inBlockComment) {
                    if (j + 1 < line.length && line[j] == '*' && line[j + 1] == '/') {
                        inBlockComment = false
                        j += 2
                    } else {
                        j++
                    }
                    continue
                }
                if (inTripleQuote) {
                    if (j + 2 < line.length && line[j] == '"' && line[j + 1] == '"' && line[j + 2] == '"') {
                        inTripleQuote = false
                        j += 3
                    } else {
                        j++
                    }
                    continue
                }
                if (inString) {
                    if (line[j] == '\\') {
                        j += 2
                    } else if (line[j] == '"') {
                        inString = false
                        j++
                    } else {
                        j++
                    }
                    continue
                }
                if (inChar) {
                    if (line[j] == '\\') {
                        j += 2
                    } else if (line[j] == '\'') {
                        inChar = false
                        j++
                    } else {
                        j++
                    }
                    continue
                }

                if (j + 1 < line.length && line[j] == '/' && line[j + 1] == '/') {
                    break
                }
                if (j + 1 < line.length && line[j] == '/' && line[j + 1] == '*') {
                    inBlockComment = true
                    j += 2
                    continue
                }
                if (j + 2 < line.length && line[j] == '"' && line[j + 1] == '"' && line[j + 2] == '"') {
                    inTripleQuote = true
                    j += 3
                    continue
                }
                if (line[j] == '"') {
                    inString = true
                    j++
                    continue
                }
                if (line[j] == '\'') {
                    inChar = true
                    j++
                    continue
                }
                if (line[j] == '{') {
                    depth++
                    opened = true
                } else if (line[j] == '}') {
                    depth--
                    if (opened && depth <= 0) {
                        return sb.toString().trimEnd()
                    }
                }
                j++
            }

            // Expression-body function without braces
            if (!opened && i >= sigIdx && line.contains('=') && !line.trimEnd().endsWith("=")) {
                return sb.toString().trimEnd()
            }
        }
        return sb.toString().trimEnd()
    }
}

class TestEvidenceExtension : BeforeAllCallback, BeforeTestExecutionCallback, AfterTestExecutionCallback, TestWatcher {
    companion object {
        private const val START_MS_KEY = "test.evidence.startMs"
        private const val REPORT_HOOK_KEY = "test.evidence.reportWriter"
    }

    override fun beforeAll(context: ExtensionContext) {
        val globalStore = context.root.getStore(ExtensionContext.Namespace.GLOBAL)
        globalStore.computeIfAbsent(REPORT_HOOK_KEY) { ReportWriterCloseable() }
    }

    override fun beforeTestExecution(context: ExtensionContext) {
        val globalStore = context.root.getStore(ExtensionContext.Namespace.GLOBAL)
        globalStore.computeIfAbsent(REPORT_HOOK_KEY) { ReportWriterCloseable() }

        val testClass = context.requiredTestClass
        val testMethod = context.requiredTestMethod
        val source = TestSourceExtractor.extract(testMethod.declaringClass.name, testMethod.name)
        val record = TestCaseRecord(
            className = testClass.name,
            simpleClassName = testClass.simpleName,
            methodName = testMethod.name,
            displayName = context.displayName,
            declaringClassName = testMethod.declaringClass.name,
            startedAt = Instant.now(),
            source = source
        )
        TestEvidenceCollector.startCase(record)
        context.getStore(ExtensionContext.Namespace.create(TestEvidenceExtension::class.java))
            .put(START_MS_KEY, System.nanoTime())

        println("[CASE] ${testClass.simpleName} > ${context.displayName}")
        if (!source.fileHeader.isNullOrBlank()) {
            println("  [SPEC] ${source.fileHeader}")
        }
    }

    override fun afterTestExecution(context: ExtensionContext) {
        val startNs = context.getStore(ExtensionContext.Namespace.create(TestEvidenceExtension::class.java))
            .get(START_MS_KEY, Long::class.javaObjectType)
        if (startNs != null) {
            val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(0L)
            TestEvidenceCollector.currentCase()?.durationMs = elapsedMs
        }
    }

    override fun testSuccessful(context: ExtensionContext) {
        val rec = TestEvidenceCollector.finishCase(EvidenceStatus.PASSED) ?: return
        printFooter(rec)
    }

    override fun testFailed(context: ExtensionContext, cause: Throwable?) {
        val st = cause?.stackTraceToString()?.take(3000)
        val rec = TestEvidenceCollector.finishCase(
            status = EvidenceStatus.FAILED,
            reasonOrError = cause?.message ?: cause?.javaClass?.simpleName,
            stackTrace = st
        ) ?: return
        printFooter(rec)
    }

    override fun testAborted(context: ExtensionContext, cause: Throwable?) {
        val rec = TestEvidenceCollector.finishCase(
            status = EvidenceStatus.SKIPPED,
            reasonOrError = cause?.message ?: "Aborted"
        ) ?: return
        printFooter(rec)
    }

    override fun testDisabled(context: ExtensionContext, reason: Optional<String>) {
        val testClass = context.requiredTestClass
        val testMethod = context.requiredTestMethod
        val source = TestSourceExtractor.extract(testMethod.declaringClass.name, testMethod.name)
        val why = reason.orElse("Disabled by condition")
        val rec = TestCaseRecord(
            className = testClass.name,
            simpleClassName = testClass.simpleName,
            methodName = testMethod.name,
            displayName = context.displayName,
            declaringClassName = testMethod.declaringClass.name,
            startedAt = Instant.now(),
            durationMs = 0,
            status = EvidenceStatus.SKIPPED,
            reasonOrError = why,
            source = source
        )
        TestEvidenceCollector.addSkippedCase(rec)
        println("[CASE] ${testClass.simpleName} > ${context.displayName}")
        println("  [SKIPPED] $why")
    }

    private fun printFooter(rec: TestCaseRecord) {
        val httpCount = rec.steps.count { it is EvidenceStep.HttpStep }
        val dbCount = rec.steps.count { it is EvidenceStep.DbStep }
        val mailCount = rec.steps.count { it is EvidenceStep.MailStep }
        if (httpCount == 0 && dbCount == 0 && !rec.source.methodSource.isNullOrBlank()) {
            val assertLines = rec.source.methodSource.lines()
                .map { it.trim() }
                .filter { it.contains("assert") || it.contains("validate") || it.contains("calculate") }
                .take(8)
            for (line in assertLines) {
                println("  [UNIT-ASSERT] $line")
            }
        }
        println("[${rec.status}] ${rec.simpleClassName} > ${rec.displayName} | duration=${rec.durationMs}ms | http=$httpCount | db=$dbCount | mail=$mailCount | codeAsserts=${rec.source.assertionCount}")
    }

    private class ReportWriterCloseable : AutoCloseable {
        override fun close() {
            EvidenceHtmlReportWriter.write(TestEvidenceCollector.records)
        }
    }
}

object EvidenceHtmlReportWriter {
    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
        .withZone(ZoneId.systemDefault())

    fun write(records: List<TestCaseRecord>) {
        if (records.isEmpty()) return
        val outPath = Path.of("build", "reports", "tests", "evidence-report.html")
        Files.createDirectories(outPath.parent)

        val total = records.size
        val passed = records.count { it.status == EvidenceStatus.PASSED }
        val failed = records.count { it.status == EvidenceStatus.FAILED }
        val skipped = records.count { it.status == EvidenceStatus.SKIPPED }
        val totalHttp = records.sumOf { r -> r.steps.count { it is EvidenceStep.HttpStep } }
        val totalDb = records.sumOf { r -> r.steps.count { it is EvidenceStep.DbStep } }
        val totalDurationMs = records.sumOf { it.durationMs }
        val grouped = records.groupBy { it.className }

        val html = buildString {
            append("""
<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1.0"/>
<title>FoodRescue Backend - Test Evidence Report</title>
<style>
:root {
  --bg: #f6f8fa; --card: #ffffff; --border: #d0d7de; --text: #1f2328; --muted: #656d76;
  --pass-bg: #dafbe1; --pass-fg: #116329; --fail-bg: #ffebe9; --fail-fg: #cf222e;
  --skip-bg: #fff8c5; --skip-fg: #9a6700; --accent: #0969da; --code-bg: #f6f8fa;
}
* { box-sizing: border-box; }
body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Arial, sans-serif; background: var(--bg); color: var(--text); line-height: 1.5; }
.container { max-width: 1280px; margin: 0 auto; padding: 24px 20px 60px; }
.header { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 20px; }
h1 { margin: 0 0 4px; font-size: 24px; }
.subtitle { color: var(--muted); font-size: 13px; }
.kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 12px; margin-bottom: 20px; }
.kpi { background: var(--card); border: 1px solid var(--border); border-radius: 8px; padding: 14px 16px; }
.kpi .val { font-size: 24px; font-weight: 700; }
.kpi .lbl { font-size: 12px; color: var(--muted); text-transform: uppercase; letter-spacing: .04em; }
.kpi.pass .val { color: var(--pass-fg); }
.kpi.fail .val { color: var(--fail-fg); }
.kpi.skip .val { color: var(--skip-fg); }
.toolbar { background: var(--card); border: 1px solid var(--border); border-radius: 8px; padding: 12px 14px; display: flex; flex-wrap: wrap; gap: 10px; align-items: center; margin-bottom: 18px; position: sticky; top: 8px; z-index: 10; box-shadow: 0 2px 6px rgba(0,0,0,.04); }
.toolbar input { flex: 1; min-width: 240px; padding: 7px 10px; border: 1px solid var(--border); border-radius: 6px; font-size: 14px; }
.btn { padding: 6px 12px; border: 1px solid var(--border); background: #fff; border-radius: 6px; cursor: pointer; font-size: 13px; font-weight: 500; }
.btn:hover { background: #f3f4f6; }
.btn.active { background: var(--accent); color: #fff; border-color: var(--accent); }
.suite { background: var(--card); border: 1px solid var(--border); border-radius: 8px; margin-bottom: 16px; overflow: hidden; }
.suite-head { padding: 12px 16px; background: #f9fafb; border-bottom: 1px solid var(--border); display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 8px; }
.suite-title { font-weight: 700; font-size: 16px; }
.suite-spec { font-size: 12px; color: var(--muted); margin-top: 2px; }
.suite-stats { font-size: 12px; color: var(--muted); display: flex; gap: 8px; align-items: center; }
.case { border-bottom: 1px solid var(--border); }
.case:last-child { border-bottom: none; }
.case summary { list-style: none; cursor: pointer; padding: 10px 16px; display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 8px; }
.case summary::-webkit-details-marker { display: none; }
.case summary:hover { background: #f6f8fa; }
.case-left { display: flex; align-items: center; gap: 10px; flex: 1; min-width: 260px; }
.case-name { font-weight: 600; font-size: 14px; }
.case-meta { display: flex; gap: 8px; align-items: center; font-size: 12px; color: var(--muted); }
.badge { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 11px; font-weight: 700; }
.badge.PASSED { background: var(--pass-bg); color: var(--pass-fg); }
.badge.FAILED { background: var(--fail-bg); color: var(--fail-fg); }
.badge.SKIPPED { background: var(--skip-bg); color: var(--skip-fg); }
.badge.pill { background: #eef2ff; color: #3730a3; font-weight: 600; }
.case-body { padding: 12px 16px 16px; background: #fcfcfd; border-top: 1px dashed var(--border); }
.sec-title { font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: .04em; color: var(--muted); margin: 12px 0 6px; }
.sec-title:first-child { margin-top: 0; }
.http-item { border: 1px solid var(--border); border-radius: 6px; background: #fff; margin-bottom: 8px; overflow: hidden; }
.http-head { padding: 6px 10px; background: #f6f8fa; display: flex; flex-wrap: wrap; gap: 8px; align-items: center; font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12px; }
.m-badge { padding: 1px 6px; border-radius: 4px; font-weight: 700; color: #fff; background: #0969da; }
.m-POST { background: #1a7f37; } .m-PATCH, .m-PUT { background: #9a6700; } .m-DELETE { background: #cf222e; }
.st-ok { color: var(--pass-fg); font-weight: 700; }
.st-err { color: var(--fail-fg); font-weight: 700; }
.http-payloads { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 8px; padding: 8px 10px; }
.payload-box { background: var(--code-bg); border: 1px solid #e5e7eb; border-radius: 4px; padding: 6px 8px; }
.payload-lbl { font-size: 11px; font-weight: 600; color: var(--muted); margin-bottom: 3px; }
pre { margin: 0; white-space: pre-wrap; word-break: break-word; font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12px; }
.db-table { width: 100%; border-collapse: collapse; font-size: 12px; background: #fff; border: 1px solid var(--border); }
.db-table th, .db-table td { border: 1px solid var(--border); padding: 6px 8px; text-align: left; font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
.db-table th { background: #f6f8fa; font-family: inherit; }
.code-box { background: #f6f8fa; border: 1px solid var(--border); border-radius: 6px; padding: 10px 12px; overflow-x: auto; }
.err-box { background: var(--fail-bg); border: 1px solid #ff8182; color: var(--fail-fg); border-radius: 6px; padding: 10px 12px; }
@media print {
  .toolbar, .no-print { display: none !important; }
  body { background: #fff; }
  .suite { break-inside: avoid-page; }
}
</style>
</head>
<body>
<div class="container">
  <div class="header">
    <div>
      <h1>FoodRescue Backend — Test Evidence Report</h1>
      <div class="subtitle">Generated at ${esc(timeFmt.format(Instant.now()))} • Real Testcontainers PostgreSQL + Spring Boot HTTP + Unit Verification</div>
    </div>
    <div class="no-print">
      <a class="btn" href="test/index.html" style="text-decoration:none;color:inherit;">Mở Gradle Report (test/index.html)</a>
      <button class="btn" onclick="window.print()">In / Lưu PDF</button>
    </div>
  </div>

  <div class="kpis">
    <div class="kpi"><div class="val">$total</div><div class="lbl">Tổng Test Cases</div></div>
    <div class="kpi pass"><div class="val">$passed</div><div class="lbl">Passed</div></div>
    <div class="kpi fail"><div class="val">$failed</div><div class="lbl">Failed</div></div>
    <div class="kpi skip"><div class="val">$skipped</div><div class="lbl">Skipped</div></div>
    <div class="kpi"><div class="val">$totalHttp</div><div class="lbl">HTTP Calls Đã Chạy</div></div>
    <div class="kpi"><div class="val">$totalDb</div><div class="lbl">DB Checks</div></div>
    <div class="kpi"><div class="val">${formatDuration(totalDurationMs)}</div><div class="lbl">Tổng Thời Gian Test</div></div>
  </div>

  <div class="toolbar no-print">
    <input id="searchBox" type="text" placeholder="Tìm theo AC-xx, tên test, class, endpoint (/api/v1/...), SQL..." oninput="applyFilters()"/>
    <button class="btn active" data-filter="ALL" onclick="setStatusFilter('ALL', this)">Tất cả ($total)</button>
    <button class="btn" data-filter="PASSED" onclick="setStatusFilter('PASSED', this)">Passed ($passed)</button>
    <button class="btn" data-filter="FAILED" onclick="setStatusFilter('FAILED', this)">Failed ($failed)</button>
    <button class="btn" data-filter="SKIPPED" onclick="setStatusFilter('SKIPPED', this)">Skipped ($skipped)</button>
    <button class="btn" onclick="toggleAll(true)">Mở rộng tất cả</button>
    <button class="btn" onclick="toggleAll(false)">Thu gọn tất cả</button>
  </div>
""")

            for ((_, cases) in grouped) {
                val first = cases.first()
                val suitePass = cases.count { it.status == EvidenceStatus.PASSED }
                val suiteFail = cases.count { it.status == EvidenceStatus.FAILED }
                val suiteSkip = cases.count { it.status == EvidenceStatus.SKIPPED }
                val suiteSpec = cases.mapNotNull { it.source.fileHeader }.firstOrNull()
                val suiteFile = cases.mapNotNull { it.source.fileRelativePath }.firstOrNull()
                val suiteDuration = cases.sumOf { it.durationMs }

                append("""
  <div class="suite" data-suite>
    <div class="suite-head">
      <div>
        <div class="suite-title">${esc(first.simpleClassName)} <span style="font-weight:400;font-size:12px;color:var(--muted);">(${esc(first.className)})</span></div>
        ${if (!suiteSpec.isNullOrBlank() || !suiteFile.isNullOrBlank()) "<div class=\"suite-spec\">${esc(suiteSpec ?: "")}${if (!suiteSpec.isNullOrBlank() && !suiteFile.isNullOrBlank()) " • " else ""}${esc(suiteFile ?: "")}</div>" else ""}
      </div>
      <div class="suite-stats">
        <span class="badge PASSED">$suitePass pass</span>
        ${if (suiteFail > 0) "<span class=\"badge FAILED\">$suiteFail fail</span>" else ""}
        ${if (suiteSkip > 0) "<span class=\"badge SKIPPED\">$suiteSkip skip</span>" else ""}
        <span>${formatDuration(suiteDuration)}</span>
      </div>
    </div>
""")

                for (c in cases) {
                    val httpSteps = c.steps.filterIsInstance<EvidenceStep.HttpStep>()
                    val dbSteps = c.steps.filterIsInstance<EvidenceStep.DbStep>()
                    val mailSteps = c.steps.filterIsInstance<EvidenceStep.MailStep>()
                    val searchText = buildString {
                        append(c.className).append(' ').append(c.displayName).append(' ')
                        append(c.source.fileHeader.orEmpty()).append(' ')
                        for (h in httpSteps) append(h.method).append(' ').append(h.path).append(' ').append(h.statusCode).append(' ')
                        for (d in dbSteps) append(d.sql).append(' ')
                    }.lowercase()

                    append("""
    <details class="case" data-status="${c.status.name}" data-search="${esc(searchText)}"${if (c.status == EvidenceStatus.FAILED) " open" else ""}>
      <summary>
        <div class="case-left">
          <span class="badge ${c.status.name}">${c.status.name}</span>
          <span class="case-name">${esc(c.displayName)}</span>
        </div>
        <div class="case-meta">
          ${if (httpSteps.isNotEmpty()) "<span class=\"badge pill\">${httpSteps.size} HTTP</span>" else ""}
          ${if (dbSteps.isNotEmpty()) "<span class=\"badge pill\">${dbSteps.size} DB</span>" else ""}
          ${if (mailSteps.isNotEmpty()) "<span class=\"badge pill\">${mailSteps.size} Mail</span>" else ""}
          ${if (c.source.assertionCount > 0) "<span class=\"badge pill\">${c.source.assertionCount} asserts</span>" else ""}
          <span>${c.durationMs} ms</span>
        </div>
      </summary>
      <div class="case-body">
""")

                    if (c.status == EvidenceStatus.FAILED && (!c.reasonOrError.isNullOrBlank() || !c.stackTrace.isNullOrBlank())) {
                        append("""
        <div class="sec-title">Lỗi / Failure</div>
        <div class="err-box"><pre>${esc(c.reasonOrError ?: "")}
${esc(c.stackTrace ?: "")}</pre></div>
""")
                    } else if (c.status == EvidenceStatus.SKIPPED && !c.reasonOrError.isNullOrBlank()) {
                        append("""
        <div class="sec-title">Lý do Skip</div>
        <div class="payload-box"><pre>${esc(c.reasonOrError!!)}</pre></div>
""")
                    }

                    if (httpSteps.isNotEmpty()) {
                        append("""        <div class="sec-title">Minh chứng HTTP Calls (${httpSteps.size})</div>""")
                        for ((idx, h) in httpSteps.withIndex()) {
                            val statusCls = if (h.statusCode in 200..399) "st-ok" else "st-err"
                            append("""
        <div class="http-item">
          <div class="http-head">
            <span>#${idx + 1}</span>
            <span class="m-badge m-${esc(h.method)}">${esc(h.method)}</span>
            <span><strong>${esc(h.path)}</strong></span>
            <span class="$statusCls">HTTP ${h.statusCode}</span>
            <span>${if (h.authenticated) "Bearer Auth" else "Anonymous"}</span>
            <span>${h.durationMs} ms</span>
          </div>
""")
                            if (!h.requestSummary.isNullOrBlank() || !h.responseSummary.isNullOrBlank()) {
                                append("""          <div class="http-payloads">""")
                                if (!h.requestSummary.isNullOrBlank()) {
                                    append("""<div class="payload-box"><div class="payload-lbl">Request Body</div><pre>${esc(h.requestSummary)}</pre></div>""")
                                }
                                if (!h.responseSummary.isNullOrBlank()) {
                                    append("""<div class="payload-box"><div class="payload-lbl">Response Body</div><pre>${esc(h.responseSummary)}</pre></div>""")
                                }
                                append("""          </div>""")
                            }
                            append("""        </div>""")
                        }
                    }

                    if (dbSteps.isNotEmpty()) {
                        append("""
        <div class="sec-title">Minh chứng Database Queries (${dbSteps.size})</div>
        <table class="db-table">
          <thead><tr><th>#</th><th>SQL Query</th><th>Giá trị thực tế</th></tr></thead>
          <tbody>
""")
                        for ((idx, d) in dbSteps.withIndex()) {
                            append("""            <tr><td>${idx + 1}</td><td>${esc(d.sql)}</td><td><strong>${esc(d.result)}</strong></td></tr>""")
                        }
                        append("""          </tbody></table>""")
                    }

                    if (mailSteps.isNotEmpty()) {
                        append("""
        <div class="sec-title">Email Tokens Đã Ghi Nhận (${mailSteps.size})</div>
        <table class="db-table">
          <thead><tr><th>#</th><th>Purpose</th><th>Recipient</th></tr></thead>
          <tbody>
""")
                        for ((idx, m) in mailSteps.withIndex()) {
                            append("""            <tr><td>${idx + 1}</td><td>${esc(m.purpose)}</td><td>${esc(m.recipient)}</td></tr>""")
                        }
                        append("""          </tbody></table>""")
                    }

                    if (!c.source.methodSource.isNullOrBlank()) {
                        val loc = if (!c.source.fileRelativePath.isNullOrBlank() && c.source.startLine != null) {
                            "${c.source.fileRelativePath}:${c.source.startLine}"
                        } else ""
                        append("""
        <div class="sec-title">Mã nguồn &amp; Assertions đã thực thi ${if (loc.isNotBlank()) "(${esc(loc)})" else ""}</div>
        <div class="code-box"><pre>${esc(c.source.methodSource)}</pre></div>
""")
                    }

                    append("""
      </div>
    </details>
""")
                }
                append("""  </div>""")
            }

            append("""
</div>
<script>
let statusFilter = 'ALL';
function setStatusFilter(st, btn) {
  statusFilter = st;
  document.querySelectorAll('.toolbar .btn[data-filter]').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  applyFilters();
}
function applyFilters() {
  const q = (document.getElementById('searchBox').value || '').trim().toLowerCase();
  document.querySelectorAll('.suite').forEach(suite => {
    let visibleCases = 0;
    suite.querySelectorAll('.case').forEach(c => {
      const stOk = statusFilter === 'ALL' || c.dataset.status === statusFilter;
      const qOk = !q || (c.dataset.search || '').includes(q);
      const show = stOk && qOk;
      c.style.display = show ? '' : 'none';
      if (show) visibleCases++;
    });
    suite.style.display = visibleCases > 0 ? '' : 'none';
  });
}
function toggleAll(open) {
  document.querySelectorAll('.case').forEach(c => {
    if (c.style.display !== 'none') c.open = open;
  });
}
window.addEventListener('beforeprint', () => toggleAll(true));
</script>
</body>
</html>
""")
        }

        Files.writeString(outPath, html, StandardCharsets.UTF_8)
        println("[EVIDENCE REPORT] Generated ${outPath.toAbsolutePath()} ($total tests, $totalHttp HTTP calls, $totalDb DB checks)")
    }

    private fun esc(s: String): String = buildString(s.length) {
        for (ch in s) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }

    private fun formatDuration(ms: Long): String {
        return if (ms < 1000) "${ms}ms" else String.format("%.2fs", ms / 1000.0)
    }
}
