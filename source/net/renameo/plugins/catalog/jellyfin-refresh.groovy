// Ask Jellyfin to scan its libraries after ReNameo renamed files.
// API key: Jellyfin Dashboard > API Keys > +

description "Refresh the Jellyfin library after renaming"

setting "server", "Server", "http://localhost:8096"
secret "apiKey", "API key"

def refresh = {
    def c = new URL("${settings.server.replaceAll('/+$', '')}/Library/Refresh").openConnection()
    c.requestMethod = "POST"
    c.setRequestProperty("X-Emby-Token", settings.apiKey)
    c.connectTimeout = 10000
    log "Jellyfin: library refresh, HTTP ${c.responseCode}"
}

// one scan per batch of renamed files
onRenameBatch { renames -> refresh() }

action("Refresh now", "Ask Jellyfin to scan all libraries") { folder -> refresh() }
