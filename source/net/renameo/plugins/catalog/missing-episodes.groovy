// Compare a series folder with the episode list on TheMovieDB and list the aired episodes you don't have.
// Pick the folder of one series (e.g. "Breaking Bad" with its season folders).

description "Find the aired episodes that are missing from a series folder (TheMovieDB)"

setting "specials", "Include specials (yes/no)", "no"

action("Find missing episodes", "Pick the folder of one series") { folder ->
    def have = [] as Set
    progress 0, 3, "Reading the folder"
    folder.eachFileRecurse(groovy.io.FileType.FILES) { f ->
        if (cancelled()) throw new InterruptedException()
        if (f.name ==~ /(?i).+\.(mkv|mp4|m4v|avi|mov|wmv|mpg|ts|m2ts|webm)$/) {
            net.renameo.media.MediaDetection.parseEpisodeNumber(f, false)?.each { sxe ->
                have << "${sxe.season < 0 ? 1 : sxe.season}x${sxe.episode}".toString()
            }
        }
    }

    def name = folder.name.replaceAll(/\s*[\(\[]?\b(19|20)\d{2}\b[\)\]]?\s*$/, '').trim()
    def locale = Locale.ENGLISH
    def service = net.renameo.WebServices.TheMovieDB_TV
    progress 1, 3, "Looking up \"${name}\" on TheMovieDB"
    def results = service.search(name, locale)
    if (!results) {
        log "TheMovieDB found no series called \"${name}\" (the folder name)"
        return
    }
    def series = results[0]
    def today = new net.renameo.web.SimpleDate(java.time.LocalDate.now())
    progress 2, 3, "Loading the episode list of ${series.name}"
    def episodes = service.getEpisodeList(series, net.renameo.web.SortOrder.Airdate, locale).findAll { e ->
        e.episode != null && e.airdate != null && e.airdate.compareTo(today) <= 0 && (e.season != 0 || settings.specials.toLowerCase() in ['yes', 'y', 'si', 'sì', 'true'])
    }
    progress 3, 3, "Comparing"
    def missing = episodes.findAll { e -> !have.contains("${e.season}x${e.episode}".toString()) }

    log "${series.name}: ${episodes.size()} aired episodes, ${have.size()} found, ${missing.size()} missing"
    missing.each { e -> log String.format("  S%02dE%02d  %s  (%s)", e.season, e.episode, e.title ?: '', e.airdate) }
}
