package pl.szynolandia.singerstats

import android.content.Context
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object AutoTiming {
    private const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
    private const val MODEL_NAME = "ggml-base.bin"

    interface Listener {
        fun onStatus(message: String)
        fun onProgress(percent: Int)
        fun onDone(confidence: Double)
        fun onError(message: String)
    }

    private data class TimedWord(val text: String, val startMs: Long, val endMs: Long)
    private data class Match(val startWord: Int, val endWord: Int, val score: Double)
    private data class Anchor(val line: Int, val startMs: Long, val endMs: Long, val score: Double, val forced: Boolean = false)

    @JvmStatic
    fun run(context: Context, project: ProjectData, listener: Listener) {
        if (project.audioPath.isNullOrBlank() || !File(project.audioPath!!).exists()) {
            listener.onError("Najpierw wybierz plik audio.")
            return
        }
        if (project.lyrics.isEmpty()) {
            listener.onError("Najpierw wygeneruj fragmenty tekstu po kropkach.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val model = ensureModel(context, listener)
                withContext(Dispatchers.Main) {
                    listener.onStatus("Analizuję wokal. Szukam kotwic i układam wszystkie fragmenty po kolei…")
                }

                val whisperModel = Whisper.loadModel(context, model.absolutePath)
                val threads = Runtime.getRuntime().availableProcessors().coerceIn(4, 8)
                val result = Whisper.transcribe(
                    whisperModel,
                    project.audioPath!!,
                    WhisperConfig(
                        language = "pl",
                        translate = false,
                        threads = threads,
                        maxSegmentLength = 70,
                        printTimestamps = true
                    )
                )
                Whisper.releaseModel(whisperModel)

                val words = ArrayList<TimedWord>()
                for (seg in result.segments) {
                    val raw = seg.text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                    if (raw.isEmpty()) continue
                    val dur = max(1L, seg.endMs - seg.startMs)
                    raw.forEachIndexed { i, token ->
                        val s = seg.startMs + dur * i / raw.size
                        val e = seg.startMs + dur * (i + 1) / raw.size
                        val n = normToken(token)
                        if (n.isNotBlank()) words.add(TimedWord(n, s, e))
                    }
                }
                if (words.isEmpty()) throw IllegalStateException("Model nie rozpoznał żadnych słów w tym utworze.")

                val lineTokens = project.lyrics.map { lyricTokens(it.text) }
                val allOldFilled = project.lyrics.all { it.start != null && it.end != null }
                val firstOld = project.lyrics.firstOrNull()?.start
                val firstRecognizedSec = words.first().startMs / 1000.0

                // A manually corrected first START is the strongest anchor. If the whole timeline was
                // previously auto-filled and starts absurdly late, do not blindly trust that old result.
                val anchorSec = when {
                    firstOld != null && firstOld >= 0.0 && (!allOldFilled || firstOld <= 20.0) -> firstOld
                    firstRecognizedSec <= 18.0 -> firstRecognizedSec
                    else -> 8.0
                }
                val anchorMs = (anchorSec * 1000.0).toLong()

                val anchors = ArrayList<Anchor>()
                anchors.add(Anchor(0, anchorMs, anchorMs, 1.0, forced = true))

                var expectedMs = anchorMs
                var minWordIndex = words.indexOfFirst { it.endMs >= anchorMs }.let { if (it < 0) 0 else it }
                var directMatches = 0
                var directScore = 0.0

                for (i in 1 until project.lyrics.size) {
                    val target = lineTokens[i]
                    if (target.isEmpty()) continue

                    expectedMs += estimatedDurationMs(target) + 80L
                    val best = findBestNear(words, target, minWordIndex, expectedMs)
                    if (best != null && best.score >= 0.28) {
                        val startMs = words[best.startWord].startMs
                        val endMs = words[best.endWord].endMs

                        // Prevent one bad Whisper guess from jumping tens of seconds ahead while several
                        // lyric fragments are still waiting to be placed.
                        val maxReasonableJump = 24_000L + estimatedDurationMs(target) * 2
                        if (startMs <= expectedMs + maxReasonableJump) {
                            anchors.add(Anchor(i, startMs, endMs, best.score))
                            minWordIndex = best.endWord + 1
                            expectedMs = endMs
                            directMatches++
                            directScore += best.score
                        }
                    }
                    withContext(Dispatchers.Main) {
                        listener.onProgress(((i + 1) * 70) / project.lyrics.size)
                    }
                }

                // Keep only chronologically valid anchors. Repeated choruses often tempt speech models
                // into matching the right words at the wrong occurrence.
                val cleanAnchors = ArrayList<Anchor>()
                var lastLine = -1
                var lastTime = -1L
                for (a in anchors.sortedBy { it.line }) {
                    if (a.line > lastLine && a.startMs >= lastTime) {
                        cleanAnchors.add(a)
                        lastLine = a.line
                        lastTime = max(a.startMs, a.endMs)
                    }
                }

                // Fill every line between reliable anchors. Unknown lines are never skipped anymore.
                for (aIndex in cleanAnchors.indices) {
                    val a = cleanAnchors[aIndex]
                    val next = cleanAnchors.getOrNull(aIndex + 1)
                    if (!a.forced) {
                        project.lyrics[a.line].start = a.startMs / 1000.0
                        project.lyrics[a.line].end = max(a.startMs + 250L, a.endMs) / 1000.0
                    }

                    val fromLine = if (a.forced) a.line else a.line + 1
                    val toLineExclusive = next?.line ?: project.lyrics.size
                    if (fromLine >= toLineExclusive) continue

                    val regionStart = if (a.forced) a.startMs else max(a.endMs + 60L, a.startMs)
                    val weights = ArrayList<Long>()
                    var totalWeight = 0L
                    for (line in fromLine until toLineExclusive) {
                        val w = estimatedDurationMs(lineTokens[line]).coerceAtLeast(850L)
                        weights.add(w)
                        totalWeight += w
                    }

                    val naturalEnd = regionStart + totalWeight + max(0, weights.size - 1) * 70L
                    val regionEnd = if (next != null) {
                        max(regionStart + weights.size * 450L, next.startMs - 80L)
                    } else {
                        val songEnd = if (project.songDuration > 0.0) (project.songDuration * 1000.0).toLong() else words.last().endMs
                        min(songEnd, max(naturalEnd, words.last().endMs))
                    }
                    val available = max(600L * weights.size, regionEnd - regionStart)
                    var cursor = regionStart

                    for ((offset, line) in (fromLine until toLineExclusive).withIndex()) {
                        val proportion = if (totalWeight <= 0L) 1.0 / weights.size else weights[offset].toDouble() / totalWeight.toDouble()
                        val dur = max(500L, (available * proportion).toLong() - 55L)
                        project.lyrics[line].start = cursor / 1000.0
                        project.lyrics[line].end = (cursor + dur) / 1000.0
                        cursor += dur + 55L
                    }
                }

                // If the first line was forced and the loop above did not give it an end, ensure it has one.
                if (project.lyrics[0].start == null) project.lyrics[0].start = anchorMs / 1000.0
                if (project.lyrics[0].end == null || project.lyrics[0].end!! <= project.lyrics[0].start!!) {
                    val nextStart = project.lyrics.getOrNull(1)?.start
                    project.lyrics[0].end = if (nextStart != null && nextStart > project.lyrics[0].start!!) {
                        max(project.lyrics[0].start!! + 0.5, nextStart - 0.055)
                    } else {
                        project.lyrics[0].start!! + estimatedDurationMs(lineTokens[0]) / 1000.0
                    }
                }

                // Final monotonic safety pass: no overlaps caused by noisy anchors.
                var lastEnd = anchorMs / 1000.0
                for (line in project.lyrics) {
                    var s = line.start ?: lastEnd
                    var e = line.end ?: (s + 1.0)
                    if (s < lastEnd - 0.02) s = lastEnd + 0.02
                    if (e <= s + 0.20) e = s + 0.55
                    line.start = s
                    line.end = e
                    lastEnd = e
                }

                val conf = if (directMatches == 0) 0.0 else (directScore / directMatches) * (directMatches.toDouble() / max(1, project.lyrics.size - 1))
                withContext(Dispatchers.Main) {
                    listener.onProgress(100)
                    listener.onStatus(
                        "Gotowe. Bezpośrednio rozpoznano $directMatches/${project.lyrics.size} fragmentów; resztę ułożono sekwencyjnie od ${String.format("%.3f", anchorSec)} s."
                    )
                    listener.onDone(conf.coerceIn(0.0, 1.0))
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    listener.onError(t.javaClass.simpleName + ": " + (t.message ?: "nieznany błąd"))
                }
            }
        }
    }

    private fun estimatedDurationMs(tokens: List<String>): Long {
        if (tokens.isEmpty()) return 1200L
        // Rap is commonly 2.3–3.2 words/s. This is only a fallback between AI anchors.
        return (tokens.size * 390L).coerceIn(900L, 6500L)
    }

    private fun findBestNear(words: List<TimedWord>, target: List<String>, minIndex: Int, expectedMs: Long): Match? {
        if (minIndex >= words.size || target.isEmpty()) return null
        val startIndex = max(0, minIndex)
        var best: Match? = null
        val searchEndMs = expectedMs + 28_000L
        var s = startIndex
        while (s < words.size && words[s].startMs <= searchEndMs) {
            if (words[s].endMs < expectedMs - 4_000L) { s++; continue }
            val targetN = target.size
            val minLen = max(1, targetN - max(3, targetN / 2))
            val maxLen = min(words.size - s, targetN + max(5, targetN / 2))
            for (len in minLen..maxLen) {
                val end = s + len - 1
                val candidate = ArrayList<String>(len)
                for (i in s..end) candidate.add(words[i].text)
                val lexical = similarity(target, candidate)
                val timePenalty = min(0.18, abs(words[s].startMs - expectedMs).toDouble() / 90_000.0)
                val score = lexical - timePenalty
                if (best == null || score > best!!.score) best = Match(s, end, score)
            }
            s++
        }
        return best
    }

    private suspend fun ensureModel(context: Context, listener: Listener): File {
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        val model = File(dir, MODEL_NAME)
        if (model.exists() && model.length() > 100_000_000L) return model
        withContext(Dispatchers.Main) { listener.onStatus("Pierwsze użycie: pobieram model AI ~142 MB. Potem działa offline.") }
        val tmp = File(dir, "$MODEL_NAME.part")
        val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("User-Agent", "SingerStatsVisualizer/1.6")
        conn.connect()
        if (conn.responseCode !in 200..299) throw IllegalStateException("Nie udało się pobrać modelu: HTTP ${conn.responseCode}")
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            FileOutputStream(tmp).use { output ->
                val buf = ByteArray(1024 * 1024)
                var read: Int
                var done = 0L
                var last = -1
                while (input.read(buf).also { read = it } > 0) {
                    output.write(buf, 0, read)
                    done += read
                    if (total > 0) {
                        val p = ((done * 100) / total).toInt()
                        if (p != last) {
                            last = p
                            withContext(Dispatchers.Main) { listener.onProgress(p) }
                        }
                    }
                }
            }
        }
        if (!tmp.renameTo(model)) {
            tmp.copyTo(model, overwrite = true)
            tmp.delete()
        }
        return model
    }

    private fun lyricTokens(text: String): List<String> = text
        .replace(Regex("^\\s*\\([^)]*\\)\\s*"), "")
        .split(Regex("\\s+"))
        .map { normToken(it) }
        .filter { it.isNotBlank() }

    private fun normToken(s: String): String {
        val x = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return x.replace(Regex("[^a-z0-9]"), "")
    }

    private fun similarity(a: List<String>, b: List<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in 0..a.size) dp[i][0] = i
        for (j in 0..b.size) dp[0][j] = j
        for (i in 1..a.size) for (j in 1..b.size) {
            val cost = tokenCost(a[i - 1], b[j - 1])
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + cost
            )
        }
        return 1.0 - dp[a.size][b.size].toDouble() / max(a.size, b.size).toDouble()
    }

    private fun tokenCost(a: String, b: String): Int {
        if (a == b) return 0
        if (a.length >= 4 && b.length >= 4 && (a.startsWith(b.take(4)) || b.startsWith(a.take(4)))) return 0
        return 1
    }
}
