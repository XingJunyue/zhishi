package com.zhishi.timetable

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Message
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import androidx.core.content.ContextCompat

/**
 * 教务导入内置浏览器的设置。目标：学校的统一认证（含二次认证、验证码、短信、扫码等）在这里和手机浏览器表现一样。
 *
 * 为什么这么设：
 *  - 浏览器标识去掉 WebView 特有的「; wv」和「Version/4.0」。有的统一认证 / 风控按这两个记号把请求当成「应用内嵌页」，
 *    换模板、拦截或者不渲染；去掉后就是普通手机 Chrome 的标识。「电脑版」开关换成桌面 Chrome 标识（服务端会给电脑模板）。
 *  - 支持多窗口：window.open / target=_blank 打开的页面放回同一个浏览器里，而不是悄悄丢掉。
 *  - alert / confirm / prompt 弹真正的对话框（二次认证页常用它提示「验证码已发送」、确认信任设备）。
 *  - 第三方 Cookie、DOM Storage、数据库都开；混合内容按兼容模式。
 *  - 定位请求：应用本身有定位权限时才放行；摄像头 / 麦克风等不放行（知时没有这些权限）。
 */
@SuppressLint("SetJavaScriptEnabled")
fun setupJwWebView(w: WebView, desktop: Boolean) {
    WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
    w.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        builtInZoomControls = true
        displayZoomControls = false
        setSupportZoom(true)
        javaScriptCanOpenWindowsAutomatically = true
        setSupportMultipleWindows(true)
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        allowContentAccess = true
        allowFileAccess = true
        mediaPlaybackRequiresUserGesture = false
        loadsImagesAutomatically = true
        blockNetworkImage = false
        textZoom = 100
        userAgentString = jwUserAgent(WebSettings.getDefaultUserAgent(w.context), desktop)
    }
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
}

/**
 * 系统默认 UA → 普通手机 Chrome 的 UA（去掉「; wv」「Version/x.x」），或桌面 Chrome 的 UA。纯函数，有单测。
 * 例：Mozilla/5.0 (Linux; Android 14; Pixel 6 Build/UP1A; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.6099.230 Mobile Safari/537.36
 *  →  Mozilla/5.0 (Linux; Android 14; Pixel 6 Build/UP1A) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36
 */
fun jwUserAgent(def: String, desktop: Boolean): String {
    val chrome = Regex("Chrome/([\\d.]+)").find(def)?.groupValues?.get(1) ?: "120.0.0.0"
    if (desktop) return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Safari/537.36"
    var ua = def.replace(Regex(";\\s*wv\\)"), ")").replace(Regex("\\s*Version/[\\d.]+"), "")
    if (!ua.contains("Chrome/")) ua = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Mobile Safari/537.36"
    if (!ua.contains("Mobile")) ua = ua.replace("Safari/", "Mobile Safari/")
    return ua.replace(Regex("\\s+"), " ").trim()
}

/** 内置浏览器自己加载哪些链接：网页和测试用的本地文件。weixin://、intent:// 之类不跳出去。 */
fun jwWebScheme(scheme: String?): Boolean = scheme != null && scheme.lowercase() in setOf("http", "https", "file", "about", "data", "blob", "javascript")
fun jwWebScheme(u: Uri): Boolean = jwWebScheme(u.scheme)

/** 进度、弹窗、新窗口、权限。 */
class JwChrome(private val ctx: Context, private val main: WebView, private val onProgress: (Int) -> Unit) : WebChromeClient() {
    override fun onProgressChanged(view: WebView, p: Int) { onProgress(p) }

    // window.open / target=_blank：先给一个临时 WebView 接住，拿到网址后在主浏览器里打开
    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
        val tmp = WebView(ctx)
        tmp.settings.javaScriptEnabled = true
        tmp.webViewClient = object : WebViewClient() {
            private var done = false
            private fun take(v: WebView, u: String?): Boolean {
                if (done || u.isNullOrBlank() || u == "about:blank") return false
                done = true
                if (jwWebScheme(Uri.parse(u).scheme)) main.loadUrl(u)
                v.stopLoading(); v.post { try { v.destroy() } catch (x: Exception) {} }
                return true
            }
            override fun shouldOverrideUrlLoading(v: WebView, req: WebResourceRequest): Boolean = take(v, req.url.toString()) || true
            override fun onPageStarted(v: WebView, url: String?, favicon: android.graphics.Bitmap?) { take(v, url) }
        }
        val t = resultMsg.obj as? WebView.WebViewTransport ?: return false
        t.webView = tmp
        resultMsg.sendToTarget()
        return true
    }

    override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean = dialog(result) {
        setMessage(message.orEmpty()).setPositiveButton(tr("确定")) { _, _ -> result.confirm() }
    }

    override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean = dialog(result) {
        setMessage(message.orEmpty()).setPositiveButton(tr("确定")) { _, _ -> result.confirm() }.setNegativeButton(tr("取消")) { _, _ -> result.cancel() }
    }

    override fun onJsPrompt(view: WebView, url: String?, message: String?, defaultValue: String?, result: JsPromptResult): Boolean {
        val input = EditText(ctx).apply { setText(defaultValue.orEmpty()) }
        return dialog(result) {
            setMessage(message.orEmpty()).setView(input)
                .setPositiveButton(tr("确定")) { _, _ -> result.confirm(input.text.toString()) }.setNegativeButton(tr("取消")) { _, _ -> result.cancel() }
        }
    }

    private fun dialog(result: JsResult, build: AlertDialog.Builder.() -> AlertDialog.Builder): Boolean = try {
        AlertDialog.Builder(ctx).build().setOnCancelListener { result.cancel() }.show(); true
    } catch (x: Exception) { false }   // 拿不到窗口时交给 WebView 默认处理

    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) {
        val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        callback.invoke(origin, ok, false)
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        // 只放行不需要系统权限的（受保护媒体 ID）；摄像头 / 麦克风知时没有权限，拒绝，页面会自己提示换方式
        val ok = request.resources.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
        if (ok.isNotEmpty()) request.grant(ok.toTypedArray()) else request.deny()
    }

    override fun onConsoleMessage(m: ConsoleMessage): Boolean {
        if (BuildConfig.DEBUG || m.messageLevel() == ConsoleMessage.MessageLevel.ERROR)
            Log.i("ZhishiWeb", "${m.messageLevel()} ${m.message()} @${m.sourceId()}:${m.lineNumber()}")
        return true
    }
}
