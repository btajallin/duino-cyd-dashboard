package com.retrofold.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RetroFoldUI(a: MainActivity) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            when (a.screen) {
                "settings" -> Settings(a)
                "controller" -> Controller(a)
                else -> Library(a)
            }
        }
    }
}

@Composable
fun Header(a: MainActivity) {
    Row(
        Modifier.fillMaxWidth().padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("RETROFOLD", fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(a.status, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = { a.screen = "settings" }) { Text("SETTINGS") }
    }
}

@Composable
fun Library(a: MainActivity) {
    Column(Modifier.fillMaxSize()) {
        Header(a)
        if (!a.access()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Storage access required", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = a::grant) { Text("Grant file access") }
                }
            }
            return
        }
        if (a.visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No games found", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("${Roms.root}/nes\n${Roms.root}/megadrive", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = a::scan) { Text("SCAN") }
                }
            }
            return
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(a.visible) { i, s ->
                Card(
                    modifier = Modifier.width(190.dp).height(90.dp).clickable { a.system = i; a.game = 0 },
                    border = if (i == a.system) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(s.tag, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.weight(1f))
                        Text("${a.games[s].orEmpty().size} games")
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = 20.dp)) {
            Text(
                a.visible.getOrNull(a.system)?.title ?: "Games",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text("A PLAY   ↑↓ GAME   ←→ SYSTEM", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            itemsIndexed(a.selectedGames) { i, g ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(
                            if (i == a.game) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { if (i == a.game) a.play() else a.game = i }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(g.system.tag.take(3), fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g.name, fontSize = 17.sp, fontWeight = if (i == a.game) FontWeight.Bold else FontWeight.Normal)
                        Text(g.file.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (i == a.game) Text("PLAY  A", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun Settings(a: MainActivity) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("RetroFold Setup", fontSize = 30.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { a.screen = "library" }) { Text("BACK B") }
        }
        Spacer(Modifier.height(20.dp))
        Card {
            Column(Modifier.padding(18.dp)) {
                Text("ROM storage", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("${Roms.root}\nNES → roms/nes   Genesis → roms/megadrive")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = a::grant) { Text(if (a.access()) "ACCESS ✓" else "GRANT") }
                    OutlinedButton(onClick = a::scan) { Text("RESCAN") }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card {
            Column(Modifier.padding(18.dp)) {
                Text("RetroArch", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(a.retro?.let { "Detected: $it\nCores: FCEUmm + Genesis Plus GX" } ?: "RetroArch not detected")
            }
        }
        Spacer(Modifier.height(12.dp))
        Card {
            Column(Modifier.padding(18.dp)) {
                Text("TV mode", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Use Pixel Cast for wireless play; USB-C → HDMI is preferred for low latency.")
                Spacer(Modifier.height(10.dp))
                Button(onClick = a::cast) { Text("OPEN CAST SETTINGS") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { a.screen = "controller" }) { Text("CONTROLLER TEST") }
    }
}

@Composable
fun Controller(a: MainActivity) {
    val p = a.pad
    fun f(v: Float) = String.format("%+.2f", v)
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row {
            Column(Modifier.weight(1f)) {
                Text("Controller Diagnostic", fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text(p.name)
            }
            OutlinedButton(onClick = { a.screen = "library" }) { Text("BACK B") }
        }
        Spacer(Modifier.height(20.dp))
        Text("LAST BUTTON  ${p.button}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "LEFT     X ${f(p.lx)}   Y ${f(p.ly)}\nRIGHT   X ${f(p.rx)}   Y ${f(p.ry)}\nD-PAD  X ${f(p.hx)}   Y ${f(p.hy)}\nLT ${f(p.lt)}     RT ${f(p.rt)}",
            fontSize = 20.sp,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "Move every stick and press every button. These are the raw Android values RetroFold receives.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
