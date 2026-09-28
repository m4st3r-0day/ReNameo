# ReNameo — Desktop App Guide

ReNameo renames and organizes **movies, TV shows, anime, music and subtitles** using data from TheMovieDB and other online databases. This guide explains how to use the app window; for the terminal see [USER_GUIDE_CLI.md](USER_GUIDE_CLI.md).

## Starting the app

- **App**: open `ReNameo.app` (in `dist/` after a build, or wherever you copied it, e.g. `/Applications`).
- **From source**: `./renameo.sh` in the project folder (needs the JavaFX SDK, see README).

On first launch ReNameo automatically picks up settings, presets and history from earlier versions.

## The window

| Area | What it contains |
| --- | --- |
| Top bar | Name and version, button to collapse the sidebar (⌘\\), **command palette** (⌘K), help (F1) and status indicator: green *Ready*, blue *Matching…*, orange *N to review*. |
| Sidebar | **Media Tools**: Rename, Episodes, Subtitles. **Utilities**: SFV, Filter, List. At the bottom, *Settings*. |
| Header | Section title and, in Rename, the main actions: **Match**, **Fetch Metadata**, **Format**, Presets, **Undo**, History and show/hide inspector (⌘I). |
| Content | In Rename: *Original Files* on the left, *Proposed Names* in the middle, *Metadata* on the right. |
| Status bar | Files added, files matched, warnings, progress and the **Rename** button with its options menu (⌄). |

## Renaming: the typical workflow

### 1. Add files

Drag files or folders onto *Drag & drop media files here*, or use **Browse Files**. Folders are read recursively.

ReNameo only takes **video, audio and subtitle files** (MKV, MP4, AVI, MP3, FLAC, SRT, ASS, …) and BDMV/VIDEO_TS disc folders. Files such as `.nfo`, `.jpg`, `.png`, `.txt` are ignored: images (posters, fanart) come from TheMovieDB, not from local files.

Each row shows the type (movie, series, music), the name, size, format and folder.

### 2. Find the data: Match or Fetch Metadata

- **Match** (⌘M) identifies movies, episodes and music on its own, file by file: the right choice almost every time. If a group of files is misclassified (e.g. a brand new movie taken for a series), ReNameo automatically retries with the other type without asking; it only asks you about genuinely ambiguous cases.
- **Fetch Metadata** opens the list of sources, if you want a specific one:
  - *Episode Mode*: TheMovieDB (TV), TVmaze, AniDB (TheTVDB is shown as unavailable: its old API has been shut down);
  - *Movie Mode*: TheMovieDB, OMDb;
  - *Music Mode*: AcoustID, ID3 tags;
  - *Smart Mode* → **Autodetect**, the same automatic recognition as the Match button.

Holding **Shift** while choosing a source skips the automatic name detection and asks you what to search for (in *Opportunistic* mode, the default; change it in Fetch Metadata → *Preferences*).

### 3. Check the result

In the *Proposed Names* column the new name shows what changes: unchanged parts in **grey**, added parts in **green**, removed parts in **red strikethrough** (for long names the full comparison is in the tooltip). Below the name you see the type, the year or episode and the **source** (TMDB, TMDB TV, TVmaze…). Below the original files the **technical badges** read from the name appear: 4K, HDR, DV, HEVC, DTS-HD, DD+ Atmos, ITA, ENG, SRT…

Each proposed name has a label on the right:

| Label | Meaning |
| --- | --- |
| **Exact** green | Certain match (≥ 95%). |
| **Likely · 82%** blue | Very probable (70–94%). |
| **Review · 45%** orange | Needs checking: it must be confirmed by hand in the preview. |
| **Exists** | A file with that name already exists. |
| **Unchanged** | The file already has the right name. |

The name is taken from the title your library already uses: if the file or folder carries the original or a localized title (e.g. *Romanzo Criminale*, *Tutti i diavoli sono qui*), ReNameo keeps it instead of replacing it with the English one.

The **Metadata** panel (inspector) shows for the selected row: poster (the series poster for episodes), title, rating, plot, TMDb/IMDb IDs, season and episode, technical badges, current and **new path**, plus the *From file name* section with how the name was read (title, year, season, episode, quality, codec, audio, languages, release group).

To fix a match:
- **Change Match…** (in the inspector or via right click): search the title, choose *Movie* or *TV Show*, pick the result; for series the episodes of the selected files are aligned automatically;
- reorder with the ↑ ↓ arrows or by dragging;
- exclude a row with ⊖ or the **Delete** key;
- press **F2** to type a name by hand;
- double click a proposed name to open it in the format editor.

### Actions on several files

Select several rows (⌘ click or ⇧ click) and **right click**:

| Action | What it does |
| --- | --- |
| Match Selected | Identifies only the selected files again. |
| Change Match … | Manual match for all selected files. |
| Set Language … | Matches the selected files again in another language. |
| Clear Match | Removes the match, the files stay in the list. |
| Rename Selected … | Opens the preview with only the selected files ticked. |
| Quick Look / Reveal in Finder | macOS preview or show in Finder. |

### 4. Preview and rename

Press **Rename** (or ⌘↵): before touching any file, **Preview Changes** opens, a table *Original → New Name → New Folder → Source* with the status of every row:

- **Needs review** rows (low confidence) are excluded until you tick them;
- **conflicts** (destination already exists, or two files with the same new name) are highlighted before anything happens; choose what to do at the bottom: *Skip*, *Overwrite* (the existing file goes to the Trash) or *Auto-number* (`Name (2).mkv`);
- **Dry Run** simulates everything (files still present, write permissions, conflicts) without touching anything;
- **Apply Changes** runs it.

At the end a discreet notification in the corner sums up the result (*12 files renamed successfully · 2 files require attention*). **⌘Z** or the ↶ icon undo the last rename, even after restarting the app.

The **⌄** menu next to Rename contains:

- **Extension**: *Preserve* keeps the original extension (default), *Override* lets the format decide.
- **Action**: *Rename/Move* moves and renames (default); *Copy*, *Keep Link*, *Symlink*, *Hardlink*, *Clone*; *Test* simulates without touching anything.
- **After Rename**: *Download poster & fanart* saves the images from TheMovieDB next to the renamed files.
- **Naming Profile**: *Plex*, *Jellyfin*, *Emby*, *Kodi* (rename in place), *Windows-friendly* and *macOS-friendly* (rename in place with safe characters: `:` becomes ` - ` on Windows and `꞉` on macOS), or *Custom Format*.

Every rename is recorded in the **History** (clock icon in the header), from where you can undo it.

## Library for Plex, Jellyfin, Emby or Kodi

From **⌄ → Naming Profile** choose **Plex**, **Jellyfin**, **Emby** or **Kodi**. Files are **renamed in place**: they stay in the folder they are in, only the file name changes, following the server's convention.

```
TV/01. The Haunting of Hill House (2018)/Season 1/The Haunting of Hill House (2018) - S01E01 - Steven Sees a Ghost.mkv
```

If you want ReNameo to move the files and build the whole folder structure instead, use **Format** with `{jellyfin.library('/path/to/library')}` (see *Custom formats*). ReNameo looks at what that folder already contains and doesn't repeat it:

- **a library folder** (e.g. `/Volumes/Media/TV`): series go straight into it, without an extra `Shows` or `TV Shows` folder;
- **a root folder** that already contains `Movies`, `Shows`/`TV Shows`, `Anime` or `Music`: files go into the matching sub folder;
- existing folders spelled differently are reused: `Season 1` counts as `Season 01`, and `01. Silo (2023)` counts as `Silo (2023) {tmdb-125988}`.

The examples below show the full structure that `{plex}` and `{jellyfin}` describe:

**Plex**
```
Movies/Dune (2021) {tmdb-438631}/Dune (2021) {tmdb-438631}.mkv
TV Shows/Silo (2023) {tmdb-125988}/Season 01/Silo (2023) - S01E01 - Freedom Day.mkv
TV Shows/Silo (2023) {tmdb-125988}/Season 00/Silo (2023) - S00E01 - Special title.mkv
```

**Jellyfin**
```
Movies/Dune (2021) [tmdbid-438631]/Dune (2021) [tmdbid-438631].mkv
Shows/Silo (2023) [tmdbid-125988]/Season 01/Silo (2023) - S01E01 - Freedom Day.mkv
```

Emby uses `[tmdbid=438631]`, Kodi no ID. The year and database ID in the folder name avoid confusion between titles with the same name or remakes. Specials go into `Season 00`. The choice is remembered until you pick another one or edit the format.

## Extras

Deleted scenes, behind the scenes, featurettes, interviews, trailers, shorts and other bonus material are recognized in three ways:

- by **folder**: `Deleted Scenes`, `Behind The Scenes`, `Featurettes`, `Interviews`, `Trailers`, `Shorts`, `Scenes`, `Extras`, `Other`, or the Italian `Scene Eliminate`, `Dietro le Quinte`, `Contenuti Speciali`;
- by Plex style **suffix**: `Title-deleted.mkv`, `-trailer`, `-featurette`, `-interview`, `-behindthescenes`, `-scene`, `-short`, `-other`;
- by **unambiguous keywords** in the name: *deleted scenes*, *making of*, *featurette*, *trailer*, *bloopers*…

An extra is attributed to the movie of the folder it sits in (not to its own title) and does **not** become "part 2" of the movie. With the Plex/Jellyfin layouts it goes into the right sub folder:

```
Movies/Dune (2021) {tmdb-438631}/Deleted Scenes/Sandworm Test.mkv
```

With other formats it keeps its own title and gets the suffix Plex and Jellyfin recognize (`Sandworm Test-deleted.mkv`). *Samples* stay excluded.

## Custom formats

**Format** opens the editor with a live preview. Expressions in `{…}` are replaced with the file's data; everything else is plain text. Some examples:

| Format | Result |
| --- | --- |
| `{n} ({y})` | `Dune (2021)` |
| `{n} - {s00e00} - {t}` | `Silo - S01E01 - Freedom Day` |
| `{n}/Season {s.pad(2)}/{n} - {s00e00} - {t}` | `Silo/Season 01/Silo - S01E01 - Freedom Day` |
| `/Volumes/Media/{plex}` | full Plex layout |
| `/Volumes/Media/{jellyfin}` | full Jellyfin layout |
| `{jellyfin.name}` | just the Jellyfin style file name, the file stays where it is (what the Naming Profile uses) |
| `{jellyfin.library('/Volumes/Media/TV')}` | move into the given library, reusing existing folders |
| `{n} ({y})/{extra}/{extraTitle}` | `Dune (2021)/Deleted Scenes/Sandworm Test` (extras only) |

Useful bindings: `{n}` name, `{y}` year, `{t}` episode title, `{s}` season, `{e}` episode, `{s00e00}`, `{sxe}`, `{absolute}`, `{vf}` resolution, `{vc}` video codec, `{ac}` audio codec, `{lang}` language, `{tmdbid}`, `{imdbid}`, `{genre}`, `{director}`, `{rating}`, `{plex}`, `{jellyfin}`, `{emby}`, `{kodi}`, `{extra}`, `{extraTitle}`.

**Presets** (bookmark icon) store format, source, language and action together; keys **1–9** apply the first nine.

## Very long series

ReNameo recognizes seasons above 100 and episodes above 1000:

- `One.Piece.S01E1071.mkv`, `Show.S101E05.mkv`, `Show.S1001E02.mkv`
- `One Piece - 1071 - Title.mkv`, `[Group] Show - 1100v2 [1080p].mkv` (anime style absolute numbering)
- `Show.Episode.1071.mkv`, `Show.EP1071.mkv`, `Show 3x1071.mkv`, `Show.S01E1071-E1072.mkv`
- folders `Season 101`, `Stagione 120`, `S120`

Years (`2021`) and resolutions (`1080`, `720x480`) are not mistaken for episode numbers.

## How the search name is chosen

For each movie ReNameo compares the file name and the folder name and uses the one that looks most curated first:

- the *Title (Year)* form wins;
- natural upper and lower case counts, rather than all lower or all upper case;
- real spaces count, rather than dots or underscores;
- release tags are penalized.

Generic or obfuscated names such as `movie.mkv`, `VTS_01_1.mkv` or `a3f9c2e1b7d4.mkv` are ignored in favor of the folder. The final name always comes from the database, with correct capitalization.

## Keyboard shortcuts

| Key | Action |
| --- | --- |
| ⌘K | Command palette: type a command (e.g. "rename sel", "plex", "undo", "settings") or text to search the files |
| ⌘M | Match |
| ⌘O | Add files |
| ⌘↵ | Preview and rename |
| ⌘Z | Undo the last rename |
| ⌘I | Show / hide the inspector |
| ⌘\\ | Compact sidebar (icons only) |
| Space | Quick Look of the selected files |
| F1 | This guide |
| F2 | Type the name of the selected row by hand |
| 1–9 | Apply the matching preset |
| Delete | Exclude the selected row (Shift/Alt + Delete: only the cell) |
| Double click a file | Show it in Finder |
| Double click a name | Open the format editor with that file as sample |
| F5 | Groovy console |
| F7 | Copy match debug information to the clipboard |
| Ctrl+Shift+Delete | Clear the cache |

## Settings

**Settings** at the bottom of the sidebar (or ⌘K → "settings") opens a panel with:

- **Night mode**: dark theme (default);
- **Compact rows**: shorter rows for long lists;
- **Accent color**: the color of buttons and selections;
- the link to this guide and the app version.

## Other sections

- **Episodes**: browse the complete episode list of a series, by season and order (airdate, DVD, absolute).
- **Subtitles**: search and download subtitles from OpenSubtitles; drop video files on the download tile. See *Signing in to OpenSubtitles* below.

### Signing in to OpenSubtitles

ReNameo uses the new **opensubtitles.com** API. The old opensubtitles.org site no longer accepts new applications and answers `401 Unauthorized`. You need:

1. a free account on [opensubtitles.com](https://www.opensubtitles.com/en/users/sign_up) (an opensubtitles.org account doesn't count: sign up again if needed);
2. a free **API key**: once signed in on the site, open [API consumers](https://www.opensubtitles.com/en/consumers), create a consumer (name it e.g. `ReNameo`) and copy the key.

In the Subtitles tab press the user button, enter **API Key**, **Username** and **Password**, then press **Sign In**. The confirmation shows how many downloads you have left today (free accounts get a limited number per day). The password is kept in the macOS Keychain, the key and the user name in the preferences. Clear the user name and press Sign In to sign out.

The opensubtitles.com API no longer lets applications upload subtitles: the upload tile opens the upload page of the site instead.

Error messages: *Wrong username or password* means wrong credentials; *The API key was not accepted* means a wrong or revoked key; *daily download quota* means you have used up today's downloads.
- **SFV**: create and verify SFV, MD5, SHA checksums.
- **Filter**: inspect file attributes, extract archives, split folders.
- **List**: generate lists of names from a pattern.

## Troubleshooting

- **No results**: try **Autodetect** or another source, or hold Shift to type the search yourself.
- **Wrong movie among titles with the same name**: rename the folder to *Title (Year)* first: ReNameo gives it precedence.
- **Exists label**: the destination file is already there; change the format or remove the duplicate.
- **Empty resolution and codec** (`{vf}`, `{vc}`, `{ac}`): install the MediaInfo library with `brew install libmediainfo`.
- **TheTVDB unavailable**: its old API has been shut down; use TheMovieDB (default) or TVmaze.
