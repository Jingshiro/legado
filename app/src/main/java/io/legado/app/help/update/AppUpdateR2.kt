package io.legado.app.help.update

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import io.legado.app.constant.AppConst
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.CoroutineScope

@Keep
object AppUpdateR2 : AppUpdate.AppUpdateInterface {

    // R2 公开下载地址（非敏感信息，用户需要访问此 URL 下载 APK）
    private const val LATEST_JSON_URL =
        "https://pub-e92820e1b7014e0cb1b79a5187956610.r2.dev/latest.json"

    private val checkVariant: AppVariant
        get() = when (AppConfig.updateToVariant) {
            "official_version" -> AppVariant.OFFICIAL
            "beta_release_version" -> AppVariant.BETA_RELEASE
            "beta_releaseA_version" -> AppVariant.BETA_RELEASEA
            "beta_releaseS_version" -> AppVariant.BETA_RELEASES
            else -> AppConst.appInfo.appVariant
        }

    override fun check(scope: CoroutineScope): Coroutine<AppUpdate.UpdateInfo> {
        return Coroutine.async(scope) {
            val res = okHttpClient.newCallResponse {
                url(LATEST_JSON_URL)
            }
            if (!res.isSuccessful) {
                throw NoStackTraceException("获取新版本出错(${res.code})")
            }
            val body = res.body.text()
            if (body.isBlank()) {
                throw NoStackTraceException("获取新版本出错")
            }
            val release = GSON.fromJsonObject<R2LatestRelease>(body)
                .getOrElse {
                    throw NoStackTraceException("获取新版本出错 " + it.localizedMessage)
                }

            val targetVariant = checkVariant
            val asset = release.assets.firstOrNull { it.toAppVariant() == targetVariant }
                ?: throw NoStackTraceException("未找到适合当前版本的更新包")

            if (release.versionName <= AppConst.appInfo.versionName) {
                throw NoStackTraceException("已是最新版本")
            }

            AppUpdate.UpdateInfo(
                release.versionName,
                release.updateLog,
                asset.url,
                asset.name
            )
        }.timeout(10000)
    }
}

@Keep
data class R2LatestRelease(
    @SerializedName("versionName") val versionName: String,
    @SerializedName("updateLog") val updateLog: String,
    @SerializedName("assets") val assets: List<R2Asset>
)

@Keep
data class R2Asset(
    @SerializedName("name") val name: String,
    @SerializedName("url") val url: String,
    @SerializedName("variant") val variant: String
) {
    fun toAppVariant(): AppVariant = when (variant) {
        "BETA_RELEASEA" -> AppVariant.BETA_RELEASEA
        "BETA_RELEASES" -> AppVariant.BETA_RELEASES
        "BETA_RELEASE" -> AppVariant.BETA_RELEASE
        "OFFICIAL" -> AppVariant.OFFICIAL
        else -> AppVariant.UNKNOWN
    }
}
