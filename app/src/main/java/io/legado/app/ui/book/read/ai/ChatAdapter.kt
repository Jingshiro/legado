package io.legado.app.ui.book.read.ai

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
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

class ChatAdapter(
    private val onDeleteMessage: (position: Int) -> Unit
) : ListAdapter<ChatMessage, ChatAdapter.ChatViewHolder>(DIFF_CALLBACK) {

    private var markwon: Markwon? = null

    /** 当前展开删除按钮的 ViewHolder */
    private var expandedHolder: ChatViewHolder? = null

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

        // 每次绑定时隐藏删除按钮（防止复用残留）
        holder.binding.ivDeleteAi.gone()
        holder.binding.ivDeleteUser.gone()

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

            // 长按用户气泡显示删除按钮
            holder.binding.tvUserContent.setOnLongClickListener {
                toggleDeleteButton(holder, isUser = true)
                true
            }

            // 点击删除按钮
            holder.binding.ivDeleteUser.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_ID.toInt()) {
                    collapseDeleteButton(holder)
                    onDeleteMessage(pos)
                }
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

            // 处理思维链内容
            val reasoning = msg.reasoningContent
            if (!reasoning.isNullOrBlank()) {
                holder.binding.llReasoning.visible()
                markwon?.setMarkdown(holder.binding.tvReasoningContent, reasoning)
                // 初始化为折叠状态（每次绑定都重置）
                holder.binding.tvReasoningContent.gone()
                holder.binding.ivReasoningArrow.rotation = 90f
                // 点击头部切换折叠/展开
                holder.binding.llReasoningHeader.setOnClickListener {
                    val isExpanded = holder.binding.tvReasoningContent.visibility == View.VISIBLE
                    if (isExpanded) {
                        collapseReasoning(holder)
                    } else {
                        expandReasoning(holder)
                    }
                }
            } else {
                holder.binding.llReasoning.gone()
            }

            // 长按 AI 气泡显示删除按钮
            holder.binding.tvAiContent.setOnLongClickListener {
                toggleDeleteButton(holder, isUser = false)
                true
            }

            // 点击删除按钮
            holder.binding.ivDeleteAi.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_ID.toInt()) {
                    collapseDeleteButton(holder)
                    onDeleteMessage(pos)
                }
            }
        }
    }

    /**
     * 切换删除按钮的显示状态；如果当前有其他展开的 holder，先折叠它
     */
    private fun toggleDeleteButton(holder: ChatViewHolder, isUser: Boolean) {
        val deleteView = if (isUser) holder.binding.ivDeleteUser else holder.binding.ivDeleteAi
        val alreadyExpanded = deleteView.visibility == View.VISIBLE

        // 折叠之前展开的（如果不是同一个）
        if (expandedHolder != null && expandedHolder != holder) {
            collapseDeleteButton(expandedHolder!!)
        }

        if (alreadyExpanded) {
            collapseDeleteButton(holder)
        } else {
            deleteView.alpha = 0f
            deleteView.visible()
            deleteView.animate().alpha(1f).setDuration(150).start()
            expandedHolder = holder
        }
    }

    private fun collapseDeleteButton(holder: ChatViewHolder) {
        holder.binding.ivDeleteAi.gone()
        holder.binding.ivDeleteUser.gone()
        if (expandedHolder == holder) expandedHolder = null
    }

    private fun expandReasoning(holder: ChatViewHolder) {
        holder.binding.tvReasoningContent.visible()
        animateArrow(holder.binding.ivReasoningArrow, 90f, 270f)
    }

    private fun collapseReasoning(holder: ChatViewHolder) {
        holder.binding.tvReasoningContent.gone()
        animateArrow(holder.binding.ivReasoningArrow, 270f, 90f)
    }

    private fun animateArrow(view: android.widget.ImageView, from: Float, to: Float) {
        val anim = RotateAnimation(
            from, to,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 200
            fillAfter = true
        }
        view.startAnimation(anim)
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
