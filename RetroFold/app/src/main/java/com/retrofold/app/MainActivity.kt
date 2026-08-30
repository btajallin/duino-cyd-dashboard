package com.retrofold.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    var games by mutableStateOf<Map<SystemDef, List<Game>>>(emptyMap())
    var system by mutableIntStateOf(0)
    var game by mutableIntStateOf(0)
    var screen by mutableStateOf("library")
    var status by mutableStateOf("Ready")
    var pad by mutableStateOf(PadState())
    var retro by mutableStateOf<String?>(null)

    private var lastAxis = 0L
    private var lastDir = 0

    val visible: List<SystemDef>
        get() = SystemDef.entries.filter { games[it].orEmpty().isNotEmpty() }

    val selectedGames: List<Game>
        get() = visible.getOrNull(system)?.let { games[it].orEmpty() }.orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        immersive()
        refresh()
        setContent { RetroFoldUI(this) }
        if (access()) scan()
    }

    override fun onResume() {
        super.onResume()
        immersive()
        refresh()
        if (access() && games.isEmpty()) scan()
    }

    private fun immersive() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    fun access(): Boolean =
        android.os.Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

    fun grant() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
        } else {
            scan()
        }
    }

    fun refresh() {
        retro = RetroArch.find(this)
    }

    fun scan() {
        if (!access()) {
            status = "Grant storage access first"
            return
        }

        runCatching { Roms.scan() }
            .onSuccess {
                games = it
                system = system.coerceIn(0, (visible.size - 1).coerceAtLeast(0))
                game = 0
                status = "${it.values.sumOf { values -> values.size }} games ready"
            }
            .onFailure {
                status = "Scan failed: ${it.message}"
            }
    }

    fun play() {
        selectedGames.getOrNull(game)?.let { selected ->
            runCatching { RetroArch.launch(this, selected) }
                .onFailure {
                    status = it.message ?: "Launch failed"
                    screen = "settings"
                }
        }
    }

    fun cast() {
        val intent = listOf(
            Intent("android.settings.CAST_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
        ).firstOrNull { it.resolveActivity(packageManager) != null }

        intent?.let { startActivity(it) }
    }

    fun nav(dx: Int = 0, dy: Int = 0) {
        if (screen != "library") return

        if (dx != 0 && visible.isNotEmpty()) {
            system = (system + dx + visible.size) % visible.size
            game = 0
        }

        if (dy != 0 && selectedGames.isNotEmpty()) {
            game = (game + dy + selectedGames.size) % selectedGames.size
        }
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val controller =
            e.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
                e.isFromSource(InputDevice.SOURCE_DPAD) ||
                e.isFromSource(InputDevice.SOURCE_JOYSTICK)

        if (!controller) return super.dispatchKeyEvent(e)

        pad = pad.copy(
            name = e.device?.name ?: "Controller",
            button = KeyEvent.keyCodeToString(e.keyCode),
        )

        if (e.action != KeyEvent.ACTION_DOWN || e.repeatCount > 0) return true

        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> nav(dx = -1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> nav(dx = 1)
            KeyEvent.KEYCODE_DPAD_UP -> nav(dy = -1)
            KeyEvent.KEYCODE_DPAD_DOWN -> nav(dy = 1)
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER,
            -> if (screen == "library") play()
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BACK,
            -> screen = "library"
            KeyEvent.KEYCODE_BUTTON_START -> screen = "settings"
            KeyEvent.KEYCODE_BUTTON_SELECT -> screen = "controller"
        }
        return true
    }

    override fun dispatchGenericMotionEvent(e: MotionEvent): Boolean {
        if (!e.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            return super.dispatchGenericMotionEvent(e)
        }

        fun axis(axis: Int): Float {
            val value = e.getAxisValue(axis)
            return if (abs(value) < 0.12f) 0f else value
        }

        val currentPad = PadState(
            name = e.device?.name ?: "Controller",
            button = pad.button,
            lx = axis(MotionEvent.AXIS_X),
            ly = axis(MotionEvent.AXIS_Y),
            rx = axis(MotionEvent.AXIS_Z),
            ry = axis(MotionEvent.AXIS_RZ),
            lt = e.getAxisValue(MotionEvent.AXIS_LTRIGGER),
            rt = e.getAxisValue(MotionEvent.AXIS_RTRIGGER),
            hx = axis(MotionEvent.AXIS_HAT_X),
            hy = axis(MotionEvent.AXIS_HAT_Y),
        )
        pad = currentPad

        val horizontal = if (abs(currentPad.hx) > 0.5f) currentPad.hx else currentPad.lx
        val vertical = if (abs(currentPad.hy) > 0.5f) currentPad.hy else currentPad.ly

        val direction = when {
            horizontal < -0.65f -> 1
            horizontal > 0.65f -> 2
            vertical < -0.65f -> 3
            vertical > 0.65f -> 4
            else -> 0
        }

        val now = System.currentTimeMillis()
        if (direction == 0) {
            lastDir = 0
            return true
        }

        if (direction != lastDir || now - lastAxis > 220L) {
            when (direction) {
                1 -> nav(dx = -1)
                2 -> nav(dx = 1)
                3 -> nav(dy = -1)
                4 -> nav(dy = 1)
            }
            lastDir = direction
            lastAxis = now
        }

        return true
    }
}
