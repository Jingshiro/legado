package io.legado.app.ui.main.my

import android.os.Bundle
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.databinding.ActivityReadingSkillBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class ReadingSkillActivity : BaseActivity<ActivityReadingSkillBinding>() {

    override val binding by viewBinding(ActivityReadingSkillBinding::inflate)

    companion object {
        private const val SKILL_REPO_URL =
            "https://github.com/Jingshiro/reading-skill"
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.btnDownloadSkill.setOnClickListener {
            alert(
                title = getString(R.string.reading_skill),
                message = "将以下地址发送给你的 AI Agent，即可安装 Legado Skill：\n\n$SKILL_REPO_URL"
            ) {
                yesButton {
                    sendToClip(SKILL_REPO_URL)
                    toastOnUi("已复制到剪贴板")
                }
                noButton {}
            }
        }
    }

}
