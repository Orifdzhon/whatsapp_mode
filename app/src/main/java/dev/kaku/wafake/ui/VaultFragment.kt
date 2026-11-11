package dev.kaku.wafake.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import dev.kaku.wafake.R
import dev.kaku.wafake.vault.VaultDb
import java.util.Date

class VaultFragment : Fragment(R.layout.fragment_vault) {

    private lateinit var db: VaultDb
    private lateinit var box: LinearLayout
    private lateinit var filter: Button
    private lateinit var counter: TextView

    private var deletedOnly = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        db = VaultDb(requireContext().applicationContext)
        box = view.findViewById(R.id.vault_list)
        filter = view.findViewById(R.id.vault_filter)
        counter = view.findViewById(R.id.vault_counter)

        filter.setOnClickListener {
            deletedOnly = !deletedOnly
            render()
        }

        view.findViewById<Button>(R.id.vault_wipe).setOnClickListener {
            db.wipe()
            render()
        }

        render()
    }

    override fun onResume() {
        super.onResume()
        if (::box.isInitialized) render()
    }

    private fun render() {
        box.removeAllViews()
        filter.text = if (deletedOnly) "показать все" else "только удалённые"

        counter.text = "всего ${db.count(false)} · удалено ${db.count(true)}"

        val rows = db.all(deletedOnly)
        if (rows.isEmpty()) {
            box.addView(TextView(requireContext()).apply {
                text = if (deletedOnly) "удалённых пока нет" else "архив пуст"
                alpha = 0.5f
                setPadding(0, 24, 0, 0)
            })
            return
        }

        for (r in rows) box.addView(card(r))
    }

    private fun card(r: VaultDb.Row): View {
        val wrap = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 8)
        }

        val head = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }

        head.addView(TextView(requireContext()).apply {
            text = if (r.deleted) "УДАЛЕНО" else if (r.fromMe) "исходящее" else "входящее"
            setTextColor(if (r.deleted) 0xFFC62828.toInt() else 0xFF2E7D32.toInt())
            textSize = 11f
            setPadding(0, 6, 8, 0)
        })

        head.addView(TextView(requireContext()).apply {
            text = DateFormat.format("dd.MM.yyyy HH:mm:ss", Date(r.ts)).toString()
            alpha = 0.55f
            textSize = 11f
            setPadding(0, 6, 0, 0)
        })

        wrap.addView(head)
        wrap.addView(TextView(requireContext()).apply {
            text = r.jid
            alpha = 0.45f
            textSize = 11f
        })
        wrap.addView(TextView(requireContext()).apply {
            text = r.body.ifEmpty { label(r.mediaType) }
            textSize = 15f
            setTextIsSelectable(true)
        })

        if (r.mediaUri.isNotEmpty()) {
            wrap.addView(Button(requireContext()).apply {
                text = "открыть медиа"
                setOnClickListener { openMedia(r.mediaUri) }
            })
        }

        wrap.addView(Button(requireContext()).apply {
            text = "удалить из архива"
            setOnClickListener {
                db.remove(r.keyId)
                render()
            }
        })

        wrap.addView(View(requireContext()).apply {
            setBackgroundColor(0x22000000)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        })

        return wrap
    }

    private fun label(type: Int): String = when (type) {
        1 -> "[изображение]"
        2 -> "[аудио]"
        3 -> "[видео]"
        4 -> "[контакт]"
        5 -> "[стикер]"
        9 -> "[документ]"
        13 -> "[GIF]"
        else -> "[медиа]"
    }

    private fun openMedia(uriString: String) {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(requireContext(), "нет приложения для файла", Toast.LENGTH_SHORT).show() }
    }
}
