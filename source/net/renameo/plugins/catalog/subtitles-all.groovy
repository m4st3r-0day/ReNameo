// Download missing subtitles from OpenSubtitles, for a whole folder or right after renaming.
// Needs an OpenSubtitles API key and login (Subtitles page, user button).

description "Download missing subtitles for a folder, or automatically after renaming"

setting "languages", "Languages", "en"
setting "auto", "After renaming (yes/no)", "no"

def download = { Collection<File> files ->
    def cli = new net.renameo.cli.CmdlineOperations()
    def codes = settings.languages.split(/[,\s]+/).findAll { it }
    codes.eachWithIndex { code, i ->
        if (cancelled()) throw new InterruptedException()
        progress i, codes.size(), "Language ${code}"
        def language = net.renameo.Language.getLanguage(code)
        if (language == null) {
            log "Unknown language: ${code}"
            return
        }
        def found = cli.getMissingSubtitles(files, null, language, net.renameo.subtitle.SubtitleFormat.SubRip, java.nio.charset.StandardCharsets.UTF_8, net.renameo.subtitle.SubtitleNaming.MATCH_VIDEO_ADD_LANGUAGE_TAG, true)
        log "${language.name}: ${found.size()} subtitles downloaded"
        found.each { log "  ${it.name}" }
    }
}

onRenameBatch { renames ->
    if (settings.auto.toLowerCase() in ['yes', 'y', 'true', 'si', 'sì', '1']) {
        download(renames.collect { it[1] })
    }
}

action("Get missing subtitles", "Look for missing subtitles for every video in a folder (and its subfolders)") { folder ->
    def files = []
    folder.eachFileRecurse(groovy.io.FileType.FILES) { files << it }
    download(files)
}
