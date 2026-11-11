package dev.kaku.wafake.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import dev.kaku.wafake.R
import dev.kaku.wafake.util.RootShell

class LogsFragment : Fragment(R.layout.fragment_logs) {

    private lateinit var text: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        text = view.findViewById(R.id.log_text)
        view.findViewById<Button>(R.id.log_refresh).setOnClickListener { refresh() }
        refresh()
    }

    private fun refresh() {
        text.text = "читаю..."
        Thread {
            val out = RootShell.exec("logcat -d -s WAFake:V -t 400", timeoutSec = 15)
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                text.text = out.ifBlank {
                    "пусто.\n\nварианты:\n" +
                        "— модуль не активирован в LSPosed\n" +
                        "— WhatsApp не перезапускался после включения\n" +
                        "— нет root, работает только режим B (оверлей)"
                }
            }
        }.start()
    }
}
