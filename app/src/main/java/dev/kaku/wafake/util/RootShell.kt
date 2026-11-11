package dev.kaku.wafake.util

import java.io.File

/**
 * Минимальный root-shell без внешних зависимостей.
 * CI-сборка не тянет jitpack, jar не нужен.
 */
object RootShell {

    fun exec(cmd: String, timeoutSec: Long = 12): String = runCatching {
        val pb = ProcessBuilder("su", "-c", cmd)
        pb.redirectErrorStream(true)
        val p = pb.start()

        val out = StringBuilder()
        val t = Thread {
            runCatching {
                p.inputStream.bufferedReader().use { r ->
                    out.append(r.readText())
                }
            }
        }
        t.start()
        p.waitFor()
        t.join(timeoutSec * 1000)
        if (t.isAlive) runCatching { p.destroy() }
        out.toString().trim()
    }.getOrDefault("")

    fun hasRoot(): Boolean {
        val id = exec("id")
        return id.contains("uid=0")
    }

    fun fileExecutable(path: String): Boolean = File(path).exists()
}
