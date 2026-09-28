// Tell Plex to scan its libraries after ReNameo renamed something.
// Copy into the plugins folder and fill in your server address and token
// (how to find the token: https://support.plex.tv/articles/204059436).

def server = "http://localhost:32400"
def token = "your-plex-token"

description "Refresh the Plex libraries after renaming"

def pending = false
onRename { from, to ->
    if (pending) return
    pending = true
    Thread.start {
        sleep 5000
        pending = false
        def connection = new URL("${server}/library/sections/all/refresh?X-Plex-Token=${token}").openConnection()
        log "Plex refresh: HTTP ${connection.responseCode}"
    }
}
