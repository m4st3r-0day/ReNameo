// Ask Emby to scan its libraries after ReNameo renamed files.
// API key: Emby Settings > Advanced > API Keys

description "Refresh the Emby library after renaming"

setting "server", "Server", "http://localhost:8096"
secret "apiKey", "API key"

def refresh = {
    def c = new URL("${settings.server.replaceAll('/+$', '')}/emby/Library/Refresh").openConnection()
    c.requestMethod = "POST"
    c.setRequestProperty("X-Emby-Token", settings.apiKey)
    c.connectTimeout = 10000
    log "Emby: library refresh, HTTP ${c.responseCode}"
}

onRenameBatch { renames -> refresh() }

action("Refresh now", "Ask Emby to scan all libraries") { folder -> refresh() }
