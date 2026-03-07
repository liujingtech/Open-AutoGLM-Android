package com.kevinluo.autoglm.debug

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kevinluo.autoglm.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DebugHistoryAdapter(
    private var history: List<DebugTestHistory>,
    private val onItemClick: (DebugTestHistory) -> Unit,
    private val onDeleteClick: (DebugTestHistory) -> Unit
) : RecyclerView.Adapter<DebugHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val templateNameText: TextView = view.findViewById(R.id.templateNameText)
        val timestampText: TextView = view.findViewById(R.id.timestampText)
        val previewText: TextView = view.findViewById(R.id.previewText)
        val statusText: TextView = view.findViewById(R.id.statusText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_debug_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = history[position]

        holder.templateNameText.text = item.promptTemplateName
        holder.timestampText.text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.timestamp))
        holder.previewText.text = if (item.modelResponse.length > 100) {
            item.modelResponse.substring(0, 100) + "..."
        } else {
            item.modelResponse
        }
        holder.statusText.text = if (item.success) "成功" else "失败"
        holder.statusText.setTextColor(
            holder.itemView.context.getColor(if (item.success) R.color.primary else R.color.error)
        )

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = history.size

    fun updateData(newHistory: List<DebugTestHistory>) {
        this.history = newHistory
        notifyDataSetChanged()
    }
}
