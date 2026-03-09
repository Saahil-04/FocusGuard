package com.focusguard.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
 * Displays the list of monitored apps and their blockable sections.
 * Users can toggle individual sections on/off.
 * Changes are persisted immediately to SharedPreferences and reflected
 * live in the accessibility service (which reads config on each event).
 */
class BlockedSectionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockedSectionsBinding
    private val configs: MutableList<BlockedAppConfig> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockedSectionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        configs.addAll(PreferencesManager.getBlockedAppsConfig(this))
        setupRecyclerView()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun setupRecyclerView() {
        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = AppsAdapter(configs) {
            // Persist on every toggle
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
                tvKeywords.text = "Keywords: ${section.keywords.joinToString(", ")}"
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
