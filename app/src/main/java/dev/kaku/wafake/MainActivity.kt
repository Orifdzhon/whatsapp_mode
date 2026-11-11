package dev.kaku.wafake

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import dev.kaku.wafake.ui.FakesFragment
import dev.kaku.wafake.ui.LogsFragment
import dev.kaku.wafake.ui.SetupFragment
import dev.kaku.wafake.ui.VaultFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val pager = findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.pager)
        val tabs = findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabs)

        val pages = listOf(
            "Сообщения" to { FakesFragment() },
            "Архив" to { VaultFragment() },
            "Настройка" to { SetupFragment() },
            "Логи" to { LogsFragment() }
        )

        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = pages.size
            override fun createFragment(position: Int): Fragment = pages[position].second()
        }

        TabLayoutMediator(tabs, pager) { tab, position ->
            tab.text = pages[position].first
        }.attach()
    }
}
