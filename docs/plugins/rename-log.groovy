// Keep a plain text log of every rename in your home folder,
// and add {plugin.resolution} to formats as an example of a custom binding.

def logFile = new File(System.getProperty("user.home"), "renameo-renames.log")

description "Log every rename to ~/renameo-renames.log"

onRename { from, to ->
    logFile << "${new Date().format('yyyy-MM-dd HH:mm')}  ${from}  ->  ${to}\n"
}

// {plugin.resolution} gives "4K", "1080p" or "SD"; m are the usual format bindings ({vf}, {n}, {y} …)
binding("resolution") { m ->
    def height = 0
    try { height = m.height as int } catch (e) { } // needs MediaInfo
    height >= 2000 ? "4K" : height >= 1000 ? "1080p" : height >= 700 ? "720p" : "SD"
}
