// List episodes and movies that are in a folder more than once (e.g. a 720p and a 1080p copy).
// Nothing is deleted: the list shows the size of each copy so you can decide.

description "Find episodes and movies you have more than once"

action("Find duplicates", "Pick a library folder, e.g. your TV shows or movies") { folder ->
    def groups = [:].withDefault { [] }
    def titles = [:]
    progress "Looking for videos"
    def videos = []
    folder.eachFileRecurse(groovy.io.FileType.FILES) { f ->
        if (cancelled()) throw new InterruptedException()
        if (f.name ==~ /(?i).+\.(mkv|mp4|m4v|avi|mov|wmv|mpg|ts|m2ts|webm)$/ && !(f.name =~ /(?i)\bsample\b/)) videos << f
    }
    videos.eachWithIndex { f, i ->
        if (cancelled()) throw new InterruptedException()
        progress i + 1, videos.size(), f.name
        def sxe = net.renameo.media.MediaDetection.parseEpisodeNumber(f, true)
        def key
        if (sxe) {
            // the series folder is the one above the season folder, or the parent itself
            def series = f.parentFile.name ==~ /(?i)(season|staffel|stagione|saison|temporada)\s*\d+|s\d+|specials/ ? f.parentFile.parentFile : f.parentFile
            key = "${series.name}  ${sxe.collect { String.format('S%02dE%02d', it.season < 0 ? 1 : it.season, it.episode) }.join('-')}"
        } else {
            // movies: title and year, e.g. "Avatar (2009)" from "Avatar (2009) 1080p.mkv" or "Avatar.2009.720p.mkv"
            def m = f.name =~ /^(.+?)[\s._(\[]+((?:19|20)\d{2})\b/
            if (!m.find()) return
            def title = "${m.group(1).replaceAll(/[._]/, ' ').trim()} (${m.group(2)})"
            key = title.toLowerCase()
            titles.putIfAbsent(key, title)
        }
        groups[key.toString()] << f
    }

    def dupes = groups.findAll { k, v -> v.size() > 1 }.sort { it.key }
    log "${dupes.size()} ${dupes.size() == 1 ? 'title is' : 'titles are'} there more than once"
    dupes.each { k, files ->
        log titles[k] ?: k
        files.sort { -it.length() }.each { f -> log String.format("  %7.2f GB  %s", f.length() / 1e9, f.path) }
    }
}
