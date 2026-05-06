package io.legado.app.ui.config

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppConst
import io.legado.app.databinding.ItemCloudBackupBinding
import io.legado.app.lib.webdav.WebDavFile
import io.legado.app.utils.ConvertUtils


class CloudBackupAdapter(context: Context, val callBack: CallBack) :
    RecyclerAdapter<WebDavFile, ItemCloudBackupBinding>(context) {

    var selected = hashSetOf<WebDavFile>()

    override fun getViewBinding(parent: ViewGroup): ItemCloudBackupBinding {
        return ItemCloudBackupBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemCloudBackupBinding,
        item: WebDavFile,
        payloads: MutableList<Any>
    ) {
        binding.run {
            if (payloads.isEmpty()) {
                tvName.text = item.displayName
                tvSize.text = ConvertUtils.formatFileSize(item.size)
                tvDate.text = AppConst.dateFormat.format(item.lastModify)
            }
            cbSelect.isChecked = selected.contains(item)
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemCloudBackupBinding) {
        holder.itemView.setOnClickListener {
            getItem(holder.layoutPosition)?.let { item ->
                if (!selected.contains(item)) {
                    selected.add(item)
                } else {
                    selected.remove(item)
                }
                notifyItemChanged(holder.layoutPosition, true)
                callBack.upCountView()
            }
        }
        holder.itemView.setOnLongClickListener {
            getItem(holder.layoutPosition)?.let { item ->
                callBack.onItemLongClick(item)
            }
            true
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun selectAll(selectAll: Boolean) {
        if (selectAll) {
            getItems().forEach { selected.add(it) }
        } else {
            selected.clear()
        }
        notifyDataSetChanged()
        callBack.upCountView()
    }

    fun revertSelection() {
        getItems().forEach {
            if (selected.contains(it)) {
                selected.remove(it)
            } else {
                selected.add(it)
            }
        }
        notifyItemRangeChanged(0, itemCount, true)
        callBack.upCountView()
    }

    interface CallBack {
        fun upCountView()
        fun onItemLongClick(item: WebDavFile)
    }

}
