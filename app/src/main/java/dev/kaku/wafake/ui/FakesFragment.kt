package dev.kaku.wafake.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch
import dev.kaku.wafake.Fake
import dev.kaku.wafake.FakeStore
import dev.kaku.wafake.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class FakesFragment : Fragment(R.layout.fragment_fakes) {

    private lateinit var prefs: SharedPreferences
    private lateinit var switch: MaterialSwitch
    private lateinit var jidField: EditText
    private lateinit var textField: EditText
    private lateinit var whenButton: Button
    private lateinit var listBox: LinearLayout

    private var picked: Calendar = Calendar.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = requireContext().getSharedPreferences(FakeStore.PREFS_FILE, 0)

        switch = view.findViewById(R.id.enabled)
        jidField = view.findViewById(R.id.jid)
        textField = view.findViewById(R.id.body)
        whenButton = view.findViewById(R.id.when_btn)
        listBox = view.findViewById(R.id.list)

        switch.isChecked = prefs.getBoolean(FakeStore.KEY_ENABLED, true)
        switch.setOnCheckedChangeListener { _, v ->
            prefs.edit().putBoolean(FakeStore.KEY_ENABLED, v).commit()
            seal()
        }

        whenButton.text = label(picked.timeInMillis)
        whenButton.setOnClickListener { pickDate() }
        view.findViewById<Button>(R.id.add_btn).setOnClickListener { add() }

        render()
    }

    override fun onResume() {
        super.onResume()
        if (::listBox.isInitialized) render()
    }

    private fun label(ts: Long): String =
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(ts))

    private fun pickDate() {
        DatePickerDialog(
            requireContext(),
            { _, y, m, d ->
                picked.set(Calendar.YEAR, y)
                picked.set(Calendar.MONTH, m)
                picked.set(Calendar.DAY_OF_MONTH, d)
                pickTime()
            },
            picked.get(Calendar.YEAR),
            picked.get(Calendar.MONTH),
            picked.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun pickTime() {
        TimePickerDialog(
            requireContext(),
            { _, h, min ->
                picked.set(Calendar.HOUR_OF_DAY, h)
                picked.set(Calendar.MINUTE, min)
                picked.set(Calendar.SECOND, 0)
                whenButton.text = label(picked.timeInMillis)
            },
            picked.get(Calendar.HOUR_OF_DAY),
            picked.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun add() {
        val jid = jidField.text.toString().trim()
        val body = textField.text.toString().trim()

        if (jid.isEmpty() || body.isEmpty()) {
            Toast.makeText(requireContext(), "нужны jid и текст", Toast.LENGTH_SHORT).show()
            return
        }

        val all = read().toMutableList()
        all.add(
            Fake(
                id = UUID.randomUUID().toString(),
                jid = jid,
                text = body,
                timestamp = picked.timeInMillis,
                outgoing = true
            )
        )
        write(all)
        textField.setText("")
        render()
        Toast.makeText(requireContext(), "добавлено. открой чат", Toast.LENGTH_SHORT).show()
    }

    private fun read(): List<Fake> =
        FakeStore.decode(prefs.getString(FakeStore.KEY_DATA, null))

    private fun write(list: List<Fake>) {
        prefs.edit().putString(FakeStore.KEY_DATA, FakeStore.encode(list)).commit()
        seal()
    }

    private fun remove(f: Fake) {
        val all = read().toMutableList()
        all.removeAll { it.id == f.id }
        write(all)
        render()
    }

    private fun render() {
        listBox.removeAllViews()
        val all = read()
        if (all.isEmpty()) {
            listBox.addView(TextView(requireContext()).apply {
                text = "пусто"
                alpha = 0.5f
            })
            return
        }

        val fmt = SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault())
        all.sortedBy { it.timestamp }.forEach { f ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 12, 0, 12)
            }
            row.addView(TextView(requireContext()).apply {
                text = f.jid
                alpha = 0.6f
                textSize = 12f
            })
            row.addView(TextView(requireContext()).apply {
                text = f.text
                textSize = 15f
            })
            row.addView(TextView(requireContext()).apply {
                text = fmt.format(Date(f.timestamp))
                alpha = 0.5f
                textSize = 12f
            })
            row.addView(Button(requireContext()).apply {
                text = "удалить"
                setOnClickListener { remove(f) }
            })
            listBox.addView(row)
        }
    }

    private fun seal() {
        runCatching {
            val f = File(requireContext().dataDir, "shared_prefs/${FakeStore.PREFS_FILE}.xml")
            if (!f.exists()) return@runCatching
            ProcessBuilder("chmod", "644", f.absolutePath).redirectErrorStream(true).start().waitFor()
        }
    }
}
