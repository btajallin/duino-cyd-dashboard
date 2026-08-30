package com.retrofetch.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.zip.ZipInputStream

class MainActivity : Activity() {

    private enum class RomSystem(val label: String, val folder: String) {
        NES("Nintendo / NES", "nes"),
        GENESIS("Sega Genesis / Mega Drive", "megadrive")
    }

    private lateinit var webView: WebView
    private lateinit var addressBar: EditText
    private lateinit var statusText: TextView
    private lateinit var backButton: Button
    private lateinit var forwardButton: Button

    private val romRoot: File
        get() = File(Environment.getExternalStorageDirectory(), "ROMs")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        configureWebView()

        if (!hasStorageAccess()) {
            requestStorageAccess()
        } else {
            ensureRomFolders()
        }

        webView.loadUrl("https://www.google.com/")
    }

    override fun onResume() {
        super.onResume()
        if (hasStorageAccess()) {
            ensureRomFolders()
            status("Ready — downloads go straight to /ROMs")
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(18, 18, 18))
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(6, 6, 6, 6)
        }

        fun navButton(text: String, action: () -> Unit) = Button(this).apply {
            this.text = text
            minWidth = 0
            minimumWidth = 0
            setPadding(14, 0, 14, 0)
            setOnClickListener { action() }
        }

        backButton = navButton("◀") { if (webView.canGoBack()) webView.goBack() }
        forwardButton = navButton("▶") { if (webView.canGoForward()) webView.goForward() }
        val refreshButton = navButton("↻") { webView.reload() }
        val romsButton = navButton("ROMs") { showRomSummary() }

        addressBar = EditText(this).apply {
            setSingleLine(true)
            hint = "Search or enter address"
            textSize = 15f
            imeOptions = EditorInfo.IME_ACTION_GO
            setSelectAllOnFocus(true)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    navigate(text.toString())
                    true
                } else false
            }
        }

        val goButton = navButton("GO") { navigate(addressBar.text.toString()) }

        toolbar.addView(backButton)
        toolbar.addView(forwardButton)
        toolbar.addView(refreshButton)
        toolbar.addView(addressBar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        toolbar.addView(goButton)
        toolbar.addView(romsButton)

        webView = WebView(this)

        statusText = TextView(this).apply {
            text = "RetroFetch v0.1"
            setTextColor(Color.LTGRAY)
            setBackgroundColor(Color.rgb(28, 28, 28))
            textSize = 12f
            setPadding(14, 8, 14, 8)
        }

        root.addView(toolbar)
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(statusText)
        setContentView(root)
    }

    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = WebSettings.getDefaultUserAgent(this@MainActivity)
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!url.isNullOrBlank()) addressBar.setText(url)
                updateNavigationButtons()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress in 1..99) status("Loading… $newProgress%")
                else if (newProgress == 100) status("Ready")
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (url.startsWith("blob:", ignoreCase = true)) {
                toast("This site uses a browser-generated download link that RetroFetch v0.1 cannot capture yet.")
                return@setDownloadListener
            }
            startRomDownload(url, userAgent, contentDisposition, mimeType)
        }
    }

    private fun navigate(raw: String) {
        val text = raw.trim()
        if (text.isBlank()) return
        val url = when {
            text.startsWith("http://", true) || text.startsWith("https://", true) -> text
            !text.contains(' ') && text.contains('.') -> "https://$text"
            else -> "https://www.google.com/search?q=" + URLEncoder.encode(text, "UTF-8")
        }
        webView.loadUrl(url)
    }

    private fun hasStorageAccess(): Boolean = Environment.isExternalStorageManager()

    private fun requestStorageAccess() {
        AlertDialog.Builder(this)
            .setTitle("Allow ROM folder access")
            .setMessage("RetroFetch needs All files access so a downloaded ROM can be placed directly into /ROMs/nes or /ROMs/megadrive.")
            .setPositiveButton("OPEN SETTINGS") { _, _ ->
                val appIntent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                runCatching { startActivity(appIntent) }
                    .onFailure { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            }
            .setNegativeButton("NOT NOW", null)
            .show()
    }

    private fun ensureRomFolders() {
        File(romRoot, RomSystem.NES.folder).mkdirs()
        File(romRoot, RomSystem.GENESIS.folder).mkdirs()
    }

    private fun startRomDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        if (!hasStorageAccess()) {
            toast("Grant All files access first.")
            requestStorageAccess()
            return
        }

        val referer = webView.url
        status("Starting ROM download…")

        Thread {
            var connection: HttpURLConnection? = null
            try {
                val tempDir = File(cacheDir, "rom-downloads").apply { mkdirs() }
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 20_000
                    readTimeout = 60_000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "*/*")
                    if (!userAgent.isNullOrBlank()) setRequestProperty("User-Agent", userAgent)
                    if (!referer.isNullOrBlank()) setRequestProperty("Referer", referer)
                    CookieManager.getInstance().getCookie(url)?.let { setRequestProperty("Cookie", it) }
                }

                connection.connect()
                val code = connection.responseCode
                if (code !in 200..299) error("Server returned HTTP $code")

                val finalUrl = connection.url.toString()
                val disposition = connection.getHeaderField("Content-Disposition") ?: contentDisposition
                val type = connection.contentType ?: mimeType
                val guessedName = sanitizeFilename(URLUtil.guessFileName(finalUrl, disposition, type))
                val tempFile = File(tempDir, "${System.currentTimeMillis()}-$guessedName")
                val expected = connection.contentLengthLong

                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        var lastUi = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            val now = System.currentTimeMillis()
                            if (now - lastUi > 500) {
                                if (expected > 0) {
                                    val pct = ((downloaded * 100) / expected).coerceIn(0, 100)
                                    statusFromWorker("Downloading $guessedName… $pct%")
                                } else {
                                    statusFromWorker("Downloading $guessedName… ${downloaded / 1024} KB")
                                }
                                lastUi = now
                            }
                        }
                    }
                }

                processDownloadedFile(tempFile, guessedName, finalUrl)
            } catch (e: Exception) {
                statusFromWorker("Download failed: ${e.message ?: "unknown error"}")
                runOnUiThread { toast("Download failed: ${e.message ?: "unknown error"}") }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun processDownloadedFile(tempFile: File, originalName: String, sourceUrl: String) {
        val ext = originalName.substringAfterLast('.', "").lowercase(Locale.US)

        if (ext == "zip") {
            processZip(tempFile, originalName, sourceUrl)
            return
        }

        val system = detectSystem(tempFile, originalName, sourceUrl)
        if (system != null) {
            val saved = moveIntoLibrary(tempFile, originalName, system)
            reportSaved(saved, system)
        } else {
            runOnUiThread { askForSystem(tempFile, originalName) }
        }
    }

    private fun processZip(zipFile: File, archiveName: String, sourceUrl: String) {
        val saved = mutableListOf<Pair<File, RomSystem>>()
        val tempDir = File(cacheDir, "rom-unzip").apply { mkdirs() }

        try {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) {
                        val entryName = sanitizeFilename(File(entry.name).name)
                        val ext = entryName.substringAfterLast('.', "").lowercase(Locale.US)
                        if (ext in setOf("nes", "gen", "md", "smd", "bin")) {
                            val extracted = File(tempDir, "${System.nanoTime()}-$entryName")
                            FileOutputStream(extracted).use { output -> zip.copyTo(output) }
                            val system = detectSystem(extracted, entryName, sourceUrl)
                            if (system != null) {
                                val finalFile = moveIntoLibrary(extracted, entryName, system)
                                saved += finalFile to system
                            } else {
                                extracted.delete()
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }

            if (saved.isNotEmpty()) {
                zipFile.delete()
                val nesCount = saved.count { it.second == RomSystem.NES }
                val genesisCount = saved.count { it.second == RomSystem.GENESIS }
                val summary = buildString {
                    append("Saved ${saved.size} ROM")
                    if (saved.size != 1) append('s')
                    if (nesCount > 0) append(" • NES: $nesCount")
                    if (genesisCount > 0) append(" • Genesis: $genesisCount")
                }
                statusFromWorker(summary)
                runOnUiThread { toast(summary) }
                return
            }

            val archiveSystem = inferFromText("$archiveName $sourceUrl")
            if (archiveSystem != null) {
                val savedArchive = moveIntoLibrary(zipFile, archiveName, archiveSystem)
                reportSaved(savedArchive, archiveSystem)
            } else {
                runOnUiThread { askForSystem(zipFile, archiveName) }
            }
        } catch (e: Exception) {
            statusFromWorker("ZIP processing failed: ${e.message ?: "unknown error"}")
            runOnUiThread { toast("ZIP processing failed: ${e.message ?: "unknown error"}") }
        }
    }

    private fun detectSystem(file: File, name: String, sourceUrl: String): RomSystem? {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        return when (ext) {
            "nes" -> RomSystem.NES
            "gen", "md", "smd" -> RomSystem.GENESIS
            "bin" -> if (looksLikeNes(file)) RomSystem.NES else RomSystem.GENESIS
            "7z", "zip" -> inferFromText("$name $sourceUrl")
            else -> when {
                looksLikeNes(file) -> RomSystem.NES
                looksLikeGenesis(file) -> RomSystem.GENESIS
                else -> inferFromText("$name $sourceUrl")
            }
        }
    }

    private fun looksLikeNes(file: File): Boolean = runCatching {
        if (file.length() < 4) return@runCatching false
        FileInputStream(file).use { input ->
            val header = ByteArray(4)
            input.read(header) == 4 &&
                header[0] == 0x4E.toByte() && header[1] == 0x45.toByte() &&
                header[2] == 0x53.toByte() && header[3] == 0x1A.toByte()
        }
    }.getOrDefault(false)

    private fun looksLikeGenesis(file: File): Boolean = runCatching {
        if (file.length() < 0x120) return@runCatching false
        FileInputStream(file).use { input ->
            input.skip(0x100)
            val header = ByteArray(32)
            input.read(header)
            String(header, Charsets.US_ASCII).uppercase(Locale.US).contains("SEGA")
        }
    }.getOrDefault(false)

    private fun inferFromText(text: String): RomSystem? {
        val t = text.lowercase(Locale.US)
        return when {
            "nintendo entertainment" in t || "/nes/" in t || "_nes_" in t || "-nes-" in t -> RomSystem.NES
            "genesis" in t || "mega drive" in t || "megadrive" in t || "mega-drive" in t -> RomSystem.GENESIS
            else -> null
        }
    }

    private fun moveIntoLibrary(source: File, requestedName: String, system: RomSystem): File {
        ensureRomFolders()
        val destinationDir = File(romRoot, system.folder).apply { mkdirs() }
        val destination = uniqueDestination(destinationDir, sanitizeFilename(requestedName))
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output -> input.copyTo(output) }
        }
        source.delete()
        return destination
    }

    private fun uniqueDestination(dir: File, filename: String): File {
        val first = File(dir, filename)
        if (!first.exists()) return first

        val dot = filename.lastIndexOf('.')
        val base = if (dot > 0) filename.substring(0, dot) else filename
        val ext = if (dot > 0) filename.substring(dot) else ""
        var number = 2
        while (true) {
            val candidate = File(dir, "$base ($number)$ext")
            if (!candidate.exists()) return candidate
            number++
        }
    }

    private fun sanitizeFilename(name: String): String {
        val cleaned = name
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .take(180)
        return cleaned.ifBlank { "rom-${System.currentTimeMillis()}" }
    }

    private fun askForSystem(tempFile: File, filename: String) {
        if (isFinishing || !tempFile.exists()) return
        AlertDialog.Builder(this)
            .setTitle("Which console is this ROM?")
            .setMessage("RetroFetch couldn't identify:\n$filename")
            .setItems(arrayOf("Nintendo / NES", "Sega Genesis / Mega Drive")) { _, which ->
                Thread {
                    runCatching {
                        val system = if (which == 0) RomSystem.NES else RomSystem.GENESIS
                        val saved = moveIntoLibrary(tempFile, filename, system)
                        reportSaved(saved, system)
                    }.onFailure { e ->
                        statusFromWorker("Save failed: ${e.message ?: "unknown error"}")
                    }
                }.start()
            }
            .setNegativeButton("CANCEL") { _, _ -> tempFile.delete() }
            .show()
    }

    private fun reportSaved(file: File, system: RomSystem) {
        val message = "${file.name} → /ROMs/${system.folder}/"
        statusFromWorker(message)
        runOnUiThread { toast(message) }
    }

    private fun showRomSummary() {
        if (!hasStorageAccess()) {
            requestStorageAccess()
            return
        }
        ensureRomFolders()
        val nesFiles = File(romRoot, RomSystem.NES.folder).listFiles()?.count { it.isFile } ?: 0
        val genesisFiles = File(romRoot, RomSystem.GENESIS.folder).listFiles()?.count { it.isFile } ?: 0
        AlertDialog.Builder(this)
            .setTitle("ROM library")
            .setMessage(
                "NES: $nesFiles ROMs\nGenesis / Mega Drive: $genesisFiles ROMs\n\n" +
                    "Folder: /storage/emulated/0/ROMs/"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun updateNavigationButtons() {
        backButton.isEnabled = webView.canGoBack()
        forwardButton.isEnabled = webView.canGoForward()
    }

    private fun status(message: String) {
        statusText.text = message
    }

    private fun statusFromWorker(message: String) {
        runOnUiThread { status(message) }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    @Deprecated("Deprecated in Android framework, retained for WebView navigation")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
