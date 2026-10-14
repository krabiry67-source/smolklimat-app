package ru.smolclimate.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var root: android.widget.FrameLayout
    private var site: WebView? = null
    private var siteUrl = ""
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val photoPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    // Разрешения: результат возвращаем в страницу -> window.onSmolPermission(kind, granted)
    private val askPhotos = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> reply("photos", ok) }
    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> reply("notif", ok) }

    // Каталог сайта: «Заказать монтаж» возвращает сюда и открывает заявку.
    private val openSite = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val page = (res.data?.getStringExtra("page") ?: "").replace("\\", "").replace("'", "’")
            web.post { web.evaluateJavascript("window.onSmolOrder && window.onSmolOrder('$page')", null) }
        }
    }

    // Выбор фото: только изображения, до 3 штук. Размер (≤ 5 МБ) и тип проверяет страница и сервер.
    private val pickImages = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val cb = fileCallback; fileCallback = null
        if (cb == null) return@registerForActivityResult
        val data = res.data
        if (res.resultCode != Activity.RESULT_OK || data == null) { cb.onReceiveValue(null); return@registerForActivityResult }
        val uris = mutableListOf<Uri>()
        data.clipData?.let { c -> for (i in 0 until minOf(c.itemCount, 3)) uris.add(c.getItemAt(i).uri) }
        if (uris.isEmpty()) data.data?.let { uris.add(it) }
        cb.onReceiveValue(uris.toTypedArray())
    }

    private fun reply(kind: String, ok: Boolean) =
        web.post { web.evaluateJavascript("window.onSmolPermission && window.onSmolPermission('$kind', $ok)", null) }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        root = android.widget.FrameLayout(this)
        root.addView(web, android.widget.FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.settings.userAgentString = web.settings.userAgentString + " SmolKlimatApp"
        android.webkit.CookieManager.getInstance().setAcceptCookie(false)
        web.addJavascriptInterface(Bridge(), "SmolApp")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val u = req.url
                if (u.scheme == "file") return false
                startActivity(Intent(Intent.ACTION_VIEW, u)) // tel:, mailto:, сайт, MAX — во внешних приложениях
                return true
            }
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript("document.documentElement.classList.add('app')", null)
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = cb
                val i = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif"))
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                pickImages.launch(Intent.createChooser(i, "Фото для заявки"))
                return true
            }
        }
        web.loadUrl("file:///android_asset/index.html")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val s = site
        if (s != null && s.visibility == android.view.View.VISIBLE && s.canGoBack()) { s.goBack(); return }
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    // Каталог внутри вкладки «Кондиционеры»: второй WebView поверх области контента (координаты — в CSS-пикселях страницы).
    // Открывает только каталог и карточки товаров; остальные ссылки — во внешних приложениях.
    @SuppressLint("SetJavaScriptEnabled")
    private fun showSite(url: String, x: Double, y: Double, w: Double, h: Double) {
        val d = resources.displayMetrics.density
        val s = site ?: WebView(this).also { v ->
            v.settings.javaScriptEnabled = true
            v.settings.domStorageEnabled = true
            v.setBackgroundColor(android.graphics.Color.WHITE)
            v.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                    val u = req.url
                    val ok = (u.scheme == "https" || u.scheme == "http") && (u.host ?: "").endsWith("smolclimate.ru") &&
                        (u.path ?: "").let { it.startsWith("/katalog") || it.startsWith("/tproduct") }
                    if (ok) return false
                    try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (e: Exception) {}
                    return true
                }
            }
            root.addView(v)
            site = v
        }
        s.layoutParams = android.widget.FrameLayout.LayoutParams((w * d).toInt(), (h * d).toInt()).apply {
            leftMargin = (x * d).toInt(); topMargin = (y * d).toInt()
        }
        s.visibility = android.view.View.VISIBLE
        if (siteUrl != url) { siteUrl = url; s.loadUrl(url) }
    }

    inner class Bridge {
        @JavascriptInterface
        fun installApk(url: String) = runOnUiThread { downloadAndInstall(url) }
        @JavascriptInterface
        fun version(): String = packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
        @JavascriptInterface
        fun openBrowser(url: String) = runOnUiThread {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
        }
        @JavascriptInterface
        fun showSite(url: String, x: Double, y: Double, w: Double, h: Double) = runOnUiThread { showSite(url, x, y, w, h) }
        @JavascriptInterface
        fun hideSite() = runOnUiThread { site?.visibility = android.view.View.GONE }
        @JavascriptInterface
        fun reloadSite() = runOnUiThread { site?.reload() }
        @JavascriptInterface
        fun openSite(url: String) = runOnUiThread { openSite.launch(SiteActivity.intent(this@MainActivity, url)) }
        @JavascriptInterface
        fun hasPhotos(): Boolean = granted(photoPerm)
        @JavascriptInterface
        fun requestPhotos() = runOnUiThread { if (granted(photoPerm)) reply("photos", true) else askPhotos.launch(photoPerm) }
        @JavascriptInterface
        fun requestNotifications() = runOnUiThread {
            if (Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)) reply("notif", true)
            else askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun downloadAndInstall(url: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
        val file = File(dir, "smolklimat-update.apk").apply { if (exists()) delete() }
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = dm.enqueue(DownloadManager.Request(Uri.parse(url))
            .setTitle("Обновление СмолКлимат")
            .setDestinationUri(Uri.fromFile(file))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED))
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                unregisterReceiver(this)
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", file)
                startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        ContextCompat.registerReceiver(this, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED)
    }
}
