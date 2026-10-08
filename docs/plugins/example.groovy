// A plugin that shows everything a plugin can do. Put it into the plugins folder
// (Plugins page > Open folder) and press Reload. The ready-made plugins on the
// Plugins page are written the same way: source/net/renameo/plugins/catalog.

description "Example: log renames, add {plugin.resolution} and a folder action"

// fields on the Plugins page; read them with settings.<key>
// in Docker set them with environment variables, e.g. PLUGIN_EXAMPLE_GREETING=Hi
setting "greeting", "Greeting", "Renamed"
secret "token", "Token"

// after every renamed file (app, watch folder, command line, Docker)
onRename { from, to ->
    log "${settings.greeting}: ${from.name} -> ${to.name}"
}

// once per batch, renames = [[from, to], ...]: the place to notify a media server
onRenameBatch { renames ->
    log "${renames.size()} files renamed"
}

// {plugin.resolution} in formats; m has the usual bindings ({n}, {y}, {height} with MediaInfo, ...)
binding("resolution") { m ->
    def height = 0
    try { height = m.height as int } catch (e) { }
    height >= 2000 ? "4K" : height >= 1000 ? "1080p" : height >= 700 ? "720p" : "SD"
}

// a button on the Plugins page, run for a folder you pick
action("Count videos", "Count the video files in a folder") { folder ->
    def count = 0
    folder.eachFileRecurse(groovy.io.FileType.FILES) { if (it.name ==~ /(?i).+\.(mkv|mp4|avi)$/) count++ }
    log "${count} videos in ${folder}"
}

// trash file   moves a file to the trash (in Docker: to the trash folder in the data folder)
