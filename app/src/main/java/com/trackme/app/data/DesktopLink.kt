package com.trackme.app.data

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.HashSet
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class DesktopClient(
    val ip: String,
    val browser: String,
    val system: String,
    val since: String,
)

/**
 * The phone is the server. A computer on the same Wi-Fi opens the address
 * (or scans the QR) and gets a live copy of the app. Clicks on the computer
 * are applied on the phone, and the computer refreshes from the phone.
 */
object DesktopLink {
    private const val PORT_START = 8787
    private val running = AtomicBoolean(false)
    private var socket: ServerSocket? = null
    private var token: String = ""
    private var pairCode: String = ""
    private val pool = Executors.newCachedThreadPool()
    private val liveSockets = Collections.synchronizedSet(HashSet<Socket>())

    private val _clients = MutableStateFlow<List<DesktopClient>>(emptyList())
    val clients: StateFlow<List<DesktopClient>> = _clients.asStateFlow()

    var linkUrl: String? = null
        private set
    var code: String? = null
        private set

    interface Bridge {
        fun stateJson(): String
        fun act(type: String, id: String)
        fun file(id: String): File?
        fun windowsApp(): File?
    }

    @Volatile
    var bridge: Bridge? = null

    fun lanAddress(): String? {
        val nets = NetworkInterface.getNetworkInterfaces() ?: return null
        var fallback: String? = null
        for (nif in nets) {
            if (!nif.isUp || nif.isLoopback) continue
            for (addr in nif.inetAddresses) {
                if (addr !is Inet4Address || addr.isLoopbackAddress) continue
                val host = addr.hostAddress ?: continue
                if (host.startsWith("169.254.")) continue
                val name = nif.name.lowercase(Locale.US)
                if (name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("ap")) {
                    return host
                }
                if (fallback == null) fallback = host
            }
        }
        return fallback
    }

    /** Starts listening. Returns the URL the computer should open, or null. */
    fun start(bridge: Bridge): String? {
        this.bridge = bridge
        if (running.get() && linkUrl != null) return linkUrl
        val ip = lanAddress() ?: return null
        token = randomToken()
        pairCode = randomCode()
        var bound: ServerSocket? = null
        var port = PORT_START
        while (port < PORT_START + 8) {
            try {
                bound = ServerSocket(port)
                break
            } catch (_: Exception) {
                port++
            }
        }
        val server = bound ?: return null
        server.soTimeout = 0
        socket = server
        running.set(true)
        _clients.value = emptyList()
        linkUrl = "http://$ip:$port/?t=$token"
        code = pairCode
        pool.execute {
            while (running.get()) {
                val client = try {
                    server.accept()
                } catch (_: Exception) {
                    null
                } ?: break
                pool.execute { handle(client) }
            }
        }
        return linkUrl
    }

    fun stop() {
        running.set(false)
        token = ""
        pairCode = ""
        bridge = null
        val open = liveSockets.toList()
        liveSockets.clear()
        for (client in open) {
            try {
                client.close()
            } catch (_: Exception) {
            }
        }
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        linkUrl = null
        code = null
        _clients.value = emptyList()
    }

    fun qrBitmap(text: String, size: Int = 640): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

    private fun randomCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val bytes = ByteArray(4)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { alphabet[(it.toInt() and 0xFF) % alphabet.length].toString() }
    }

    private fun randomToken(): String {
        val bytes = ByteArray(9)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun handle(client: Socket) {
        client.soTimeout = 8000
        liveSockets.add(client)
        try {
            val input = BufferedInputStream(client.getInputStream())
            val output = BufferedOutputStream(client.getOutputStream())
            val head = readHead(input) ?: return
            val line = head.lineSequence().firstOrNull().orEmpty()
            val parts = line.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val target = parts[1]
            val path = target.substringBefore("?")
            val query = parseQuery(target.substringAfter("?", ""))
            val ok = query["t"] == token && token.isNotEmpty()
            when {
                method == "GET" && (path == "/" || path == "/index.html") && ok -> {
                    writeText(output, 200, "text/html; charset=utf-8", PAGE)
                }
                method == "GET" && (path == "/" || path == "/index.html") -> {
                    writeText(output, 200, "text/html; charset=utf-8", PAIR_PAGE)
                }
                method == "POST" && path == "/api/pair" -> {
                    val len = contentLength(head).coerceIn(0, 200)
                    val raw = readBody(input, len)
                    val sent = runCatching { JSONObject(raw).optString("code") }.getOrNull().orEmpty()
                    if (sent.equals(pairCode, ignoreCase = true) && pairCode.isNotEmpty()) {
                        writeText(output, 200, "application/json; charset=utf-8", "{\"token\":\"$token\"}")
                    } else {
                        writeText(output, 200, "application/json; charset=utf-8", "{\"token\":\"\"}")
                    }
                }
                method == "GET" && path == "/api/state" && ok -> {
                    noteClient(client, head)
                    val body = bridge?.stateJson() ?: "{\"alive\":false}"
                    writeText(output, 200, "application/json; charset=utf-8", body)
                }
                method == "GET" && path == "/api/state" -> {
                    writeText(output, 401, "application/json; charset=utf-8", "{\"alive\":false}")
                }
                method == "GET" && path == "/TrackWindows.cmd" && ok -> {
                    val file = bridge?.windowsApp()
                    if (file == null || !file.exists() || file.length() <= 0L) {
                        writeText(output, 404, "text/plain; charset=utf-8", "TrackWindows.cmd is not in this build.")
                    } else {
                        writeDownload(output, file, "application/octet-stream", "TrackWindows.cmd")
                    }
                }
                method == "POST" && path == "/api/act" && ok -> {
                    val len = contentLength(head).coerceIn(0, 8000)
                    val raw = readBody(input, len)
                    val json = runCatching { JSONObject(raw) }.getOrNull()
                    val type = json?.optString("type").orEmpty()
                    val id = json?.optString("id").orEmpty()
                    if (type.isNotBlank()) bridge?.act(type, id)
                    writeText(output, 200, "application/json; charset=utf-8", "{\"ok\":true}")
                }
                method == "GET" && path == "/api/file" && ok && windowsClient(head) -> {
                    val file = query["id"]?.let { bridge?.file(it) }
                    if (file == null || !file.exists()) {
                        writeText(output, 404, "text/plain", "Not found")
                    } else {
                        writeFile(output, file)
                    }
                }
                method == "GET" && path == "/api/file" && ok -> {
                    writeText(output, 403, "text/plain; charset=utf-8", "Open this file in TrackWindows. The browser cannot download it.")
                }
                else -> writeText(output, 404, "text/plain; charset=utf-8", "Open the address shown on the phone.")
            }
        } catch (_: Exception) {
        } finally {
            liveSockets.remove(client)
            try {
                client.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun windowsClient(head: String): Boolean {
        val ua = head.lineSequence()
            .firstOrNull { it.startsWith("User-Agent:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            .orEmpty()
        return ua.contains("TrackWindows")
    }

    private fun noteClient(client: Socket, head: String) {
        val ip = client.inetAddress?.hostAddress ?: return
        val ua = head.lineSequence()
            .firstOrNull { it.startsWith("User-Agent:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            .orEmpty()
        val browser = when {
            ua.contains("TrackWindows") -> "TrackWindows"
            ua.contains("Edg/") -> "Edge"
            ua.contains("Chrome/") -> "Chrome"
            ua.contains("Firefox/") -> "Firefox"
            ua.contains("Safari/") -> "Safari"
            else -> "Browser"
        }
        val system = when {
            ua.contains("Windows") -> "Windows"
            ua.contains("Mac OS") -> "Mac"
            ua.contains("Linux") -> "Linux"
            ua.contains("Android") -> "Android"
            else -> "Computer"
        }
        val now = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        val current = _clients.value.toMutableList()
        val existing = current.indexOfFirst { it.ip == ip && it.browser == browser }
        val row = DesktopClient(ip, browser, system, if (existing >= 0) current[existing].since else now)
        if (existing >= 0) current[existing] = row else current.add(row)
        _clients.value = current
    }

    private fun contentLength(head: String): Int {
        val line = head.lineSequence().firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
        return line?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0
    }

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split("&").mapNotNull { pair ->
            val k = pair.substringBefore("=")
            val v = URLDecoder.decode(pair.substringAfter("=", ""), "UTF-8")
            if (k.isBlank()) null else k to v
        }.toMap()
    }

    private fun readHead(input: InputStream): String? {
        val buf = ByteArray(4096)
        val out = StringBuilder()
        var total = 0
        while (total < buf.size) {
            val n = input.read(buf, total, 1)
            if (n < 0) return null
            total += n
            if (total >= 4 &&
                buf[total - 4] == '\r'.code.toByte() &&
                buf[total - 3] == '\n'.code.toByte() &&
                buf[total - 2] == '\r'.code.toByte() &&
                buf[total - 1] == '\n'.code.toByte()
            ) {
                break
            }
        }
        out.append(String(buf, 0, total, Charsets.ISO_8859_1))
        return out.toString()
    }

    private fun readBody(input: InputStream, len: Int): String {
        if (len <= 0) return ""
        val buf = ByteArray(len)
        var got = 0
        while (got < len) {
            val n = input.read(buf, got, len - got)
            if (n < 0) break
            got += n
        }
        return String(buf, 0, got, Charsets.UTF_8)
    }

    private fun writeText(output: BufferedOutputStream, code: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val status = when (code) {
            200 -> "OK"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            else -> "Error"
        }
        val head = "HTTP/1.1 $code $status\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n" +
            "Cache-Control: no-store\r\n\r\n"
        output.write(head.toByteArray(Charsets.ISO_8859_1))
        output.write(bytes)
        output.flush()
    }

    private fun writeFile(output: BufferedOutputStream, file: File) {
        writeDownload(output, file, "application/pdf", file.name)
    }

    private fun writeDownload(output: BufferedOutputStream, file: File, type: String, name: String) {
        val head = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: ${file.length()}\r\n" +
            "Content-Disposition: attachment; filename=\"$name\"\r\n" +
            "Connection: close\r\n\r\n"
        output.write(head.toByteArray(Charsets.ISO_8859_1))
        file.inputStream().use { it.copyTo(output) }
        output.flush()
    }
}

private const val PAIR_PAGE = """<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>Track</title>
<style>
  body { margin: 0; min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #121216; color: #fff; font-family: Segoe UI, system-ui, sans-serif; }
  form { width: 320px; text-align: center; }
  input { width: 100%; font-size: 28px; letter-spacing: 8px; text-align: center; text-transform: uppercase; padding: 12px; border-radius: 12px; border: 0; margin: 12px 0; }
  button { width: 100%; padding: 12px; border: 0; border-radius: 999px; background: #ff8a80; font-weight: 700; cursor: pointer; }
  p { color: #fca5a5; min-height: 1.2em; }
</style></head>
<body><form id="f">
  <h1>Track</h1>
  <div>Type the 4-character code shown on the phone</div>
  <input id="c" maxlength="4" autocomplete="off" autofocus/>
  <button type="submit">Open</button>
  <p id="m"></p>
</form>
<script>
document.getElementById("f").onsubmit = function (e) {
  e.preventDefault();
  var code = document.getElementById("c").value.trim();
  fetch("/api/pair", {method:"POST", headers:{"Content-Type":"application/json"}, body: JSON.stringify({code: code})})
    .then(function (r) { return r.json(); })
    .then(function (j) {
      if (j.token) location.href = "/?t=" + encodeURIComponent(j.token);
      else document.getElementById("m").textContent = "That code does not match the phone.";
    });
};
</script></body></html>
"""

private const val PAGE = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>Track</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  body { margin: 0; background: #0c0c10; color: #f4f4f5; font-family: Segoe UI, system-ui, sans-serif; }
  .wrap { max-width: 480px; margin: 0 auto; min-height: 100vh; background: #121216; }
  header { padding: 18px 16px 8px; display: flex; justify-content: space-between; align-items: center; }
  h1 { font-size: 22px; margin: 0; }
  .live { font-size: 12px; color: #86efac; }
  nav { display: flex; gap: 8px; padding: 8px 16px 12px; }
  button, .btn { border: 0; border-radius: 999px; padding: 8px 14px; background: #2a2a32; color: #fff; cursor: pointer; font-weight: 650; }
  button.on, .on { background: #ff8a80; color: #1a1a1a; }
  .card { margin: 0 16px 10px; background: #1e1e24; border-radius: 16px; padding: 14px; cursor: pointer; }
  .card:hover { outline: 1px solid #ff8a80; }
  .file, .file:hover { cursor: default; outline: none; }
  .plat { color: #ff8a80; font-size: 12px; font-weight: 700; }
  .name { font-size: 16px; font-weight: 700; margin-top: 2px; }
  .sub { color: #a1a1aa; font-size: 13px; }
  .count { font-size: 20px; font-weight: 750; margin-top: 6px; }
  .empty { color: #a1a1aa; padding: 28px 16px; text-align: center; }
  .back { margin: 8px 16px; }
  a.file { display: block; color: inherit; text-decoration: none; }
  a.win { background: #d7e8ff; color: #1a1a1a; text-decoration: none; }
  .add { display: flex; gap: 8px; padding: 0 16px 12px; }
  .add input { flex: 1; border: 0; border-radius: 999px; padding: 10px 14px; background: #2a2a32; color: #fff; }
</style>
</head>
<body>
<div class="wrap">
  <header><h1 id="who">Track</h1><div class="live" id="live">Connecting...</div></header>
  <nav>
    <button id="b-home" onclick="act('home')">Home</button>
    <button id="b-news" onclick="act('news')">News</button>
    <button id="b-files" onclick="act('files')">Files</button>
    <button onclick="act('refresh')">Refresh</button>
    <a class="btn win" id="win" href="#">Install TrackWindows</a>
  </nav>
  <form class="add" id="add">
    <input id="url" placeholder="Paste a profile link" />
    <button type="submit">Add</button>
  </form>
  <div id="app"></div>
</div>
<script>
var token = new URLSearchParams(location.search).get("t") || "";
document.getElementById("win").href = "/TrackWindows.cmd?t=" + encodeURIComponent(token);
document.getElementById("add").onsubmit = function (e) {
  e.preventDefault();
  var box = document.getElementById("url");
  act("scan", box.value);
  box.value = "";
};
function act(type, id) {
  fetch("/api/act?t=" + encodeURIComponent(token), {
    method: "POST",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify({type: type, id: id || ""})
  }).then(load).catch(function () {});
}
function esc(s) {
  return String(s || "").replace(/[&<>"]/g, function (c) {
    return {"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;"}[c];
  });
}
function load() {
  fetch("/api/state?t=" + encodeURIComponent(token)).then(function (r) {
    if (!r.ok) throw new Error("no");
    return r.json();
  }).then(draw).catch(function () {
    document.getElementById("live").textContent = "Left the phone";
    document.getElementById("app").innerHTML = '<div class="empty">The phone logged out. Open Linked Devices again and connect with the new code.</div>';
    document.getElementById("add").style.display = "none";
  });
}
function draw(s) {
  document.getElementById("who").textContent = s.name || "Track";
  document.getElementById("live").textContent = s.note || "Live with your phone";
  document.getElementById("add").style.display = "flex";
  ["home","news","files"].forEach(function (k) {
    document.getElementById("b-" + k).className = (s.screen === k.toUpperCase() || (k === "home" && s.screen === "DETAIL")) ? "on" : "";
  });
  var html = "";
  if (s.screen === "DETAIL" && s.detail) {
    var d = s.detail;
    html += '<button class="back" onclick="act(\'home\')">Back</button>';
    html += '<div class="card"><div class="plat">' + esc(d.platform) + '</div><div class="name">' + esc(d.name) + '</div>';
    if (d.handle) html += '<div class="sub">' + esc(d.handle) + '</div>';
    if (d.count) html += '<div class="count">' + esc(d.count) + ' ' + esc(d.countLabel) + '</div>';
    (d.extras || []).forEach(function (e) {
      if (e.value) html += '<div class="sub">' + esc(e.label) + ': ' + esc(e.value) + '</div>';
    });
    (d.sections || []).forEach(function (sec) {
      html += '<div class="name" style="margin-top:12px">' + esc(sec.title) + '</div>';
      (sec.items || []).forEach(function (line) { html += '<div class="sub">' + esc(line) + '</div>'; });
    });
    html += '</div>';
  } else if (s.screen === "NEWS") {
    if (!s.news || !s.news.length) html = '<div class="empty">No headlines yet. They appear as the phone loads them.</div>';
    (s.news || []).forEach(function (n) {
      html += '<a class="card" style="display:block;color:inherit;text-decoration:none" href="' + esc(n.url) + '" target="_blank">';
      html += '<div class="plat">' + esc(n.source) + '</div><div class="name">' + esc(n.title) + '</div></a>';
    });
  } else if (s.screen === "FILES") {
    if (!s.files || !s.files.length) html = '<div class="empty">No uploaded files on the phone.</div>';
    else html += '<div class="empty">Files stay on the phone. Open them in TrackWindows. This page cannot download them.</div>';
    (s.files || []).forEach(function (f) {
      html += '<div class="card file"><div class="name">' + esc(f.name) + '</div><div class="sub">' + esc(f.size) + '</div></div>';
    });
  } else {
    if (!s.accounts || !s.accounts.length) html = '<div class="empty">No tags yet. Paste a profile link above, or add one on the phone.</div>';
    (s.accounts || []).forEach(function (a) {
      html += '<div class="card" data-id="' + esc(a.id) + '" onclick="act(\'open\', this.getAttribute(\'data-id\'))">';
      html += '<div class="plat">' + esc(a.platform) + '</div><div class="name">' + esc(a.name) + '</div>';
      if (a.handle) html += '<div class="sub">' + esc(a.handle) + '</div>';
      if (a.count) html += '<div class="count">' + esc(a.count) + ' ' + esc(a.countLabel) + '</div>';
      html += '</div>';
    });
  }
  document.getElementById("app").innerHTML = html;
}
load();
setInterval(load, 500);
</script>
</body>
</html>
"""
