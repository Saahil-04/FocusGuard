package com.focusguard.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.focusguard.app.R
import com.focusguard.app.config.BlockedAppConfig
import com.focusguard.app.config.BlockedSection
import com.focusguard.app.databinding.ActivityBlockedSectionsBinding
import com.focusguard.app.databinding.ItemBlockedSectionBinding
import com.focusguard.app.utils.PreferencesManager

/**
 * BlockedSectionsActivity
 *
 * Redesigned with:
 *  - Custom top bar (no ActionBar) with back button
 *  - Edge-to-edge layout
 *  - M3 card-based list for apps and sections
 *  - Immediate persistence on every toggle
 */
class BlockedSectionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockedSectionsBinding
    private val configs: MutableList<BlockedAppConfig> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityBlockedSectionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // No ActionBar — we use our custom top bar
        supportActionBar?.hide()

        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        configs.addAll(PreferencesManager.getBlockedAppsConfig(this))
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = AppsAdapter(configs) {
            PreferencesManager.saveBlockedAppsConfig(this, configs)
        }
    }

    // ─── Apps Adapter ─────────────────────────────────────────────────────────

    class AppsAdapter(
        private val configs: List<BlockedAppConfig>,
        private val onChanged: () -> Unit
    ) : RecyclerView.Adapter<AppsAdapter.AppViewHolder>() {

        inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvAppName: TextView = itemView.findViewById(R.id.tvAppName)
            val tvPackageName: TextView = itemView.findViewById(R.id.tvPackageName)
            val rvSections: RecyclerView = itemView.findViewById(R.id.rvSections)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_config, parent, false)
            return AppViewHolder(view)
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val config = configs[position]
            holder.tvAppName.text = config.appLabel
            holder.tvPackageName.text = config.packageName
            holder.rvSections.layoutManager = LinearLayoutManager(holder.itemView.context)
            holder.rvSections.adapter = SectionsAdapter(config.sections, onChanged)
            holder.rvSections.isNestedScrollingEnabled = false
        }

        override fun getItemCount() = configs.size
    }

    // ─── Sections Adapter ─────────────────────────────────────────────────────

    class SectionsAdapter(
        private val sections: List<BlockedSection>,
        private val onChanged: () -> Unit
    ) : RecyclerView.Adapter<SectionsAdapter.SectionViewHolder>() {

        inner class SectionViewHolder(val binding: ItemBlockedSectionBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SectionViewHolder {
            val binding = ItemBlockedSectionBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return SectionViewHolder(binding)
        }

        override fun onBindViewHolder(holder: SectionViewHolder, position: Int) {
            val section = sections[position]
            with(holder.binding) {
                tvSectionName.text = section.label
                // Prefix with "Triggers: " for clarity
                tvKeywords.text = holder.itemView.context
                    .getString(R.string.sections_keywords_prefix) +
                        section.keywords.joinToString(", ")
                switchSection.isChecked = section.isEnabled
                switchSection.setOnCheckedChangeListener { _, checked ->
                    section.isEnabled = checked
                    onChanged()
                }
            }
        }

        override fun getItemCount() = sections.size
    }
}
