package com.baoan.dexhtml

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku
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
        fun requestShizuku(): Boolean {
            return try {
                if (!Shizuku.pingBinder()) return false
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return true
                Shizuku.requestPermission(1001)
                true
            } catch (e: Exception) { false }
        }

        @JavascriptInterface
        fun openAppPopup(pkg: String, act: String): Boolean {
            return try {
                val target = if (act.isNotEmpty()) "$pkg/$act" else pkg
                val cmd = "am start -n $target --windowingMode 3"
                runShell(cmd) || openIntentPopup(pkg)
            } catch (e: Exception) { false }
        }

        @JavascriptInterface
        fun openAppFreeform(pkg: String, act: String): Boolean {
            return try {
                val target = if (act.isNotEmpty()) "$pkg/$act" else pkg
                val cmd = "am start -n $target --windowingMode 3"
                runShell(cmd)
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
        fun closeApp(pkg: String): Boolean {
            return runShell("am force-stop $pkg")
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
                        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        icon.setBounds(0, 0, 64, 64)
                        icon.draw(canvas)
                        val stream = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.PNG, 80, stream)
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

    private fun openIntentPopup(pkg: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
            intent.putExtra("MultiWindowFlags", "popup")
            intent.putExtra("com.samsung.android.coreapps.multiwindow", "popup")
            startActivity(intent)
            true
        } catch (e: Exception) { false }
    }

    private fun runShell(cmd: String): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return false

            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(
                null,
                arrayOf("sh", "-c", cmd),
                null,
                null
            ) as Process
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            android.util.Log.e("DeX", "shell error: ${e.message}")
            false
        }
    }
}
