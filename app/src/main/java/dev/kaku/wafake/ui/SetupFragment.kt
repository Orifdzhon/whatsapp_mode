package dev.kaku.wafake.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.topjohnwu.superuser.Shell
import dev.kaku.wafake.R
import dev.kaku.wafake.overlay.FakeOverlayService

class SetupFragment : Fragment(R.layout.fragment_setup) {

    private lateinit var rootStatus: TextView
    private lateinit var lsposedStatus: TextView
    private lateinit var scopeStatus: TextView
    private lateinit var a11yStatus: TextView
    private lateinit var overlaySwitch: MaterialSwitch

    private val prefs by lazy { requireContext().getSharedPreferences("wafake_config", 0) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        rootStatus = view.findViewById(R.id.status_root)
        lsposedStatus = view.findViewById(R.id.status_lsposed)
        scopeStatus = view.findViewById(R.id.status_scope)
        a11yStatus = view.findViewById(R.id.status_a11y)
        overlaySwitch = view.findViewById(R.id.sw_overlay)

        view.findViewById<Button>(R.id.btn_check).setOnClickListener { check() }
        view.findViewById<Button>(R.id.btn_enable).setOnClickListener { enableModule() }
        view.findViewById<Button>(R.id.btn_restart_wa).setOnClickListener { restartWhatsApp() }
        view.findViewById<Button>(R.id.btn_a11y).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        overlaySwitch.isChecked = prefs.getBoolean(KEY_OVERLAY, true)
        overlaySwitch.setOnCheckedChangeListener { _, v ->
            prefs.edit().putBoolean(KEY_OVERLAY, v).commit()
            // сам сервис запускается системой; тумблер только разрешает отрисовку
        }

        check()
    }

    override fun onResume() {
        super.onResume()
        if (::a11yStatus.isInitialized) check()
    }

    private fun check() {
        val root = hasRoot()
        rootStatus.text = if (root) "root: есть" else "root: нет (доступен только режим B)"

        val lsposed = hasLsposed()
        lsposedStatus.text = if (lsposed) "LSPosed: установлен" else "LSPosed: не найден"

        scopeStatus.text = when {
            !root -> "scope com.whatsapp: нужен root"
            else -> "scope com.whatsapp: " + scopeState()
        }

        val a11y = isAccessibilityEnabled()
        a11yStatus.text = if (a11y) "спец. возможности: включены" else "спец. возможности: выключены"
    }

    private fun hasRoot(): Boolean = runCatching {
        Shell.getShell()
        Shell.isAppGrantedRoot() == true
    }.getOrDefault(false)

    private fun hasLsposed(): Boolean = runCatching {
        requireContext().packageManager.getPackageInfo("org.lsposed.manager", 0)
        true
    }.getOrDefault(false)

    private fun sh(cmd: String): String = runCatching {
        val r = Shell.cmd(cmd).exec()
        r.out.joinToString("\n")
    }.getOrDefault("")

    private fun sql(sql: String): String {
        val magisk = sh("magisk --sqlite \"$sql\"")
        if (magisk.isNotBlank()) return magisk
        return sh("sqlite3 /data/adb/lspd/config/modules_config.db \"$sql\"")
    }

    private fun moduleId(): String {
        val q = "SELECT mid FROM modules WHERE module_pkg_name='dev.kaku.wafake'"
        return sql(q).trim().lines().lastOrNull { it.isNotBlank() }?.trim().orEmpty()
    }

    private fun scopeState(): String {
        val mid = moduleId()
        if (mid.isEmpty()) return "модуль ещё не виден LSPosed — открой LSPosed один раз"
        val rows = sql("SELECT app_pkg_name FROM scope WHERE mid='$mid'")
        return if (rows.contains("com.whatsapp")) "включён" else "не включён"
    }

    private fun enableModule() {
        if (!hasRoot()) {
            toast("нужен root — режим A недоступен")
            return
        }

        val mid = moduleId()
        if (mid.isEmpty()) {
            toast("открой LSPosed один раз, модуль появится в списке")
            return
        }

        sql("UPDATE modules SET enabled=1 WHERE mid='$mid'")
        sql("INSERT OR IGNORE INTO scope(mid, app_pkg_name, user_id) VALUES('$mid','com.whatsapp',0)")

        check()
        toast("модуль включён. перезапусти WhatsApp")
    }

    private fun restartWhatsApp() {
        Thread {
            sh("am force-stop com.whatsapp")
            activity?.runOnUiThread { toast("WhatsApp остановлен. открой его заново") }
        }.start()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val ctx = requireContext()
        val expected = "${ctx.packageName}/${FakeOverlayService::class.java.name}"
        val enabled = Settings.Secure.getString(
            ctx.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    companion object {
        const val KEY_OVERLAY = "overlay_enabled"
    }
}
