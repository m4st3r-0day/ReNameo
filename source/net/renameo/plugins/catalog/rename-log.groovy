// Keep a plain text log of every rename.

description "Write every rename to a text file"

setting "file", "Log file", new File(System.getProperty("user.home"), "renameo-renames.log").path

onRenameBatch { renames ->
    def time = new Date().format("yyyy-MM-dd HH:mm")
    new File(settings.file).withWriterAppend("UTF-8") { out ->
        renames.each { out << "${time}  ${it[0]}  ->  ${it[1]}\n" }
    }
}
