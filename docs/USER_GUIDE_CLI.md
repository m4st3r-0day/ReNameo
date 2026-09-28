# ReNameo — Command Line Guide

Everything the desktop app does can also be done from the terminal, including unattended use (scripts, cron, watched folders). For the window see [USER_GUIDE_GUI.md](USER_GUIDE_GUI.md).

## How to run it

The app already contains the command. The most convenient way is to link it as `renameo`:

```bash
sudo ln -s /Applications/ReNameo.app/Contents/MacOS/ReNameo /usr/local/bin/renameo
renameo -version
```

Alternatively:

```bash
dist/ReNameo.app/Contents/MacOS/ReNameo -help     # freshly built app
./renameo.sh -help                                # from source (needs JFX_DIR)
java -jar dist/ReNameo_1.0.0.jar -help            # command line only
```

Without arguments the window opens; with arguments ReNameo works in the terminal and exits. Relative paths are resolved against the current folder.

Tab completion for bash/zsh is in `tools/bash_completion.d/renameo`:

```bash
source tools/bash_completion.d/renameo                                          # bash
autoload -U +X bashcompinit && bashcompinit && source tools/bash_completion.d/renameo   # zsh
```

## Golden rule: try with `--action test` first

`--action test` shows what would happen without touching any file:

```bash
renameo -rename -r ~/Downloads/Movies --action test -non-strict
```

```
Ignore 2 files that are not video, audio or subtitles
Rename movies using [TheMovieDB]
[TEST] from [~/Downloads/Movies/Dune (2021)/movie.mkv] to [~/Downloads/Movies/Dune (2021)/Dune (2021).mkv]
Processed 1 files
```

When the result is what you expect, drop `--action test`.

## Practical examples

### Rename movies and series in place

```bash
renameo -rename -r ~/Downloads/Movies -non-strict
```

Without `--db` ReNameo works out on its own, file by file, whether it is a movie, an episode or music. `-non-strict` lets it pick the best result automatically; without it, only certain matches are accepted.

### Organize a Plex or Jellyfin library

```bash
renameo -rename -r ~/Downloads --naming plex     --output /Volumes/Media -non-strict
renameo -rename -r ~/Downloads --naming jellyfin --output /Volumes/Media -non-strict
```

Result with `--naming plex`:

```
/Volumes/Media/Movies/Dune (2021) {tmdb-438631}/Dune (2021) {tmdb-438631}.mkv
/Volumes/Media/Movies/Dune (2021) {tmdb-438631}/Deleted Scenes/Sandworm Test.mkv
/Volumes/Media/TV Shows/Silo (2023) {tmdb-125988}/Season 01/Silo (2023) - S01E01 - Freedom Day.mkv
```

With `--naming jellyfin` the series folder is `Shows` and IDs are written as `[tmdbid-438631]`; `--naming emby` uses `[tmdbid=438631]`, `--naming kodi` no ID. Specials go into `Season 00`, extras into their own sub folder (`Deleted Scenes`, `Featurettes`, `Trailers`, …).

### Only series or only movies, with a specific source

```bash
renameo -rename ~/Series/Silo --db TheMovieDB::TV --format "{n} - {s00e00} - {t}" -non-strict
renameo -rename ~/Movies --db TheMovieDB --format "{n} ({y})"
renameo -rename ~/Anime/OnePiece --db AniDB --order Absolute -non-strict
renameo -rename ~/Music --db ID3 --format "{artist}/{album}/{pi.pad(2)} - {t}"
```

### Force the search when the name is ambiguous

```bash
renameo -rename ~/Series/Silo --q "Silo 2023" --db TheMovieDB::TV -non-strict
```

### Copy instead of move, and handle conflicts

```bash
renameo -rename -r ~/Downloads --naming plex --output /Volumes/Media --action copy --conflict auto -non-strict
```

`--conflict auto` replaces the existing file only if the new one is of better quality; `index` adds a number; `skip` (default) leaves it alone.

### Download missing subtitles

```bash
renameo -get-subtitles ~/Movies/Dune --lang it
```

Requires an opensubtitles.com API key and account: sign in once in the app (Subtitles tab, user button) and the command line uses the same key and account.

### Posters, fanart and metadata from TheMovieDB

```bash
renameo /Volumes/Media/Movies --apply artwork,metadata -r
```

`artwork` downloads posters and fanart from TheMovieDB next to the files; `metadata` stores the information in the file's extended attributes. `nfo` and `url` create `.nfo`/`.url` files for Kodi and are optional. `--apply` runs as a separate step, after renaming.

### More

```bash
renameo -list --q "Silo" --format "{s00e00} - {t}"          # episode list
renameo -mediainfo ~/Movies --format "{fn} {vf} {vc} {ac}"    # technical info
renameo -revert ~/Movies/Dune                                 # undo the last rename of those files
renameo -check ~/Movies                                       # create/verify SFV checksums
```

## Options

| Option | Values | Description |
| --- | --- | --- |
| `-rename` | | Rename the given files. |
| `-r` | | Include sub folders. |
| `--db` | `TheMovieDB::TV`, `TheTVDB`, `TVmaze`, `AniDB`, `TheMovieDB`, `OMDb`, `AcoustID`, `ID3`, `xattr` | Data source. Without it: automatic detection. |
| `--format` | expression | Pattern of the new name, see below. |
| `--naming` | `plex`, `jellyfin`, `emby`, `kodi` | Ready-made media server layout; use it with `--output`. Ignored if `--format` is also given. |
| `--output` | folder | Destination folder for relative names (the library root). |
| `--action` | `move` (default), `copy`, `keeplink`, `symlink`, `hardlink`, `clone`, `test` | What to do with the files. |
| `--conflict` | `skip` (default), `override`, `auto`, `index`, `fail` | What to do if the destination already exists. |
| `-non-strict` | | More aggressive matching, picks among several results by itself. |
| `--q` | text | Force the search name. |
| `--lang` | `it`, `en`, … | Language of the titles (default `en`). |
| `--order` | `Airdate` (default), `DVD`, `Absolute`, `AbsoluteAirdate` | Episode order. |
| `--filter` | expression | Drop results that don't satisfy the condition, e.g. `"y > 2000"`. |
| `--file-filter` | expression | Drop input files, e.g. `"f.length() > 100*1024*1024"`. |
| `-get-subtitles` | | Download missing subtitles. |
| `-list` | | Print an episode list (with `--q`). |
| `-mediainfo` | | Print technical information of the files. |
| `-revert` | | Restore the previous file names from the history. |
| `--apply` | `artwork`, `metadata`, `nfo`, `url` | Extra tasks on already renamed files. |
| `-exec` | command | Run a command for each file, e.g. `-exec echo {f}`. |
| `--log` | `all`, `fine`, `info`, `warning` | How much detail to print. |
| `--log-file` | file | Save the log to a file. |
| `-no-history`, `-clear-history` | | Disable or clear the rename history. |
| `-clear-cache`, `-clear-prefs` | | Clear the cache or the settings. |
| `-version`, `-help` | | Version and help. |

The command exits with code **0** on success and **1** if no file was processed or an error occurred: handy in scripts.

## Formats (`--format`)

Text in `{…}` is replaced with the file's data; the rest stays as is. A `/` creates sub folders.

| Format | Result |
| --- | --- |
| `{n} ({y})` | `Dune (2021)` |
| `{n} - {s00e00} - {t}` | `Silo - S01E01 - Freedom Day` |
| `{n}/Season {s.pad(2)}/{n} - {s00e00} - {t}` | `Silo/Season 01/Silo - S01E01 - Freedom Day` |
| `{n} ({y}) [{vf} {vc}]` | `Dune (2021) [1080p x265]` |
| `/Volumes/Media/{plex}` | full Plex layout |
| `{n} ({y})/{extra}/{extraTitle}` | `Dune (2021)/Deleted Scenes/Sandworm Test` (for extras) |

Main bindings:

| Binding | Content |
| --- | --- |
| `{n}` `{y}` | name and year from the database (correct capitalization) |
| `{s}` `{e}` `{s00e00}` `{sxe}` `{absolute}` `{t}` | season, episode, codes, absolute number, title |
| `{vf}` `{vc}` `{ac}` `{channels}` | resolution, video codec, audio codec, channels |
| `{lang}` `{subt}` | language (for subtitles) |
| `{tmdbid}` `{imdbid}` `{genre}` `{director}` `{rating}` `{certification}` | TheMovieDB data |
| `{plex}` `{jellyfin}` `{emby}` `{kodi}` | full path for the media server (extras included) |
| `{extra}` `{extraTitle}` | extras folder and title (empty for the main feature) |
| `{fn}` `{ext}` `{f}` `{folder}` | name, extension, path and folder of the original file |

A format can also live in a file: `--format ~/formats/series.groovy`.

## Which files are taken

Only **video, audio and subtitle files**, plus BDMV/VIDEO_TS disc folders. Everything else (`.nfo`, `.jpg`, `.png`, `.txt`, …) is ignored and the log says so (`Ignore N files that are not video, audio or subtitles`). *Samples* are always excluded.

## Extras (deleted scenes, trailers, …)

A file counts as an extra if:

- it sits in a folder like `Deleted Scenes`, `Behind The Scenes`, `Featurettes`, `Interviews`, `Trailers`, `Shorts`, `Scenes`, `Extras`, `Other`, `Scene Eliminate`, `Dietro le Quinte`, `Contenuti Speciali`;
- or it has a Plex style suffix (`-deleted`, `-trailer`, `-featurette`, `-interview`, `-behindthescenes`, `-scene`, `-short`, `-other`);
- or it contains words like *deleted scenes*, *making of*, *featurette*, *trailer*, *bloopers*.

The extra is attributed to the movie of the parent folder and keeps its own title:
- with `--naming` or `{plex}`/`{jellyfin}` it goes into the right sub folder;
- without a format it becomes `Title-deleted.mkv`, `Title-trailer.mkv` and so on, which Plex and Jellyfin recognize next to the movie.

## Long series and anime

Seasons above 100 and episodes above 1000 are recognized: `S01E1071`, `S101E05`, `S1001E02`, `3x1071`, `Episode 1071`, `EP1071`, `S01E1071-E1072`, `Show - 1071 - Title`, `[Group] Show - 1100v2 [1080p]` and the folders `Season 101`, `Stagione 120`, `S120`. For anime with absolute numbering use `--order Absolute`.

## Technical video information

The bindings `{vf}`, `{vc}`, `{ac}`, `{channels}`, the `-mediainfo` command, `--conflict auto` (which compares quality) and the detection of multi-part movies read the files with the MediaInfo library. On macOS install it once with Homebrew:

```bash
brew install libmediainfo
```

ReNameo finds it by itself in `/opt/homebrew/lib`. Without it these values stay empty but renaming still works.

## Automation

Example script that sorts the downloads folder into a Jellyfin library every night and logs the result:

```bash
#!/bin/bash
renameo -rename -r "$HOME/Downloads/Complete" \
  --naming jellyfin --output /Volumes/Media \
  --action move --conflict auto -non-strict \
  --log-file "$HOME/Library/Logs/renameo.log"
```

## Where data is stored

- Cache, history and logs: `~/.renameo` (`~/.filebot` folders from earlier versions are adopted automatically).
- Settings: Java user preferences (`-clear-prefs` to reset them).
