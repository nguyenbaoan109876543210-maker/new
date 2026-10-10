package com.baoan.dexhtml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku
import android.content.Intent
import android.content.pm.PackageManager
import java.io.ByteArrayOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(Bridge(), "AndroidBridge")
        webView.loadUrl("file:///android_asset/index.html")
        setContentView(webView)
    }

    inner class Bridge {

        @JavascriptInterface
        fun isShizukuReady(): Boolean {
            return try {
                Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) { false }
        }

@JavascriptInterface
fun openAppFreeform(target: String): Boolean {
    return try {
        if (!isShizukuReady()) return false
        val cmd = "am start --windowingMode 3 -n $target"
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            Array<String>::class.java
        )
        method.isAccessible = true
        val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        process.waitFor()
        process.exitValue() == 0
    } catch (e: Exception) {
        android.util.Log.e("DeX", "freeform error: ${e.message}")
        false
    }
}

        @JavascriptInterface
        fun openAppFreeform(target: String): Boolean {
            return try {
                if (!isShizukuReady()) return false
                val cmd = "am start --windowingMode 3 -n $target"
                val process = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
                process.waitFor()
                process.exitValue() == 0
            } catch (e: Exception) { false }
        }

        @JavascriptInterface
        fun openAppFullscreen(pkg: String): Boolean {
            return try {
                val intent = packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    true
                } else false
            } catch (e: Exception) { false }
        }

        @JavascriptInterface
        fun getInstalledApps(): String {
            return try {
                val pm = packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null)
                mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                val apps = pm.queryIntentActivities(mainIntent, 0)
                    .distinctBy { it.activityInfo.packageName }
                    .filter { it.activityInfo.packageName != packageName }
                    .map { resolve ->
                        val icon = resolve.loadIcon(pm)
                        val bmp = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        icon.setBounds(0, 0, 72, 72)
                        icon.draw(canvas)
                        val stream = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
                        val b64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                        val name = resolve.loadLabel(pm).toString().replace("\"", "\\\"")
                        val pkgName = resolve.activityInfo.packageName
                        val actName = resolve.activityInfo.name
                        """{"name":"$name","pkg":"$pkgName","act":"$actName","icon":"$b64"}"""
                    }
                "[" + apps.joinToString(",") + "]"
            } catch (e: Exception) { "[]" }
        }
    }
}
