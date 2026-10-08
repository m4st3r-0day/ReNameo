// Ask Kodi to update its video library after renaming.
// Kodi: Settings > Services > Control > "Allow remote control via HTTP".

description "Update the Kodi video library after renaming"

setting "server", "Server", "http://localhost:8080"
setting "user", "User", "kodi"
secret "password", "Password"

def scan = {
    def c = new URL("${settings.server.replaceAll('/+$', '')}/jsonrpc").openConnection()
    c.requestMethod = "POST"
    c.doOutput = true
    c.connectTimeout = 10000
    c.setRequestProperty("Content-Type", "application/json")
    if (settings.password) {
        c.setRequestProperty("Authorization", "Basic " + "${settings.user}:${settings.password}".bytes.encodeBase64())
    }
    c.outputStream.withWriter("UTF-8") { it << '{"jsonrpc":"2.0","method":"VideoLibrary.Scan","id":1}' }
    log "Kodi: library scan, HTTP ${c.responseCode}"
}

onRenameBatch { renames -> scan() }

action("Scan now", "Ask Kodi to update the video library") { folder -> scan() }
