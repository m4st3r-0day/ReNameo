# Changelog

What changed in each version of ReNameo. The section of a version is also the text of its GitHub release and of the update notice in the app.

## [1.1.0]

### New
- **Library** page: how many movies, series and episodes a folder holds, its size and quality, duplicates, videos without subtitles and, on request, the aired episodes that are missing (TheMovieDB).
- **Folders** view in the preview: the folders before and after renaming, side by side, with new folders, folders that go away and files that follow their folder.
- **Update notice**: once a day ReNameo checks for a new version and shows what's new; it can be turned off, and checked by hand under Settings or Help.
- **Database ID in movie file names** can be turned off (⌄ → Naming Profile, or `RENAMEO_MOVIE_IDS=false`): `Dune (2021).mkv` instead of `Dune (2021) [tmdbid-438631].mkv`. The folder keeps the ID either way.
- The disk image opens on a window that shows where to drag the app and how to open it the first time, and has the app icon.

### Better
- A new episode lying loose next to the folder of its series goes into that folder and its season folder, e.g. `Neagley (2026)/Season 01`.
- Watch folder (app and Docker): files ReNameo already renamed and sample clips are skipped, so a run with nothing new asks TheMovieDB nothing and logs `No new files to rename` instead of an error.
- Archives (RAR, 7z, ZIP …) can be extracted on Apple Silicon Macs: the app brings the official 7-Zip program. The Docker image has 7-Zip too.
- The plugin log is easier to read with the light theme.

### Fixed
- A sample clip could be renamed as a movie of its own, leaving an empty folder behind.

## [1.0.0] - 2026-10-08

First public release, for macOS on Apple Silicon.

- Movies, TV shows, anime, music and subtitles matched against TheMovieDB, TVmaze, AniDB, OMDb and AcoustID, with a side-by-side preview, confidence levels, undo and a persistent history.
- Naming profiles for Plex, Jellyfin, Emby and Kodi that tidy up in place: the folder of a series or movie gets its proper name and episodes go into season folders.
- Plugins page with ready-made plugins (Jellyfin, Plex, Emby and Kodi refresh, notifications, clutter cleaner, subtitles, missing episodes, duplicates, rename log) and a guide to writing your own.
- Watch folder, OpenSubtitles, checksums, episode lists, command palette, dark and light themes, built-in guide in English and Italian.
