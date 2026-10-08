// Ask Plex to scan the folders that received files, instead of whole libraries.
// Token: see "Finding an authentication token" in the Plex support pages.

description "Refresh Plex (only the folders that changed) after renaming"

setting "server", "Server", "http://localhost:32400"
secret "token", "Token"

def plex = { String path ->
    def c = new URL("${settings.server.replaceAll('/+$', '')}${path}${path.contains('?') ? '&' : '?'}X-Plex-Token=${URLEncoder.encode(settings.token, 'UTF-8')}").openConnection()
    c.connectTimeout = 10000
    c.setRequestProperty("Accept", "application/xml")
    return c
}

// library sections and their folders, e.g. [id: "2", folders: ["/media/tv"]]
def sections = {
    new groovy.xml.XmlSlurper().parse(plex("/library/sections").inputStream).Directory.collect { d ->
        [id: d.@key.text(), title: d.@title.text(), folders: d.Location.collect { it.@path.text() }]
    }
}

def scanAll = {
    log "Plex: refresh all libraries, HTTP ${plex('/library/sections/all/refresh').responseCode}"
}

onRenameBatch { renames ->
    def folders = renames.collect { it[1].parentFile.path }.unique()
    def list
    try {
        list = sections()
    } catch (e) {
        log "Plex: could not read the libraries (${e.message}), refreshing all"
        scanAll()
        return
    }
    def scanned = false
    folders.each { folder ->
        def section = list.find { s -> s.folders.any { folder == it || folder.startsWith(it + File.separator) } }
        if (section) {
            def c = plex("/library/sections/${section.id}/refresh?path=${URLEncoder.encode(folder, 'UTF-8')}")
            log "Plex: scan ${folder} in ${section.title}, HTTP ${c.responseCode}"
            scanned = true
        }
    }
    // the files are in a folder Plex knows under another path (e.g. a network share): scan everything
    if (!scanned) scanAll()
}

action("Refresh now", "Ask Plex to scan all libraries") { folder -> scanAll() }
