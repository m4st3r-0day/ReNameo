# ReNameo — Guida alla riga di comando

Tutto quello che fa l'interfaccia grafica si può fare anche dal terminale, anche in modo automatico (script, cron, cartelle monitorate). Per la finestra vedi [GUIDA_GUI.md](GUIDA_GUI.md).

## Come lanciarlo

L'app contiene già il comando. Il modo più comodo è collegarlo come `renameo`:

```bash
sudo ln -s /Applications/ReNameo.app/Contents/MacOS/ReNameo /usr/local/bin/renameo
renameo -version
```

In alternativa:

```bash
dist/ReNameo.app/Contents/MacOS/ReNameo -help     # app appena compilata
./renameo.sh -help                                # dai sorgenti 
java -jar dist/ReNameo_1.0.0.jar -help            # solo CLI
```

Senza argomenti si apre la finestra; con argomenti ReNameo lavora nel terminale ed esce. I percorsi relativi sono risolti rispetto alla cartella corrente.

Il completamento con Tab per bash/zsh si trova in `tools/bash_completion.d/renameo`:

```bash
source tools/bash_completion.d/renameo                                          # bash
autoload -U +X bashcompinit && bashcompinit && source tools/bash_completion.d/renameo   # zsh
```

## Chiavi API

La riga di comando usa le stesse chiavi salvate nell'app (**Impostazioni → API keys**). Su server, NAS, Docker o negli script puoi passarle con variabili d'ambiente, che hanno la precedenza:

| Variabile | Servizio |
| --- | --- |
| `TMDB_API_KEY` | TheMovieDB (obbligatoria) |
| `OMDB_API_KEY` | OMDb |
| `FANARTTV_API_KEY` | Fanart.tv |
| `ACOUSTID_API_KEY` | AcoustID |

```bash
TMDB_API_KEY=la-tua-chiave renameo -rename ~/Downloads --action test -non-strict
```

## Regola d'oro: prima prova con `--action test`

`--action test` mostra cosa succederebbe senza toccare nessun file:

```bash
renameo -rename -r ~/Downloads/Film --action test -non-strict
```

```
Ignore 2 files that are not video, audio or subtitles
Rename movies using [TheMovieDB]
[TEST] from [~/Downloads/Film/Dune (2021)/movie.mkv] to [~/Downloads/Film/Dune (2021)/Dune (2021).mkv]
Processed 1 files
```

Quando il risultato è quello atteso, togli `--action test`.

## Esempi pratici

### Rinominare film e serie sul posto

```bash
renameo -rename -r ~/Downloads/Film -non-strict
```

Senza `--db` ReNameo riconosce da solo, file per file, se si tratta di un film, un episodio o musica. `-non-strict` permette di scegliere automaticamente il risultato migliore; senza, vengono accettati solo gli abbinamenti certi.

### Organizzare una libreria Plex o Jellyfin

```bash
renameo -rename -r ~/Downloads --naming plex     --output /Volumes/Media -non-strict
renameo -rename -r ~/Downloads --naming jellyfin --output /Volumes/Media -non-strict
```

Risultato con `--naming plex`:

```
/Volumes/Media/Movies/Dune (2021) {tmdb-438631}/Dune (2021) {tmdb-438631}.mkv
/Volumes/Media/Movies/Dune (2021) {tmdb-438631}/Deleted Scenes/Sandworm Test.mkv
/Volumes/Media/TV Shows/Silo (2023) {tmdb-125988}/Season 01/Silo (2023) - S01E01 - Freedom Day.mkv
```

Con `--naming jellyfin` la cartella delle serie è `Shows` e gli ID sono scritti come `[tmdbid-438631]`. Gli speciali vanno in `Season 00`, gli extra nella loro sottocartella (`Deleted Scenes`, `Featurettes`, `Trailers`, …).

### Solo serie o solo film, con una sorgente precisa

```bash
renameo -rename ~/Serie/Silo --db TheMovieDB::TV --format "{n} - {s00e00} - {t}" -non-strict
renameo -rename ~/Film --db TheMovieDB --format "{n} ({y})"
renameo -rename ~/Anime/OnePiece --db AniDB --order Absolute -non-strict
renameo -rename ~/Musica --db ID3 --format "{artist}/{album}/{pi.pad(2)} - {t}"
```

### Forzare la ricerca quando il nome è ambiguo

```bash
renameo -rename ~/Serie/Silo --q "Silo 2023" --db TheMovieDB::TV -non-strict
```

### Copiare invece di spostare, e gestire i conflitti

```bash
renameo -rename -r ~/Downloads --naming plex --output /Volumes/Media --action copy --conflict auto -non-strict
```

`--conflict auto` sostituisce il file esistente solo se il nuovo è di qualità migliore; `index` aggiunge un numero; `skip` (predefinito) lascia stare.

### Scaricare i sottotitoli mancanti

```bash
renameo -get-subtitles ~/Film/Dune --lang it
```

Richiede una API key e un account di opensubtitles.com: accedi una volta dall'app (scheda Subtitles, pulsante utente) e la riga di comando userà la stessa chiave e lo stesso account.

### Poster, fanart e metadati da TheMovieDB

```bash
renameo /Volumes/Media/Movies --apply artwork,metadata -r
```

`artwork` scarica poster e fanart da TheMovieDB accanto ai file; `metadata` salva le informazioni negli attributi estesi del file. `nfo` e `url` creano file `.nfo`/`.url` per Kodi e sono facoltativi. `--apply` si esegue come passaggio separato, dopo la rinomina.

### Altro

```bash
renameo -list --q "Silo" --format "{s00e00} - {t}"          # elenco episodi
renameo -mediainfo ~/Film --format "{fn} {vf} {vc} {ac}"      # info tecniche
renameo -revert ~/Film/Dune                                   # annulla l'ultima rinomina di quei file
renameo -check ~/Film                                         # crea/verifica checksum SFV
```

## Opzioni

| Opzione | Valori | Descrizione |
| --- | --- | --- |
| `-rename` | | Rinomina i file indicati. |
| `-r` | | Entra anche nelle sottocartelle. |
| `--db` | `TheMovieDB::TV`, `TVmaze`, `AniDB`, `TheMovieDB`, `OMDb`, `AcoustID`, `ID3`, `xattr` | Sorgente dei dati. Senza: rilevamento automatico. `TheTVDB` non è più disponibile e viene sostituito da TheMovieDB. |
| `--format` | espressione | Schema del nuovo nome, vedi sotto. |
| `--naming` | `plex`, `jellyfin`, `emby`, `kodi` | Struttura pronta per i media server; usala con `--output`. Viene ignorata se c'è anche `--format`. |
| `--output` | cartella | Cartella di destinazione per i nomi relativi (la radice della libreria). |
| `--action` | `move` (predefinito), `copy`, `keeplink`, `symlink`, `hardlink`, `clone`, `test` | Cosa fare con i file. |
| `--conflict` | `skip` (predefinito), `override`, `auto`, `index`, `fail` | Cosa fare se la destinazione esiste già. |
| `-non-strict` | | Abbinamento più aggressivo, sceglie da solo tra più risultati. |
| `--q` | testo | Forza il nome da cercare. |
| `--lang` | `it`, `en`, … | Lingua dei titoli (predefinita `en`). |
| `--order` | `Airdate` (predefinito), `DVD`, `Absolute`, `AbsoluteAirdate` | Ordine degli episodi. |
| `--filter` | espressione | Scarta i risultati che non soddisfano la condizione, es. `"y > 2000"`. |
| `--file-filter` | espressione | Scarta i file in ingresso, es. `"f.length() > 100*1024*1024"`. |
| `-get-subtitles` | | Scarica i sottotitoli mancanti. |
| `-list` | | Stampa un elenco di episodi (con `--q`). |
| `-mediainfo` | | Stampa le informazioni tecniche dei file. |
| `-revert` | | Riporta i file al nome precedente usando la cronologia. |
| `--apply` | `artwork`, `metadata`, `nfo`, `url` | Operazioni aggiuntive sui file già rinominati. |
| `-exec` | comando | Esegue un comando per ogni file, es. `-exec echo {f}`. |
| `--log` | `all`, `fine`, `info`, `warning` | Quanto dettaglio stampare. |
| `--log-file` | file | Salva il log su file. |
| `-no-history`, `-clear-history` | | Disattiva o svuota la cronologia delle rinomine. |
| `-clear-cache`, `-clear-prefs` | | Svuota la cache o le impostazioni. |
| `-version`, `-help` | | Versione e aiuto. |

Il comando termina con codice **0** se tutto è andato a buon fine e **1** se nessun file è stato elaborato o c'è stato un errore: utile negli script.

## Formati (`--format`)

Il testo tra `{…}` viene sostituito con i dati del file; il resto resta com'è. Una `/` crea sottocartelle.

| Formato | Risultato |
| --- | --- |
| `{n} ({y})` | `Dune (2021)` |
| `{n} - {s00e00} - {t}` | `Silo - S01E01 - Freedom Day` |
| `{n}/Season {s.pad(2)}/{n} - {s00e00} - {t}` | `Silo/Season 01/Silo - S01E01 - Freedom Day` |
| `{n} ({y}) [{vf} {vc}]` | `Dune (2021) [1080p x265]` |
| `/Volumes/Media/{plex}` | layout Plex completo |
| `{n} ({y})/{extra}/{extraTitle}` | `Dune (2021)/Deleted Scenes/Sandworm Test` (per gli extra) |

Binding principali:

| Binding | Contenuto |
| --- | --- |
| `{n}` `{y}` | nome e anno dal database (maiuscole e minuscole corrette) |
| `{s}` `{e}` `{s00e00}` `{sxe}` `{absolute}` `{t}` | stagione, episodio, codici, numero assoluto, titolo |
| `{vf}` `{vc}` `{ac}` `{channels}` | risoluzione, codec video, codec audio, canali |
| `{lang}` `{subt}` | lingua (per sottotitoli) |
| `{tmdbid}` `{imdbid}` `{genre}` `{director}` `{rating}` `{certification}` | dati TheMovieDB |
| `{plex}` `{jellyfin}` `{emby}` `{kodi}` | percorso completo secondo il media server (extra compresi) |
| `{extra}` `{extraTitle}` | cartella e titolo dell'extra (vuoti per i contenuti principali) |
| `{fn}` `{ext}` `{f}` `{folder}` | nome, estensione, percorso e cartella del file originale |

Un formato può anche stare in un file: `--format ~/formati/serie.groovy`.

## Quali file vengono presi

Solo **video, audio e sottotitoli**, più le cartelle disco BDMV/VIDEO_TS. Il resto (`.nfo`, `.jpg`, `.png`, `.txt`, …) viene ignorato e il log lo dice (`Ignore N files that are not video, audio or subtitles`). I *sample* sono sempre esclusi.

## Extra (scene eliminate, trailer, …)

Un file è considerato un extra se:

- sta in una cartella come `Deleted Scenes`, `Behind The Scenes`, `Featurettes`, `Interviews`, `Trailers`, `Shorts`, `Scenes`, `Extras`, `Other`, `Scene Eliminate`, `Dietro le Quinte`, `Contenuti Speciali`;
- oppure ha un suffisso alla Plex (`-deleted`, `-trailer`, `-featurette`, `-interview`, `-behindthescenes`, `-scene`, `-short`, `-other`);
- oppure contiene parole come *deleted scenes*, *making of*, *featurette*, *trailer*, *bloopers*.

L'extra viene associato al film della cartella superiore e mantiene il proprio titolo:
- con `--naming` o `{plex}`/`{jellyfin}` va nella sottocartella corretta;
- senza formato diventa `Titolo-deleted.mkv`, `Titolo-trailer.mkv` e così via, che Plex e Jellyfin riconoscono accanto al film.

## Serie lunghe e anime

Sono riconosciuti stagioni oltre la 100 ed episodi oltre il 1000: `S01E1071`, `S101E05`, `S1001E02`, `3x1071`, `Episode 1071`, `EP1071`, `S01E1071-E1072`, `Show - 1071 - Titolo`, `[Gruppo] Show - 1100v2 [1080p]` e le cartelle `Season 101`, `Stagione 120`, `S120`. Per gli anime con numerazione assoluta usa `--order Absolute`.

## Informazioni tecniche dei video

I binding `{vf}`, `{vc}`, `{ac}`, `{channels}`, il comando `-mediainfo`, `--conflict auto` (che confronta la qualità) e il riconoscimento dei film divisi in più parti leggono i file con la libreria MediaInfo. Su macOS va installata una volta con Homebrew:

```bash
brew install libmediainfo
```

ReNameo la trova da solo in `/opt/homebrew/lib`. Senza, questi valori restano vuoti ma la rinomina funziona lo stesso.

## Automazione

Esempio di script che sistema ogni notte la cartella dei download in una libreria Jellyfin, registrando l'esito:

```bash
#!/bin/bash
renameo -rename -r "$HOME/Downloads/Completati" \
  --naming jellyfin --output /Volumes/Media \
  --action move --conflict auto -non-strict \
  --log-file "$HOME/Library/Logs/renameo.log"
```

## Docker e NAS

L'immagine `ghcr.io/m4st3r-0day/renameo` contiene la riga di comando, Java e MediaInfo, per `linux/amd64` e `linux/arm64`:

```bash
docker run --rm -e TMDB_API_KEY=la-tua-chiave -v /percorso/media:/media \
  ghcr.io/m4st3r-0day/renameo -rename /media/downloads -r --action test -non-strict
```

Con il comando `watch` il container rinomina a intervalli ciò che arriva in una cartella (`WATCH_DIR`, `RENAMEO_NAMING`, `RENAMEO_ACTION`, `PUID`/`PGID` …). Tutte le opzioni, il `docker-compose.yml` e l'installazione su TrueNAS SCALE sono in `docker/README.md`.

## Script e plugin

- `renameo -script file.groovy` esegue uno script Groovy; `renameo -script fn:nome` esegue `nome.groovy` dalla cartella `scripts` dei dati di ReNameo.
- I **plugin** (file `.groovy` nella cartella `plugins`) vengono caricati anche dalla riga di comando e da Docker: `onRename` viene chiamato dopo ogni rinomina e i loro valori si usano nei formati come `{plugin.nome}`. Esempi in `docs/plugins`.

## Dove vengono salvati i dati

- Cache, cronologia, log, script e plugin: `~/.renameo` su macOS (le cartelle `~/.filebot` di versioni precedenti vengono adottate automaticamente), `~/.local/share/renameo` su Linux, `%APPDATA%\ReNameo` su Windows, `/config` in Docker.
- Impostazioni: preferenze utente di Java (`-clear-prefs` per azzerarle).
