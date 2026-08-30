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

class MainActivity:ComponentActivity(){
    var games by mutableStateOf<Map<SystemDef,List<Game>>>(emptyMap()); var system by mutableIntStateOf(0); var game by mutableIntStateOf(0)
    var screen by mutableStateOf("library"); var status by mutableStateOf("Ready"); var pad by mutableStateOf(PadState()); var retro by mutableStateOf<String?>(null)
    private var lastAxis=0L; private var lastDir=0
    val visible get()=SystemDef.entries.filter{games[it].orEmpty().isNotEmpty()}; val selectedGames get()=visible.getOrNull(system)?.let{games[it].orEmpty()}.orEmpty()
    override fun onCreate(b:Bundle?){super.onCreate(b); WindowCompat.setDecorFitsSystemWindows(window,false); immersive(); refresh(); setContent{RetroFoldUI(this)}; if(access())scan()}
    override fun onResume(){super.onResume();immersive();refresh();if(access()&&games.isEmpty())scan()}
    private fun immersive(){WindowInsetsControllerCompat(window,window.decorView).apply{hide(WindowInsetsCompat.Type.systemBars());systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE}}
    fun access()=android.os.Build.VERSION.SDK_INT<30||Environment.isExternalStorageManager()
    fun grant(){if(android.os.Build.VERSION.SDK_INT>=30)startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:$packageName"))) else scan()}
    fun refresh(){retro=RetroArch.find(this)}
    fun scan(){if(!access()){status="Grant storage access first";return}; runCatching{Roms.scan()}.onSuccess{games=it;system=system.coerceIn(0,(visible.size-1).coerceAtLeast(0));game=0;status="${it.values.sumOf{v->v.size}} games ready"}.onFailure{status="Scan failed: ${it.message}"}}
    fun play(){selectedGames.getOrNull(game)?.let{runCatching{RetroArch.launch(this,it)}.onFailure{e->status=e.message?:"Launch failed";screen="settings"}}}
    fun cast(){listOf(Intent("android.settings.CAST_SETTINGS"),Intent(Settings.ACTION_WIRELESS_SETTINGS)).firstOrNull{it.resolveActivity(packageManager)!=null}?.let(::startActivity)}
    fun nav(dx:Int=0,dy:Int=0){if(screen!="library")return;if(dx!=0&&visible.isNotEmpty()){system=(system+dx+visible.size)%visible.size;game=0};if(dy!=0&&selectedGames.isNotEmpty())game=(game+dy+selectedGames.size)%selectedGames.size}
    override fun dispatchKeyEvent(e:KeyEvent):Boolean{val ctl=e.isFromSource(InputDevice.SOURCE_GAMEPAD)||e.isFromSource(InputDevice.SOURCE_DPAD)||e.isFromSource(InputDevice.SOURCE_JOYSTICK);if(!ctl)return super.dispatchKeyEvent(e);pad=pad.copy(name=e.device?.name?:"Controller",button=KeyEvent.keyCodeToString(e.keyCode));if(e.action!=KeyEvent.ACTION_DOWN||e.repeatCount>0)return true;when(e.keyCode){KeyEvent.KEYCODE_DPAD_LEFT->nav(dx=-1);KeyEvent.KEYCODE_DPAD_RIGHT->nav(dx=1);KeyEvent.KEYCODE_DPAD_UP->nav(dy=-1);KeyEvent.KEYCODE_DPAD_DOWN->nav(dy=1);KeyEvent.KEYCODE_BUTTON_A,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_DPAD_CENTER->if(screen=="library")play();KeyEvent.KEYCODE_BUTTON_B,KeyEvent.KEYCODE_BACK->screen="library";KeyEvent.KEYCODE_BUTTON_START->screen="settings";KeyEvent.KEYCODE_BUTTON_SELECT->screen="controller"};return true}
    override fun dispatchGenericMotionEvent(e:MotionEvent):Boolean{if(!e.isFromSource(InputDevice.SOURCE_JOYSTICK))return super.dispatchGenericMotionEvent(e);fun ax(a:Int)=e.getAxisValue(a).let{if(abs(it)<.12f)0f else it};val p=PadState(e.device?.name?:"Controller",pad.button,ax(MotionEvent.AXIS_X),ax(MotionEvent.AXIS_Y),ax(MotionEvent.AXIS_Z),ax(MotionEvent.AXIS_RZ),e.getAxisValue(MotionEvent.AXIS_LTRIGGER),e.getAxisValue(MotionEvent.AXIS_RTRIGGER),ax(MotionEvent.AXIS_HAT_X),ax(MotionEvent.AXIS_HAT_Y));pad=p;val h=if(abs(p.hx)>.5)p.hx else p.lx;val v=if(abs(p.hy)>.5)p.hy else p.ly;val d=when{h<-.65->1;h>.65->2;v<-.65->3;v>.65->4;else->0};val now=System.currentTimeMillis();if(d==0){lastDir=0;return true};if(d!=lastDir||now-lastAxis>220){when(d){1->nav(dx=-1);2->nav(dx=1);3->nav(dy=-1);4->nav(dy=1)};lastDir=d;lastAxis=now};return true}
}
