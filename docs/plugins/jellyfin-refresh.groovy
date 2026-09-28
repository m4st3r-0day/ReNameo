// Tell Jellyfin to scan the library after ReNameo renamed something.
// Copy into the plugins folder and fill in your server address and API key
// (Jellyfin: Dashboard > API Keys > +).

def server = "http://localhost:8096"
def apiKey = "your-jellyfin-api-key"

description "Refresh the Jellyfin library after renaming"

def pending = false
onRename { from, to ->
    // many files are renamed at once: ask for one scan per batch, a few seconds later
    if (pending) return
    pending = true
    Thread.start {
        sleep 5000
        pending = false
        def connection = new URL("${server}/Library/Refresh").openConnection()
        connection.requestMethod = "POST"
        connection.setRequestProperty("X-Emby-Token", apiKey)
        log "Jellyfin refresh: HTTP ${connection.responseCode}"
    }
}
