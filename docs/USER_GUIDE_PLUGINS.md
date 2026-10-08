# Writing plugins and scripts

You can extend ReNameo with small text files written in **Groovy**, a simple language that looks like Java and JavaScript. You don't need to be an experienced programmer: almost every example in this guide can be copied and adapted.

There are two ways to do it:

| | Plugin | Script |
| --- | --- | --- |
| What it is | A file that stays active and **reacts** to what ReNameo does | A file that **you run** when you need it, like a command |
| When it runs | After every rename, when you press one of its buttons, when a format uses `{plugin.name}` | Only when you start it with `renameo -script …` |
| Where it goes | `plugins` folder | `scripts` folder |
| Used by | App, watch folder, command line, Docker | Command line, Docker, cron |
| Examples | Tell Jellyfin to rescan, send a notification, clean up folders | Sort downloads into *TV Shows* and *Movies*, get subtitles for the whole library |

Both folders are in the ReNameo data folder:

| System | Data folder |
| --- | --- |
| macOS | `~/.renameo` |
| Linux | `~/.local/share/renameo` |
| Windows | `%APPDATA%\ReNameo` |
| Docker | `/config` |

Any text editor will do (TextEdit in plain text mode, Notepad, Visual Studio Code …). Save with the `.groovy` extension and UTF-8 encoding.

## Your first plugin

1. Open the **Plugins** page in the sidebar and press **Open folder**.
2. Create a file `hello.groovy` in that folder with these lines:

   ```groovy
   description "My first plugin"

   onRename { from, to ->
       log "Renamed ${from.name} to ${to.name}"
   }
   ```

3. Go back to ReNameo and press **Reload**: the plugin appears under *Installed* with its description.
4. Rename any file. The **Plugin log** at the bottom of the page shows *Renamed … to …*.

That's it: this is a complete plugin. The file name (without `.groovy`) is the name of the plugin.

## What a plugin can do

A plugin is a list of instructions. Each one is optional: use only the ones you need.

| Instruction | What it's for |
| --- | --- |
| `description "text"` | The description shown on the Plugins page |
| `setting "key", "Label", "default value"` | A field on the Plugins page, e.g. the address of a server |
| `secret "key", "Label"` | Like `setting`, but the text is masked (password, token, API key) |
| `settings.key` | Reads what the user entered in a field |
| `onRename { from, to -> … }` | Called after **every file** that was renamed |
| `onRenameBatch { renames -> … }` | Called **once per batch** of files renamed together |
| `binding("name") { m -> … }` | Adds `{plugin.name}` for formats |
| `action("Name", "Explanation") { folder -> … }` | A button on the Plugins page that works on a folder the user picks |
| `log "message"` | Writes to the Plugin log (and to the terminal on the command line) |
| `progress done, total, "text"` | Shows the progress bar on the plugin card, e.g. *12 of 40* |
| `cancelled()` | Turns true when the user presses **Stop** |
| `trash file` | Moves a file to the trash |

### Settings: `setting`, `secret` and `settings`

```groovy
setting "server", "Server address", "http://localhost:8096"
secret  "apiKey", "API key"

onRenameBatch { renames ->
    log "Using ${settings.server}"
}
```

On the Plugins page the plugin shows the fields *Server address* and *API key*. What you type is saved as soon as you leave the field, and the plugin reads it with `settings.server` and `settings.apiKey`. An empty field uses the default value.

### Reacting to renames: `onRename` and `onRenameBatch`

`from` and `to` are the file before and after renaming: `to.name` is the file name, `to.parentFile` the folder, `to.path` the full path.

```groovy
onRename { from, to ->
    log "${from.name} → ${to.path}"
}
```

When you rename 50 episodes at once, `onRename` is called 50 times. For things to do only once (tell a media server, send a notification) use `onRenameBatch`: it gets the list of all `[from, to]` pairs.

```groovy
onRenameBatch { renames ->
    log "Renamed ${renames.size()} files"
    renames.each { pair -> log "  ${pair[1].name}" }   // pair[0] = before, pair[1] = after
}
```

Test runs (action **test**) don't call plugins, because they don't move anything.

### New values for formats: `binding`

```groovy
binding("quality") { m ->
    def height = 0
    try { height = m.height as int } catch (e) { }     // the video height comes from MediaInfo
    height >= 2000 ? "4K" : height >= 1000 ? "1080p" : height >= 700 ? "720p" : "SD"
}
```

Now formats can use `{n} ({y}) [{plugin.quality}]` to get e.g. `Inception (2010) [1080p]`. `m` holds the same values as formats: `m.n`, `m.y`, `m.s`, `m.e`, `m.height` …

### Buttons: `action`

```groovy
action("Count videos", "Count the videos in a folder") { folder ->
    def count = 0
    folder.eachFileRecurse(groovy.io.FileType.FILES) { f ->
        if (f.name ==~ /(?i).+\.(mkv|mp4|avi)$/) count++
    }
    log "${count} videos in ${folder}"
}
```

The plugin card gets a **Count videos** button. When you press it, ReNameo asks for a folder, runs the code in the background and shows the result in the Plugin log.

### Progress bar and Stop: `progress` and `cancelled()`

While a plugin works, its card shows what it's doing, for how long, and a moving bar with a **Stop** button. For long jobs, tell ReNameo how far you are: the bar becomes exact and the user sees *12 of 40 · file-name*.

```groovy
action("Check videos", "Example with progress and Stop") { folder ->
    def videos = folder.listFiles().findAll { it.name ==~ /(?i).+\.(mkv|mp4)$/ }
    for (int i = 0; i < videos.size(); i++) {
        if (cancelled()) break              // the user pressed Stop
        progress i + 1, videos.size(), videos[i].name
        // … work on videos[i] …
    }
}
```

`progress "text"` shows just a message, without numbers. Inside closures like `each` or `eachFileRecurse`, where `break` isn't allowed (and `return` only skips one item), use `if (cancelled()) throw new InterruptedException()`: ReNameo records it as *stopped*, not as an error. Stop also tries to interrupt waiting, but not everything can be interrupted: checking `cancelled()` is the reliable way.

## Complete example: Telegram notifications

This plugin sends a Telegram message after every rename, has a button to try it and adds `{plugin.quality}` to formats.

```groovy
// Send a Telegram message when ReNameo renamed files.

description "Telegram message after every rename"

secret  "token", "Bot token"
setting "chat",  "Chat ID"

def send = { String text ->
    def url = "https://api.telegram.org/bot${settings.token}/sendMessage"
    def c = new URL(url).openConnection()
    c.requestMethod = "POST"
    c.doOutput = true
    c.setRequestProperty("Content-Type", "application/json")
    c.outputStream.withWriter("UTF-8") { out ->
        out << groovy.json.JsonOutput.toJson([chat_id: settings.chat, text: text])
    }
    log "Telegram answered ${c.responseCode}"
}

onRenameBatch { renames ->
    def names = renames.collect { it[1].name }
    send("ReNameo renamed ${names.size()} files:\n" + names.take(10).join("\n"))
}

action("Send a test message") { folder ->
    send("Test from ReNameo: it works!")
}

binding("quality") { m ->
    def height = 0
    try { height = m.height as int } catch (e) { }
    height >= 2000 ? "4K" : height >= 1000 ? "1080p" : height >= 700 ? "720p" : "SD"
}
```

To use it:

1. In Telegram, write to **@BotFather**, create a bot with `/newbot` and copy the token.
2. Send any message to your bot, then open `https://api.telegram.org/bot<TOKEN>/getUpdates` in a browser: the number after `"chat":{"id":` is the Chat ID.
3. Save the plugin as `telegram.groovy` in the plugins folder, press **Reload**, fill in both fields and press **Send a test message**.

The Plugin log shows *Telegram answered 200* when everything works. *401* means a wrong token, *400* a wrong Chat ID.

## Testing and fixing a plugin

- Press **Reload** on the Plugins page after every change to the file.
- If the plugin has a mistake, its card shows the error in red, e.g. `MissingPropertyException: No such property: sttings`: usually a misspelled name.
- In the **Plugin log** every line has the time, the plugin name and a mark: ▶ started, ✓ done (with the duration), ■ stopped, ✕ error. The log keeps the last 2000 lines, also those written while the page was closed.
- You can filter by plugin or show only errors, select lines and copy them (⌘C, or right-click → *Copy*), save them with *Save…* or empty the log with *Clear*. Handy for sending an error to whoever helps you.
- Below the card's buttons, *Last run* tells when the plugin last worked and how it went.
- Use `log` to see what happens: `log "value: ${something}"`.
- To try `onRename` without touching your real files, copy a few videos into a test folder and rename them there (action **move**, not **test**).
- The **On** switch turns a plugin off without deleting it. The trash button removes it.

A few Groovy things that trip people up:

- `==~` matches the **whole** text, `=~` looks for **a part**: `"movie.mkv" ==~ /.+\.mkv/` is true, `"movie.mkv" ==~ /\.mkv/` is not.
- Text with `${…}` isn't a plain `String`: add `.toString()` before putting it into a list or set you compare against.
- In the app plugins run in the background; on the command line the program waits for them to finish. Avoid long waits.

## Plugins on Docker and NAS

Without a desktop, settings come from environment variables: `PLUGIN_` + plugin name + setting name, in upper case and with `_` instead of dashes.

```yaml
environment:
  - RENAMEO_PLUGINS=jellyfin-refresh,notify     # install ready-made plugins
  - PLUGIN_JELLYFIN_REFRESH_SERVER=http://jellyfin:8096
  - PLUGIN_JELLYFIN_REFRESH_APIKEY=your-key
  - PLUGIN_TELEGRAM_TOKEN=123456:ABC…            # your own telegram.groovy in /config/plugins
  - PLUGIN_TELEGRAM_CHAT=42
```

Environment variables win over what is entered on the Plugins page. The exact name of each variable also shows when you hover over a settings field.

## Your first script

A script is a `.groovy` file in the `scripts` folder that you run from the terminal.

1. Create `scripts/hello.groovy`:

   ```groovy
   log.info "Hello! You passed ${args.size()} folders or files"
   args.each { log.info "  ${it}" }
   ```

2. Run it:

   ```bash
   renameo -script fn:hello ~/Downloads
   ```

`fn:hello` means "the script `hello.groovy` in the `scripts` folder". Any path works too: `renameo -script /path/to/my-script.groovy ~/Downloads`.

### What a script gets

| Name | What it holds |
| --- | --- |
| `args` | The files and folders written after the script name |
| `_def` | The values passed with `--def name=value`, e.g. `_def.action` |
| `log.info "…"`, `log.warning "…"` | Writes to the terminal |
| `now` | The current date and time |

### ReNameo functions in scripts

| Function | What it does |
| --- | --- |
| `rename(file: list, …)` or `rename(folder: folder, …)` | Renames, like `renameo -rename` |
| `getMissingSubtitles(folder: folder, lang: "en")` | Downloads missing subtitles |
| `getSubtitles(file: list, lang: "en")` | Downloads subtitles even if they exist |
| `check(file: list)` / `compute(file: list)` | Verifies or creates checksum files (SFV, MD5 …) |
| `extract(file: archive)` | Extracts RAR, ZIP, 7z archives |
| `fetchEpisodeList(query: "Dark", db: "TheMovieDB::TV", format: "{s00e00} {t}")` | The episode list of a series |
| `getMediaInfo(file, "{vf} {ac}")` | Technical information about a video |
| `parseEpisodeNumber(file)` | Season and episode read from the name (nothing for a movie) |
| `detectSeriesName(file)` / `detectMovie(file, false)` | Recognizes the series or movie |
| `execute("command", "argument")` | Runs an external program |
| `die "message"` | Stops the script with an error |

The parameters of `rename` are the same as on the command line: `db` (`TheMovieDB`, `TheMovieDB::TV`, `TVmaze`, `AniDB` …), `format`, `action` (`test`, `move`, `copy`, `hardlink`, `symlink`), `output`, `conflict`, `lang`, `query`, `order`, `filter` and `strict: false` to be more tolerant (needed when several series are renamed together).

## Complete example: sorting downloads

This script takes every video in a folder, including subfolders, and puts episodes into *TV Shows* and movies into *Movies*, with proper names.

```groovy
// Rename the videos of a folder: series into "TV Shows", movies into "Movies".
// Usage: renameo -script fn:sort-downloads ~/Downloads --def action=move target=/Volumes/Media
// Without --def action=... it only does a test run and changes nothing.

def action = _def.action ?: 'test'
def target = _def.target ?: "${System.getProperty('user.home')}/Media"

// every video in the given folders, including subfolders
def videos = []
args.each { folder ->
    folder.eachFileRecurse(groovy.io.FileType.FILES) { f ->
        if (f.name ==~ /(?i).+\.(mkv|mp4|avi|m4v)$/) videos << f
    }
}

// S01E02 or 1x02 in the name means an episode, otherwise a movie
def (episodes, movies) = videos.split { parseEpisodeNumber(it) != null }
log.info "${episodes.size()} episodes, ${movies.size()} movies"

if (episodes) {
    rename(file: episodes, db: 'TheMovieDB::TV', action: action, strict: false,
           format: "${target}/TV Shows/{n}/Season {s}/{n} - {s00e00} - {t}")
}
if (movies) {
    rename(file: movies, db: 'TheMovieDB', action: action, strict: false,
           format: "${target}/Movies/{n} ({y})/{n} ({y})")
}
```

First do a test run, which shows what would happen without changing anything:

```bash
renameo -script fn:sort-downloads ~/Downloads --def target=/Volumes/Media
```

If the names look right, run it for real:

```bash
renameo -script fn:sort-downloads ~/Downloads --def action=move target=/Volumes/Media
```

Renames done by a script go into the history (they can be undone in the app) and trigger plugins, e.g. the Jellyfin refresh.

### Running it on its own

On macOS and Linux, `cron` can start the script at fixed times (`crontab -e`). This line runs it every hour (`which renameo` shows the full path to use):

```bash
0 * * * * /usr/local/bin/renameo -script fn:sort-downloads /path/to/Downloads --def action=move target=/path/to/Media
```

With Docker, put the script into `/config/scripts` and run it like this:

```bash
docker run --rm -e TMDB_API_KEY=your-key -v /path/to/config:/config -v /path/to/media:/media \
  ghcr.io/m4st3r-0day/renameo -script fn:sort-downloads /media/downloads --def action=move target=/media
```

## Security

Plugins and scripts can do anything you can do: read, move and delete files, use the network. Only install files you trust, and read them before using them. **Formats**, on the other hand, are sandboxed: `{…}` can't run programs or change files, even when it uses `{plugin.name}`.

## Sharing a plugin

A plugin is a single file: to share it, send the `.groovy` file. Whoever gets it puts it into their `plugins` folder and presses **Reload**. The ready-made plugins of the app are written the same way and make a good starting point: they are in `source/net/renameo/plugins/catalog` in the repository, and every installed plugin can be opened and changed with **Open folder**.
