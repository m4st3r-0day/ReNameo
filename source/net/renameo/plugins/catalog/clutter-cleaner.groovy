// When files were moved out of a download folder, clean up what was left behind:
// release notes, links, checksums, sample videos and the empty folders.
// A folder is only cleaned when no video, audio or subtitle file is left in it, and everything goes to the trash
// (without a desktop, e.g. in Docker, to the "trash" folder in the ReNameo data folder).

description "Clean up leftovers (samples, .nfo, .txt …) and empty folders after files were moved"

setting "extensions", "Leftover types", "txt,nfo,url,lnk,exe,sfv,md5,nzb,srr,srs,par2,jpg,jpeg,png,db,ini"

def MEDIA = ~/(?i)\.(mkv|mp4|m4v|avi|mov|wmv|mpg|mpeg|ts|m2ts|webm|mp3|flac|m4a|aac|ogg|wav|srt|ass|ssa|sub|idx|sup|vtt)$/
def VIDEO = ~/(?i)\.(mkv|mp4|m4v|avi|mov|wmv|mpg|mpeg|ts|m2ts|webm)$/

def isSample = { File f ->
    f.name =~ VIDEO && f.length() < 300L * 1024 * 1024 && (f.name =~ /(?i)(\b|_)sample(\b|_)/ || f.parentFile.name ==~ /(?i)samples?/)
}
def isLeftover = { File f ->
    def ext = f.name.contains('.') ? f.name.substring(f.name.lastIndexOf('.') + 1).toLowerCase() : ''
    ext in (settings.extensions.toLowerCase().split(/[,\s]+/) as List) || isSample(f)
}
def hasMedia = { File dir ->
    def found = false
    dir.eachFileRecurse(groovy.io.FileType.FILES) { f -> if (f.name =~ MEDIA && !isSample(f)) found = true }
    found
}
def removeEmpty
removeEmpty = { File dir ->
    dir.listFiles()?.findAll { it.directory }?.each { removeEmpty(it) }
    if (dir.list()?.every { it == '.DS_Store' || it == 'Thumbs.db' }) {
        dir.listFiles()*.delete()
        if (dir.delete()) log "Removed empty folder ${dir}"
    }
}
def clean = { File dir ->
    if (!dir.directory || hasMedia(dir)) return
    dir.eachFileRecurse(groovy.io.FileType.FILES) { f ->
        if (isLeftover(f)) {
            trash f
            log "Trashed ${f}"
        }
    }
    removeEmpty(dir)
}

onRenameBatch { renames ->
    // only folders that files were moved out of; renaming in place leaves the folder alone
    def targets = renames.collect { it[1].parentFile.canonicalFile }
    def sources = renames.findAll { it[0].parentFile.canonicalFile != it[1].parentFile.canonicalFile }.collect { it[0].parentFile.canonicalFile }.unique()
    sources.findAll { src -> !targets.any { t -> t.path == src.path || t.path.startsWith(src.path + File.separator) } }.each { clean(it) }
}

action("Clean a folder", "Trash sample videos and leftovers in the subfolders that have no media left, and remove empty folders") { folder ->
    progress "Looking for sample videos"
    folder.eachFileRecurse(groovy.io.FileType.FILES) { f ->
        if (cancelled()) throw new InterruptedException()
        if (isSample(f)) {
            trash f
            log "Trashed sample ${f}"
        }
    }
    def subfolders = folder.listFiles()?.findAll { it.directory } ?: []
    subfolders.eachWithIndex { dir, i ->
        if (cancelled()) throw new InterruptedException()
        progress i + 1, subfolders.size(), dir.name
        clean(dir)
    }
}
