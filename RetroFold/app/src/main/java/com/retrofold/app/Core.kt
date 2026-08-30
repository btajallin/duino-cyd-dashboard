package com.retrofold.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Environment
import java.io.File

enum class SystemDef(val title:String,val folder:String,val exts:Set<String>,val core:String,val tag:String) {
    NES("Nintendo Entertainment System","nes",setOf("nes","zip","7z"),"fceumm_libretro_android.so","NES"),
    GENESIS("Sega Genesis / Mega Drive","megadrive",setOf("gen","md","smd","bin","zip","7z"),"genesis_plus_gx_libretro_android.so","GENESIS")
}
data class Game(val name:String,val file:File,val system:SystemDef)
data class PadState(val name:String="No controller input yet",val button:String="—",val lx:Float=0f,val ly:Float=0f,val rx:Float=0f,val ry:Float=0f,val lt:Float=0f,val rt:Float=0f,val hx:Float=0f,val hy:Float=0f)

object Roms {
    val root:File get()=File(Environment.getExternalStorageDirectory(),"RetroFold/roms")
    fun scan():Map<SystemDef,List<Game>> {
        root.mkdirs()
        return SystemDef.entries.associateWith { s ->
            val d=File(root,s.folder); d.mkdirs()
            d.listFiles()?.filter{it.isFile && it.extension.lowercase() in s.exts}?.map{Game(it.nameWithoutExtension.replace('_',' '),it,s)}?.sortedBy{it.name.lowercase()}.orEmpty()
        }
    }
}

object RetroArch {
    private val pkgs=listOf("com.retroarch.aarch64","com.retroarch","com.retroarch.ra32")
    fun find(a:Activity)=pkgs.firstOrNull { try { a.packageManager.getPackageInfo(it,0); true } catch(_:PackageManager.NameNotFoundException){false} }
    fun launch(a:Activity,g:Game) {
        val p=find(a) ?: error("RetroArch is not installed")
        val i=Intent().apply {
            component=ComponentName(p,"com.retroarch.browser.retroactivity.RetroActivityFuture")
            putExtra("ROM",g.file.absolutePath)
            putExtra("LIBRETRO","/data/data/$p/cores/${g.system.core}")
            putExtra("CONFIGFILE","/storage/emulated/0/Android/data/$p/files/retroarch.cfg")
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        a.startActivity(i)
    }
}
