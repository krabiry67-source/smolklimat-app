package ru.smolclimate.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

// Каталог smolclimate.ru на весь экран внутри приложения.
// Верхняя панель: «← Назад» и «Заказать монтаж» (возвращает в приложение и открывает заявку).
class SiteActivity : AppCompatActivity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * dp).toInt(), 0, (12 * dp).toInt(), 0)
            setBackgroundColor(Color.WHITE)
        }
        val back = Button(this).apply {
            text = "←"; textSize = 22f; setTextColor(Color.parseColor("#10202F")); setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { if (web.canGoBack()) web.goBack() else finish() }
        }
        val title = TextView(this).apply {
            text = "Кондиционеры"; textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.parseColor("#10202F"))
        }
        val order = Button(this).apply {
            text = "Заказать монтаж"; isAllCaps = false; textSize = 13f; setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor("#3486D6"))
            setPadding((14 * dp).toInt(), 0, (14 * dp).toInt(), 0)
            setOnClickListener {
                setResult(Activity.RESULT_OK, Intent().putExtra("page", web.title ?: ""))
                finish()
            }
        }
        bar.addView(back, LinearLayout.LayoutParams((48 * dp).toInt(), (48 * dp).toInt()))
        bar.addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        bar.addView(order, LinearLayout.LayoutParams(WRAP_CONTENT, (38 * dp).toInt()))

        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; isIndeterminate = false }
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) { progress.progress = p; progress.visibility = if (p >= 100) View.GONE else View.VISIBLE }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val u = req.url
                val host = u.host ?: ""
                // Встроенный браузер показывает только каталог и карточки товаров; остальное — во внешнем браузере.
                val inCatalog = host.endsWith("smolclimate.ru") && (u.path ?: "").let { it.startsWith("/katalog") || it.startsWith("/tproduct") }
                if ((u.scheme == "https" || u.scheme == "http") && inCatalog) return false
                try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (e: Exception) {}
                return true
            }
            override fun onPageCommitVisible(view: WebView, url: String?) { view.evaluateJavascript(HIDE_SITE_HEADER, null) }
            override fun onPageFinished(view: WebView, url: String?) { view.evaluateJavascript(HIDE_SITE_HEADER, null) }
        }

        root.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, (56 * dp).toInt()))
        root.addView(progress, LinearLayout.LayoutParams(MATCH_PARENT, (3 * dp).toInt()))
        root.addView(web, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)
        web.loadUrl(intent.getStringExtra("url") ?: "https://smolclimate.ru/katalog")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (web.canGoBack()) web.goBack() else super.onBackPressed() }

    companion object {
        fun intent(a: Activity, url: String) = Intent(a, SiteActivity::class.java).putExtra("url", url)
    }
}
