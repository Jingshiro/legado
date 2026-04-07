package io.legado.app.ui.book.read.ai

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.databinding.ItemAiChatBinding
import io.legado.app.help.config.AiConfig
import io.legado.app.help.glide.ImageLoader
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.gone
import io.legado.app.utils.visible
import io.noties.markwon.Markwon
import java.net.URI

class ChatAdapter : ListAdapter<ChatMessage, ChatAdapter.ChatViewHolder>(DIFF_CALLBACK) {

    private var markwon: Markwon? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemAiChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val msg = getItem(position)
        val context = holder.binding.root.context
        
        if (markwon == null) {
            markwon = Markwon.create(context)
        }
        
        if (msg.role == "user") {
            holder.binding.llUserMsg.visible()
            holder.binding.llAiMsg.gone()
            markwon?.setMarkdown(holder.binding.tvUserContent, msg.content ?: "")
            if (AiConfig.userAvatar.isNotBlank()) {
                ImageViewCompat.setImageTintList(holder.binding.ivUserAvatar, null)
                ImageLoader.load(context, encodeAvatarUrl(AiConfig.userAvatar)).into(holder.binding.ivUserAvatar)
            } else {
                ImageViewCompat.setImageTintList(
                    holder.binding.ivUserAvatar,
                    ColorStateList.valueOf(ThemeStore.primaryColor(context))
                )
                holder.binding.ivUserAvatar.setImageResource(R.drawable.ic_person)
            }
        } else {
            holder.binding.llAiMsg.visible()
            holder.binding.llUserMsg.gone()
            markwon?.setMarkdown(holder.binding.tvAiContent, msg.content ?: "")
            if (AiConfig.aiAvatar.isNotBlank()) {
                ImageViewCompat.setImageTintList(holder.binding.ivAiAvatar, null)
                ImageLoader.load(context, encodeAvatarUrl(AiConfig.aiAvatar)).into(holder.binding.ivAiAvatar)
            } else {
                ImageViewCompat.setImageTintList(
                    holder.binding.ivAiAvatar,
                    ColorStateList.valueOf(ThemeStore.primaryColor(context))
                )
                holder.binding.ivAiAvatar.setImageResource(R.drawable.ic_chat_ai)
            }
        }
    }

    class ChatViewHolder(val binding: ItemAiChatBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        /**
         * 将含中文、全角符号等非 ASCII 字符的 URL 进行 percent-encoding，
         * 确保 Glide/OkHttp 能正常解析和请求。
         */
        fun encodeAvatarUrl(url: String): String {
            return try {
                val uri = URI(url)
                // 利用 URI 的多参数构造器对各部分单独编码，再转回 ASCII-safe 字符串
                URI(
                    uri.scheme,
                    uri.userInfo,
                    uri.host,
                    uri.port,
                    uri.path,
                    uri.query,
                    uri.fragment
                ).toASCIIString()
            } catch (e: Exception) {
                url // 编码失败则原样返回，由 Glide 尝试处理
            }
        }

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(old: ChatMessage, new: ChatMessage): Boolean {
                // 同一位置、同一角色的消息认为是同一条
                return old.role == new.role && old.content == new.content
            }

            override fun areContentsTheSame(old: ChatMessage, new: ChatMessage): Boolean {
                return old == new
            }
        }
    }
}
