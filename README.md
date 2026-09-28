# ReNameo

**A modern media renamer for macOS (Apple Silicon).** ReNameo matches your movies, TV shows, anime, music and subtitles against online databases and gives them clean, consistent names that Plex, Jellyfin, Emby and Kodi recognize.

![ReNameo main window](docs/images/main-window.png)

ReNameo started from the open source code of FileBot 4.8.0 and has since been extensively reworked. It has a new interface, new matching and naming logic, media server profiles and support for current web APIs.

- [Features](#features)
- [Requirements](#requirements)
- [Install](#install)
- [Quick start](#quick-start)
- [Naming profiles](#naming-profiles)
- [Custom formats](#custom-formats)
- [Subtitles (OpenSubtitles)](#subtitles-opensubtitles)
- [Command line](#command-line)
- [Building from source](#building-from-source)
- [Project layout](#project-layout)
- [Credits and license](#credits-and-license)

## Features

**Recognition**
- Movies, TV series and anime from **TheMovieDB**, **TVmaze**, **AniDB** and **OMDb**, and music from **AcoustID** and ID3 tags.
- Smart parsing of release names: resolution, source, codecs, HDR / Dolby Vision, audio, languages and release groups are detected and ignored when searching.
- It keeps the title your library already uses: an Italian or original title in the folder name is preferred over the English one.
- Long running shows are supported: more than 100 seasons, more than 1000 episodes, and `S101E01` / `S2010E05` patterns.
- **Extras** are recognized: deleted scenes, featurettes, behind the scenes, interviews, trailers and shorts. They are attributed to their movie, not renamed as "part 2".
- A local offline index of popular titles gives instant cross-references (e.g. *Il Trono di Spade* → *Game of Thrones*).

**Renaming**
- Side-by-side **diff view** of the original and proposed names, with a confidence state for each match (exact, probable, uncertain).
- **Preview** before applying, **conflict detection** (duplicates and existing files), and **dry run**.
- **Undo** with ⌘Z, plus a persistent rename **history**, also after a restart.
- **Naming profiles** for Plex, Jellyfin, Emby and Kodi, plus Windows- and macOS-safe names.
- A **pattern editor** with live preview, and **presets** that store format, source, language and action together.
- **Manual match** when the automatic one is wrong, and batch actions on the selection.
- Rename or move, copy, hard link, symlink and clone. Subtitles are paired with their video.

**Interface**
- Dark and light themes, a collapsible sidebar and a compact mode.
- An **inspector** with poster, metadata and technical badges (4K, HDR, HEVC, DTS-HD, languages, …).
- A **command palette** (⌘K), keyboard shortcuts, and progress and notifications inside the main window.
- A built-in user guide in English and Italian (F1).

**Other tools**
- Episode lists, subtitle search and download, SFV / MD5 / SHA-1 checksums, a file filter, and list renaming.

## Requirements

- macOS 11 or later on Apple Silicon (arm64).
- [JDK 21](https://www.oracle.com/java/technologies/downloads/#java21) or later.
- Optional: [MediaInfo](https://mediaarea.net/en/MediaInfo) for resolution, codec and audio bindings:
  ```bash
  brew install libmediainfo
  ```

## Install

Download `ReNameo-mac-arm64.zip` from the Releases page, unzip it and move **ReNameo.app** to Applications. The app is ad-hoc signed, so the first time you open it right-click it and choose **Open**.

To use the same app from the terminal:

```bash
sudo ln -s /Applications/ReNameo.app/Contents/MacOS/ReNameo /usr/local/bin/renameo
renameo -help
```

## Quick start

1. Drag files or folders onto **Original Files**.
2. Press **Match**. ReNameo detects whether each file is a movie or an episode and picks the database.
3. Check the proposed names on the right. Anything uncertain is highlighted, and you can fix it with **Manual match**.
4. Press **Rename**. A preview shows every change before it is applied.

Did something go wrong? Press **⌘Z**, or open the history to undo an earlier rename.

## Naming profiles

**⌄ → Naming Profile** switches between ready-made conventions. Plex, Jellyfin, Emby and Kodi **rename files in place**: they stay in their folder, and only the file name follows the media server convention.

| Profile | Episode | Movie |
| --- | --- | --- |
| Plex | `Silo (2023) - S01E01 - Freedom Day.mkv` | `Dune (2021) {tmdb-438631}.mkv` |
| Jellyfin | `Silo (2023) - S01E01 - Freedom Day.mkv` | `Dune (2021) [tmdbid-438631].mkv` |
| Emby | `Silo (2023) - S01E01 - Freedom Day.mkv` | `Dune (2021) [tmdbid=438631].mkv` |
| Kodi | `Silo (2023) - S01E01 - Freedom Day.mkv` | `Dune (2021).mkv` |

To build a complete library instead (folders per movie, series and season), use a custom format with `{plex}`, `{jellyfin}`, `{emby}` or `{kodi}`, as described below.

## Custom formats

**Format** opens an editor with a live preview. Text inside `{…}` is replaced with data about the file; everything else is kept as written.

| Format | Result |
| --- | --- |
| `{n} ({y})` | `Dune (2021)` |
| `{n} - {s00e00} - {t}` | `Silo - S01E01 - Freedom Day` |
| `{n}/Season {s.pad(2)}/{n} - {s00e00} - {t}` | `Silo/Season 01/Silo - S01E01 - Freedom Day` |
| `{n} ({y}) [{vf} {vc}]` | `Dune (2021) [2160p HEVC]` |
| `{jellyfin.name}` | the Jellyfin file name only, the file stays where it is |
| `/Volumes/Media/{jellyfin}` | `Shows/Silo (2023) [tmdbid-125988]/Season 01/…` under `/Volumes/Media` |
| `{jellyfin.library('/Volumes/Media/TV')}` | moves into an existing library and reuses the folders already there (`Season 1`, `01. Silo (2023)`) |

Common bindings are `{n}` name, `{y}` year, `{t}` episode title, `{s}` / `{e}` season / episode, `{s00e00}`, `{sxe}`, `{absolute}`, `{vf}` resolution, `{vc}` / `{ac}` video / audio codec, `{lang}`, `{tmdbid}`, `{imdbid}`, `{genre}`, `{director}`, `{rating}`, `{extra}` and `{extraTitle}`. The [user guide](docs/USER_GUIDE_GUI.md#custom-formats) lists them all.

## Subtitles (OpenSubtitles)

ReNameo uses the current **opensubtitles.com** REST API. The legacy opensubtitles.org API no longer accepts new applications. To download subtitles:

1. Create a free account on [opensubtitles.com](https://www.opensubtitles.com/en/users/sign_up).
2. Create a free API key under [API consumers](https://www.opensubtitles.com/en/consumers).
3. In the **Subtitles** tab, press the user button and enter the API key, your username and your password.

The password is stored in the macOS Keychain. Free accounts have a daily download quota, which ReNameo shows after you sign in. The API does not allow uploads from applications, so upload subtitles on the website.

## Command line

The app bundle is also a command line tool:

```bash
# rename a TV show in place, test only
renameo -rename ~/Downloads/Silo --db TheMovieDB::TV --action test -non-strict

# sort downloads into a Jellyfin library
renameo -rename -r ~/Downloads --naming jellyfin --output /Volumes/Media -non-strict

# rename movies with a custom format
renameo -rename ~/Movies --db TheMovieDB --format "{n} ({y})/{n} ({y}) [{vf}]"

# fetch Italian subtitles
renameo -get-subtitles ~/Movies/Dune --lang it

# undo the last renames, print media info, check checksums
renameo -revert ~/Movies/Dune
renameo -mediainfo ~/Movies/Dune
renameo -check ~/Music/album
```

Run `renameo -help` for all options. The full reference with more examples is in the [command line guide](docs/USER_GUIDE_CLI.md).

## Documentation

| | English | Italiano |
| --- | --- | --- |
| Desktop app | [User guide](docs/USER_GUIDE_GUI.md) | [Guida](docs/GUIDA_GUI.md) |
| Command line | [CLI guide](docs/USER_GUIDE_CLI.md) | [Guida CLI](docs/GUIDA_CLI.md) |

The same guides are built into the app (Help → User Guide, or F1).

## Building from source

Prerequisites:

- JDK 21 and [Apache Ant](https://ant.apache.org) (`brew install ant`).
- The [JavaFX 21 SDK for macOS aarch64](https://gluonhq.com/products/javafx/), unpacked into `lib/javafx/` so that `lib/javafx/javafx-sdk-21.0.6/lib` exists.
- Your own API keys. Copy `profile.properties.example` to `profile.properties` and fill it in. That file is ignored by git, and at least a [TheMovieDB key](https://www.themoviedb.org/settings/api) is needed.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
ant resolve          # first time only: download dependencies into lib/ivy
ant fatjar           # build dist/ReNameo_1.0.0.jar
tools/make-app.sh    # build dist/ReNameo.app and dist/ReNameo-mac-arm64.zip
open dist/ReNameo.app
```

During development you can run the jar directly with `./renameo.sh` (GUI) or `./renameo.sh -help` (CLI).

Other useful tools:

- `python3 tools/md2html.py` regenerates the built-in help pages after you edit `docs/*.md`.
- `python3 tools/build_index.py` rebuilds the offline title index in `data/` from TheMovieDB.
- `tools/bash_completion.d/renameo` provides bash completion for the command line.

## Project layout

```
source/net/renameo/   application code (GUI in ui/, CLI in cli/, web services in web/, media detection in media/)
test/                 unit tests (JUnit 4)
docs/                 user guides (English and Italian) and images
data/                 offline title index bundled with the app
lib/                  bundled jars and native libraries (ivy/ and javafx/ are downloaded)
packaging/macos/      app icon
tools/                build scripts and helpers
```

## Credits and license

ReNameo was built on top of the open source code of **FileBot 4.8.0** © Reinhard Pointner, used under the *Modified Don't Be A Dick Public License* (see [LICENSE.md](LICENSE.md)). Since then it has been extensively reworked:
- a completely new interface and theme;
- new recognition, matching and naming logic;
- media server naming profiles;
- the OpenSubtitles REST client;
- the preview, undo and history workflow;
- a macOS-native build.

Metadata and images are provided by [TheMovieDB](https://www.themoviedb.org), [TVmaze](https://www.tvmaze.com), [AniDB](https://anidb.net), [OMDb](https://www.omdbapi.com), [Fanart.tv](https://fanart.tv), [AcoustID](https://acoustid.org) and [OpenSubtitles](https://www.opensubtitles.com). ReNameo is not endorsed or certified by any of them.
