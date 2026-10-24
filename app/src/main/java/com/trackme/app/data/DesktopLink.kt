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
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700&display=swap" rel="stylesheet">
<style>
:root{--bg:#07070d;--panel:#12121c;--line:rgba(255,255,255,.08);--text:#f4f4fa;--muted:#9a9ab3;--lime:#c6f25b}
*{box-sizing:border-box;margin:0;padding:0}
html{scroll-behavior:smooth}
body{font-family:'Outfit',system-ui,'Segoe UI',sans-serif;background:var(--bg);color:var(--text);min-height:100vh}
a{color:inherit;text-decoration:none}
#page{max-width:1100px;margin:auto;padding:clamp(28px,6vw,80px) clamp(18px,5vw,40px) 120px;padding-left:max(clamp(18px,5vw,40px),88px);transition:opacity .55s ease,filter .55s ease,transform .7s cubic-bezier(.2,.8,.2,1)}
body:not(.page-open) #page{opacity:0;filter:blur(18px);transform:scale(.96);pointer-events:none}
body:not(.page-open){overflow:hidden}
#page section{min-height:70vh;padding:40px 0;scroll-margin-top:20px}
#page h1{font-size:clamp(2.4rem,7vw,4.6rem);font-weight:400;letter-spacing:-2px;line-height:1}
#page h2{font-size:clamp(1.6rem,4vw,2.4rem);font-weight:400;letter-spacing:-1px;margin-bottom:18px}
#page p.lead{color:var(--muted);max-width:62ch;line-height:1.6;margin-top:16px}
.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:14px;margin-top:26px}
.c{background:var(--panel);border:1px solid var(--line);border-radius:24px;padding:22px;cursor:pointer;min-width:0;overflow:hidden}
.c b{display:block;font-size:1.35rem;font-weight:500;letter-spacing:-.4px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.c span{display:block;color:var(--muted);font-size:.85rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.acts{display:flex;gap:8px;margin-top:14px}
.acts button{border:1px solid var(--line);background:transparent;color:var(--text);border-radius:99px;padding:6px 12px;cursor:pointer;font:inherit;font-size:.8rem}
.bars{display:flex;align-items:flex-end;gap:6px;height:180px;margin-top:28px;border-bottom:1px solid var(--line)}
.bars i{flex:1;display:block;background:var(--lime);border-radius:12px 12px 4px 4px;min-width:0}
.c.hot b{color:#ff5a6a}
.row{display:flex;gap:8px;margin-top:18px}
.row input{flex:1;border:1px solid var(--line);border-radius:99px;padding:12px 16px;background:#1d1d2a;color:#fff}
.btn{display:inline-block;margin-top:18px;background:var(--lime);color:#0a120a;padding:13px 26px;border-radius:99px;font-weight:600;border:0;cursor:pointer}
.list{margin-top:18px;display:flex;flex-direction:column;gap:10px}
.item{background:var(--panel);border:1px solid var(--line);border-radius:18px;padding:16px}
.item b{display:block}
.item span{color:var(--muted);font-size:.85rem}
.live{color:var(--lime);font-size:.9rem;margin-top:10px}
.dock-edge{position:fixed;left:0;top:50%;width:22px;height:160px;transform:translateY(-50%);z-index:90}
.dock{position:fixed;left:0;top:50%;z-index:100;display:flex;align-items:center;transform:translate(-26px,-50%);transition:transform .55s cubic-bezier(.2,.8,.2,1);--bg:#1d1d2a;--ring:rgba(255,255,255,.16)}
.dock.open{transform:translate(14px,-50%)}
.orb{width:56px;height:56px;border-radius:50%;background:var(--bg);border:1px solid var(--ring);display:grid;place-items:center;flex:none;cursor:pointer}
.orb svg{width:30px;height:30px;stroke:#fff;fill:none;stroke-width:1.7;stroke-linecap:round;stroke-linejoin:round;transition:transform .7s}
.dock.open .orb svg{transform:rotate(180deg)}
.rest{display:flex;align-items:center;max-width:0;opacity:0;overflow:hidden;visibility:hidden;padding:10px 0;transition:max-width .65s cubic-bezier(.2,.8,.2,1),opacity .35s,visibility 0s linear .65s}
.dock.open .rest{max-width:980px;opacity:1;visibility:visible;transition:max-width .65s,opacity .35s,visibility 0s}
.neck{width:26px;height:30px;background:var(--bg);margin:0 -8px;flex:none}
.pill{background:var(--bg);border:1px solid var(--ring);border-radius:99px;padding:7px;height:64px;flex:none}
.pill .in{height:100%;border:1px solid var(--ring);border-radius:99px;display:flex;align-items:center;padding:0 8px}
.pill a{color:#cfcfe0;padding:0 14px;font-size:.92rem;white-space:nowrap;position:relative;display:flex;align-items:center;height:100%}
.pill a+a::before{content:"";position:absolute;left:0;top:50%;height:24px;width:1px;background:var(--ring);transform:translateY(-50%)}
.pill a.on{color:var(--lime)}
.pill.get a{color:#fff;font-weight:600;padding:0 20px}
.fold{width:46px;height:46px;border-radius:50%;background:var(--bg);border:1px solid var(--ring);display:grid;place-items:center;cursor:pointer}
.fold svg{width:20px;height:20px;stroke:#fff;fill:none;stroke-width:2;stroke-linecap:round;stroke-linejoin:round}
@media(max-width:700px){
  .rest{position:absolute;left:0;top:calc(100% + 6px);flex-direction:column;align-items:stretch;max-width:none;width:min(290px,calc(100vw - 40px));padding:0;gap:8px}
  .neck{display:none}
  .pill{height:auto;border-radius:30px}
  .pill .in{flex-direction:column;align-items:stretch;padding:4px 0;border-radius:24px}
  .pill a{padding:14px 22px;height:auto}
  .pill a+a::before{left:18px;right:18px;top:0;width:auto;height:1px;transform:none}
  #page{padding-left:clamp(18px,5vw,40px);padding-top:110px}
}
</style>
</head>
<body>
<div class="dock-edge" id="dockEdge"></div>
<nav class="dock" id="dock" aria-label="Main">
  <button class="orb" id="orb" type="button" aria-expanded="false" aria-label="Open menu">
    <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M3 12h18"/><path d="M12 3c3.4 3 3.4 15 0 18"/><path d="M12 3c-3.4 3-3.4 15 0 18"/></svg>
  </button>
  <div class="rest" id="rest">
    <i class="neck"></i>
    <div class="pill"><div class="in">
      <a href="#home" class="on">Home</a>
      <a href="#activity">Activity</a>
      <a href="#files">Files</a>
      <a href="#scan">Scan</a>
      <a href="#features">Features</a>
    </div></div>
    <i class="neck"></i>
    <div class="pill get"><div class="in"><a id="win" href="#">TrackWindows</a></div></div>
    <i class="neck"></i>
    <button class="fold" id="fold" type="button" aria-label="Hide menu">
      <svg viewBox="0 0 24 24"><path d="M15 5l-7 7 7 7"/></svg>
    </button>
  </div>
</nav>
<main id="page">
  <section id="home">
    <h1 id="who">Track</h1>
    <p class="lead" id="live">Connecting...</p>
    <div class="cards" id="home-cards"></div>
  </section>
  <section id="activity">
    <h2>Activity</h2>
    <p class="lead">Each bar is one day. A taller bar is a day with more saved activity. A day with nothing saved stays on the line.</p>
    <div class="bars" id="activity-graph"></div>
    <p class="lead" id="activity-cost"></p>
  </section>
  <section id="files">
    <h2>Files</h2>
    <p class="lead">Documents stay on the phone. Open them in TrackWindows. A folder name is shown when the file is filed.</p>
    <div class="list" id="files-list"></div>
  </section>
  <section id="scan">
    <h2>Scan a tag</h2>
    <p class="lead">Paste a profile link. The phone saves it. The same link cannot be added twice.</p>
    <form class="row" id="add">
      <input id="url" placeholder="Profile link" />
      <button class="btn" type="submit">Add</button>
    </form>
  </section>
  <section id="features">
    <h2>Features</h2>
    <div class="list">
      <div class="item"><b>Your platforms</b><span>Each scanned link has its own color, even two accounts on the same site.</span></div>
      <div class="item"><b>Expenses</b><span>Double-tap the scan button on the phone. Set a monthly limit, add a cost, and download a week, month, or year.</span></div>
      <div class="item"><b>Activity</b><span>Choose what shows on the home card. Cost appears after expenses are opened.</span></div>
      <div class="item"><b>Files</b><span>Leave a document unfiled, or move it into a folder.</span></div>
      <div class="item"><b>Appearance</b><span>Light, dark, or system, plus an accent color.</span></div>
      <div class="item"><b>Verified</b><span>The blue seal appears after the promo code.</span></div>
    </div>
    <a class="btn" href="https://github.com/RajkumarPadmanabhan/Track.APK/releases/download/v1.3.24/TrackMe-v1.3.24.apk">Download APK</a>
  </section>
</main>
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
  if (type === "delete" && !confirm("Remove this profile from the phone?")) return;
  if (type === "delete-file" && !confirm("Remove this file from the phone?")) return;
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
    document.getElementById("live").textContent = "The phone logged out. Open Linked Devices again and connect with the new code.";
  });
}
function draw(s) {
  document.getElementById("who").textContent = s.name || "Track";
  document.getElementById("live").textContent = s.note || "Live with your phone";
  var home = "";
  (s.accounts || []).forEach(function (a) {
    var id = esc(a.id);
    home += '<div class="c" data-id="' + id + '" onclick="act(\'open\', this.getAttribute(\'data-id\'))"><b>' + esc(a.name || a.handle || a.platform) + '</b><span>' + esc(a.platform);
    if (a.count) home += ' · ' + esc(a.count) + ' ' + esc(a.countLabel);
    home += '</span><div class="acts"><button type="button" onclick="event.stopPropagation();act(\'update\', this.parentNode.parentNode.getAttribute(\'data-id\'))">Update</button>';
    home += '<button type="button" onclick="event.stopPropagation();act(\'delete\', this.parentNode.parentNode.getAttribute(\'data-id\'))">Delete</button></div></div>';
  });
  document.getElementById("home-cards").innerHTML = home || '<div class="item"><span>No tags yet. Paste a link under Scan.</span></div>';
  var series = s.series || [];
  var max = 0;
  series.forEach(function (n) { if (n > max) max = n; });
  var bars = "";
  series.forEach(function (n) {
    var h = max > 0 && n > 0 ? Math.max(12, Math.round(n / max * 100)) : 0;
    bars += '<i style="height:' + h + '%"></i>';
  });
  document.getElementById("activity-graph").innerHTML = bars;
  var costLine = "";
  if (s.cost) costLine = "Cost this month " + s.cost + (s.limit ? " of " + s.limit : "") + (s.costHot ? ". The limit is reached." : ".");
  document.getElementById("activity-cost").textContent = costLine;
  var files = "";
  (s.files || []).forEach(function (f) {
    var where = f.folder ? esc(f.folder) + ' / ' : '';
    files += '<div class="item" data-id="' + esc(f.id) + '"><b>' + where + esc(f.name) + '</b><span>' + esc(f.size) + '</span>';
    files += '<div class="acts"><button type="button" onclick="act(\'delete-file\', this.parentNode.parentNode.getAttribute(\'data-id\'))">Delete</button></div></div>';
  });
  document.getElementById("files-list").innerHTML = files || '<div class="item"><span>No files uploaded yet.</span></div>';
}
(function () {
  var PAGE = document.getElementById("page");
  var dock = document.getElementById("dock"), orb = document.getElementById("orb"), edge = document.getElementById("dockEdge"), fold = document.getElementById("fold");
  function showPage(v) { document.body.classList.toggle("page-open", v); }
  function showDock(v) { dock.classList.toggle("open", v); orb.setAttribute("aria-expanded", v ? "true" : "false"); }
  function openAll() { showPage(true); showDock(true); }
  dock.addEventListener("pointerenter", function (e) { if (e.pointerType === "mouse") openAll(); });
  edge.addEventListener("pointerenter", function (e) { if (e.pointerType === "mouse") openAll(); });
  orb.addEventListener("click", function () { if (dock.classList.contains("open")) showDock(false); else openAll(); });
  fold.addEventListener("click", function () { showDock(false); });
  document.addEventListener("keydown", function (e) {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "b") { e.preventDefault(); if (document.body.classList.contains("page-open")) { showPage(false); showDock(false); } else openAll(); }
    else if (e.key === "Escape") showDock(false);
  });
  var links = [].slice.call(document.querySelectorAll('.pill a[href^="#"]'));
  var io = new IntersectionObserver(function (es) {
    es.forEach(function (e) {
      if (!e.isIntersecting) return;
      links.forEach(function (l) { l.classList.toggle("on", l.getAttribute("href") === "#" + e.target.id); });
    });
  }, { rootMargin: "-40% 0px -55% 0px" });
  links.forEach(function (l) { var t = document.querySelector(l.getAttribute("href")); if (t) io.observe(t); });
})();
load();
setInterval(load, 500);
</script>
</body>
</html>
"""
