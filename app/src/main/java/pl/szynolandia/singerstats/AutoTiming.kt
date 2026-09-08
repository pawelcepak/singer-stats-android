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
                withContext(Dispatchers.Main) { listener.onStatus("Analizuję śpiew i dopasowuję tekst…") }
                val whisperModel = Whisper.loadModel(context, model.absolutePath)
                val threads = Runtime.getRuntime().availableProcessors().coerceIn(4, 8)
                val result = Whisper.transcribe(
                    whisperModel,
                    project.audioPath!!,
                    WhisperConfig(language = "pl", translate = false, threads = threads, maxSegmentLength = 80, printTimestamps = true)
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
                if (words.isEmpty()) throw IllegalStateException("Whisper nie rozpoznał słów w tym utworze.")

                var cursor = 0
                var scoreSum = 0.0
                var matched = 0
                project.lyrics.forEachIndexed { index, line ->
                    val target = lyricTokens(line.text)
                    if (target.isEmpty()) return@forEachIndexed
                    val best = findBest(words, target, cursor)
                    if (best != null) {
                        line.start = words[best.first].startMs / 1000.0
                        line.end = words[best.second].endMs / 1000.0
                        cursor = max(cursor, best.second + 1)
                        scoreSum += best.third
                        matched++
                    }
                    withContext(Dispatchers.Main) { listener.onProgress(((index + 1) * 100) / project.lyrics.size) }
                }
                val conf = if (matched == 0) 0.0 else scoreSum / matched
                withContext(Dispatchers.Main) { listener.onDone(conf) }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { listener.onError(t.javaClass.simpleName + ": " + (t.message ?: "nieznany błąd")) }
            }
        }
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
        conn.setRequestProperty("User-Agent", "SingerStatsVisualizer/1.5")
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
        val x = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return x.replace(Regex("[^a-z0-9ąćęłńóśźż]"), "")
    }

    private fun findBest(words: List<TimedWord>, target: List<String>, cursor: Int): Triple<Int, Int, Double>? {
        if (cursor >= words.size) return null
        val targetN = target.size
        var bestStart = cursor
        var bestEnd = min(words.lastIndex, cursor + max(1, targetN) - 1)
        var bestScore = -1.0
        val startMax = min(words.lastIndex, cursor + max(35, targetN * 3))
        for (s in cursor..startMax) {
            val minLen = max(1, targetN - max(3, targetN / 2))
            val maxLen = min(words.size - s, targetN + max(5, targetN / 2))
            for (len in minLen..maxLen) {
                val candidate = ArrayList<String>(len)
                for (i in 0 until len) candidate.add(words[s + i].text)
                val sc = similarity(target, candidate) - (s - cursor) * 0.002
                if (sc > bestScore) {
                    bestScore = sc
                    bestStart = s
                    bestEnd = s + len - 1
                }
            }
        }
        return Triple(bestStart, bestEnd, bestScore.coerceIn(0.0, 1.0))
    }

    private fun similarity(a: List<String>, b: List<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in 0..a.size) dp[i][0] = i
        for (j in 0..b.size) dp[0][j] = j
        for (i in 1..a.size) for (j in 1..b.size) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
        }
        return 1.0 - dp[a.size][b.size].toDouble() / max(a.size, b.size).toDouble()
    }
}
