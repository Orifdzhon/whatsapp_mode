package dev.kaku.wafake.xposed

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.kaku.wafake.Fake

/**
 * Wraps WhatsApp's own message adapter and appends synthetic rows.
 * The host adapter never learns we exist — every call for a real position
 * is forwarded untouched, so no rendering path changes for genuine messages.
 */
class FakeAdapterProxy(
    private val inner: RecyclerView.Adapter<RecyclerView.ViewHolder>,
    fakes: List<Fake>,
    private val onDelete: (Fake) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val fakes = fakes.toMutableList()

    companion object {
        const val TYPE_FAKE = 0x7FFF0001
    }

    private val observer = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() = notifyDataSetChanged()
        override fun onItemRangeChanged(p: Int, c: Int) = notifyItemRangeChanged(p, c)
        override fun onItemRangeChanged(p: Int, c: Int, payload: Any?) = notifyItemRangeChanged(p, c, payload)
        override fun onItemRangeInserted(p: Int, c: Int) = notifyItemRangeInserted(p, c)
        override fun onItemRangeRemoved(p: Int, c: Int) = notifyItemRangeRemoved(p, c)
        override fun onItemRangeMoved(from: Int, to: Int, count: Int) = notifyItemMoved(from, to)
    }

    init {
        inner.registerAdapterDataObserver(observer)
    }

    override fun getItemCount(): Int = inner.itemCount + fakes.size

    override fun getItemViewType(position: Int): Int =
        if (position >= inner.itemCount) TYPE_FAKE else inner.getItemViewType(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == TYPE_FAKE) FakeHolder(FakeBubble.create(parent.context))
        else inner.onCreateViewHolder(parent, viewType)

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (position >= inner.itemCount) {
            val idx = position - inner.itemCount
            val f = fakes[idx]
            (holder as FakeHolder).bind(f)
            holder.itemView.setOnLongClickListener {
                fakes.removeAt(idx)
                notifyItemRemoved(position)
                notifyItemRangeChanged(position, fakes.size - idx)
                onDelete(f)
                true
            }
        } else {
            holder.itemView.setOnLongClickListener(null)
            inner.onBindViewHolder(holder, position)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (position >= inner.itemCount) onBindViewHolder(holder, position)
        else inner.onBindViewHolder(holder, position, payloads)
    }

    override fun getItemId(position: Int): Long =
        if (position >= inner.itemCount) -(position.toLong() + 1L) else inner.getItemId(position)

    override fun hasStableIds(): Boolean = inner.hasStableIds()

    fun detach() = runCatching { inner.unregisterAdapterDataObserver(observer) }

    class FakeHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        fun bind(f: Fake) = FakeBubble.bind(itemView as android.widget.LinearLayout, f.text, f.timestamp)
    }
}
