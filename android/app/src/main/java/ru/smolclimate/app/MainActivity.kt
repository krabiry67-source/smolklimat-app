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
    private val siteLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
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
    private fun showSiteView(url: String, x: Double, y: Double, w: Double, h: Double) {
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
        fun installApk(url: String): Unit = runOnUiThread { downloadAndInstall(url) }
        @JavascriptInterface
        fun installApk(url: String, version: String): Unit = runOnUiThread { downloadAndInstall(url, version) }
        // Список релизов GitHub запрашивает Android — без CORS и ограничений WebView. Ответ: window.onSmolReleases(status, json)
        @JavascriptInterface
        fun fetchReleases(repo: String) {
            Thread {
                var code = 0; var body = "[]"
                try {
                    val c = java.net.URL("https://api.github.com/repos/" + repo.trim().removeSuffix(".git") + "/releases?per_page=15").openConnection() as java.net.HttpURLConnection
                    c.setRequestProperty("Accept", "application/vnd.github+json")
                    c.setRequestProperty("User-Agent", "SmolKlimatApp")
                    c.connectTimeout = 15000; c.readTimeout = 15000
                    code = c.responseCode
                    body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: "[]"
                } catch (e: Exception) { code = 0 }
                val js = org.json.JSONObject.quote(body)
                web.post { web.evaluateJavascript("window.onSmolReleases && window.onSmolReleases($code, $js)", null) }
            }.start()
        }
        @JavascriptInterface
        fun cancelUpdate(): Unit = runOnUiThread { pendingUrl = null }
        @JavascriptInterface
        fun version(): String = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        @JavascriptInterface
        fun openBrowser(url: String): Unit = runOnUiThread {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
        }
        @JavascriptInterface
        fun showSite(url: String, x: Double, y: Double, w: Double, h: Double) { runOnUiThread { showSiteView(url, x, y, w, h) } }
        @JavascriptInterface
        fun hideSite(): Unit = runOnUiThread { site?.visibility = android.view.View.GONE }
        @JavascriptInterface
        fun reloadSite(): Unit = runOnUiThread { site?.reload() }
        @JavascriptInterface
        fun openSite(url: String) { runOnUiThread { siteLauncher.launch(SiteActivity.intent(this@MainActivity, url)) } }
        @JavascriptInterface
        fun hasPhotos(): Boolean = granted(photoPerm)
        @JavascriptInterface
        fun requestPhotos(): Unit = runOnUiThread { if (granted(photoPerm)) reply("photos", true) else askPhotos.launch(photoPerm) }
        @JavascriptInterface
        fun requestNotifications(): Unit = runOnUiThread {
            if (Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)) reply("notif", true)
            else askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ===== Обновление из GitHub =====
    // 1) разрешение «Установка неизвестных приложений» (если нет — открываем настройки и продолжаем в onResume)
    // 2) скачивание через DownloadManager с прогрессом в приложение
    // 3) проверка APK: тот же пакет, versionCode новее, та же подпись — иначе понятная ошибка
    // 4) системный установщик Android
    private var pendingUrl: String? = null
    private var pendingVersion: String = ""
    private var dlId = -1L
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())

    private fun upd(state: String, pct: Int = 0, extra: String = "") {
        val e = extra.replace("\\", "").replace("'", "’").replace("\n", " ")
        web.post { web.evaluateJavascript("window.onSmolUpdate && window.onSmolUpdate('$state', $pct, '$e')", null) }
    }

    private fun canInstall() = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()

    private fun downloadAndInstall(url: String, version: String = "") {
        if (dlId != -1L) return
        pendingUrl = url; pendingVersion = version
        if (!canInstall()) {
            upd("perm")
            try { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) }
            catch (e: Exception) { upd("error", 0, "Откройте Настройки → Приложения → СмолКлимат → Установка неизвестных приложений и включите разрешение") }
            return
        }
        startDownload(url)
    }

    override fun onResume() {
        super.onResume()
        val u = pendingUrl
        if (u != null && dlId == -1L) {
            if (canInstall()) startDownload(u) else upd("error", 0, "Без разрешения на установку обновление не поставить. Нажмите «Обновить» ещё раз")
        }
    }

    private fun startDownload(url: String) {
        pendingUrl = null
        val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return upd("error", 0, "Нет доступа к памяти телефона")
        val file = File(dir, "smolklimat-update.apk").apply { if (exists()) delete() }
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dlId = try {
            dm.enqueue(DownloadManager.Request(Uri.parse(url))
                .setTitle("Обновление СмолКлимат " + pendingVersion)
                .setMimeType("application/vnd.android.package-archive")
                .setDestinationUri(Uri.fromFile(file))
                .setAllowedOverMetered(true).setAllowedOverRoaming(true)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE))
        } catch (e: Exception) { upd("error", 0, "Не удалось начать загрузку: " + (e.message ?: "")); -1L }
        if (dlId == -1L) return
        upd("downloading", 0)
        val id = dlId
        val poll = object : Runnable {
            override fun run() {
                val c = dm.query(DownloadManager.Query().setFilterById(id))
                if (c == null || !c.moveToFirst()) { c?.close(); dlId = -1L; return upd("error", 0, "Загрузка отменена") }
                val st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                c.close()
                when (st) {
                    DownloadManager.STATUS_SUCCESSFUL -> { dlId = -1L; verifyAndInstall(file) }
                    DownloadManager.STATUS_FAILED -> { dlId = -1L; upd("error", 0, "Загрузка не удалась (код $reason). Проверьте интернет") }
                    else -> {
                        val pct = if (total > 0) (done * 100 / total).toInt() else 0
                        upd("downloading", pct, if (total > 0) String.format("%.1f МБ", total / 1048576.0) else "")
                        ui.postDelayed(this, 500)
                    }
                }
            }
        }
        ui.postDelayed(poll, 400)
    }

    @Suppress("DEPRECATION")
    private fun verifyAndInstall(file: File) {
        if (!file.exists() || file.length() < 1_000_000) return upd("error", 0, "Скачанный файл повреждён. Попробуйте ещё раз")
        val pm = packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val apk = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: return upd("error", 0, "Файл из релиза — не APK приложения")
        if (apk.packageName != packageName) return upd("error", 0, "В релизе APK другого приложения (" + apk.packageName + ")")
        val me = pm.getPackageInfo(packageName, flags)
        val newCode = if (Build.VERSION.SDK_INT >= 28) apk.longVersionCode else apk.versionCode.toLong()
        val curCode = if (Build.VERSION.SDK_INT >= 28) me.longVersionCode else me.versionCode.toLong()
        if (newCode <= curCode) return upd("error", 0, "В релизе версия не новее установленной (versionCode $newCode ≤ $curCode). Пересоберите APK с бо́льшим versionCode")
        fun sigs(p: android.content.pm.PackageInfo): Set<String> =
            (if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.apkContentsSigners?.toList() else p.signatures?.toList()).orEmpty().map { it.toCharsString() }.toSet()
        val a = sigs(apk); val b = sigs(me)
        if (a.isNotEmpty() && b.isNotEmpty() && a.intersect(b).isEmpty())
            return upd("error", 0, "Новая версия подписана другим ключом — Android не установит её поверх. Удалите приложение и установите заново (или соберите APK тем же ключом)")
        upd("ready", 100)
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        try {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { upd("error", 0, "Не удалось открыть установщик Android") }
    }
}
