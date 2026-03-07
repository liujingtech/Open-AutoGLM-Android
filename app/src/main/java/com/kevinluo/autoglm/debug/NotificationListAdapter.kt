package com.kevinluo.autoglm.debug

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.kevinluo.autoglm.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationListAdapter(
    private var notifications: List<MockNotification>,
    private var selectedIds: Set<String>,
    private val onItemToggle: (String, Boolean) -> Unit
) : RecyclerView.Adapter<NotificationListAdapter.ViewHolder>() {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: MaterialCheckBox = view.findViewById(R.id.checkbox)
        val appNameText: TextView = view.findViewById(R.id.appNameText)
        val timeText: TextView = view.findViewById(R.id.timeText)
        val titleText: TextView = view.findViewById(R.id.titleText)
        val contentText: TextView = view.findViewById(R.id.contentText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification_checkbox, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val notification = notifications[position]
        val isSelected = selectedIds.contains(notification.id)

        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = isSelected
        holder.appNameText.text = notification.appName
        holder.timeText.text = timeFormat.format(Date(notification.timestamp))
        holder.titleText.text = notification.title
        holder.contentText.text = notification.text

        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            onItemToggle(notification.id, isChecked)
        }

        holder.itemView.setOnClickListener {
            holder.checkbox.isChecked = !holder.checkbox.isChecked
        }
    }

    override fun getItemCount(): Int = notifications.size

    fun updateData(newNotifications: List<MockNotification>, newSelectedIds: Set<String>) {
        this.notifications = newNotifications
        this.selectedIds = newSelectedIds
        notifyDataSetChanged()
    }

    fun updateSelectedIds(newSelectedIds: Set<String>) {
        this.selectedIds = newSelectedIds
        notifyDataSetChanged()
    }
}
