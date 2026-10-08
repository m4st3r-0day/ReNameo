# ReNameo — Guida all'interfaccia grafica

ReNameo rinomina e organizza **film, serie TV, anime, musica e sottotitoli** usando i dati di TheMovieDB e degli altri database online. Questa guida spiega come usare l'app dalla finestra; per il terminale vedi [GUIDA_CLI.md](GUIDA_CLI.md).

## Avvio

- **App**: apri `ReNameo.app` (in `dist/` dopo la build, oppure dove l'hai copiata, ad esempio `/Applicazioni`).
- **Da sorgente**: `./renameo.sh` nella cartella del progetto, dopo `ant fatjar` (serve il JDK 21, vedi README).

Al primo avvio ReNameo recupera automaticamente impostazioni, preset e cronologia di eventuali versioni precedenti.

## Chiavi API

ReNameo cerca film e serie su **TheMovieDB**, che richiede una chiave personale gratuita:

1. crea un account su [themoviedb.org](https://www.themoviedb.org/signup);
2. apri *Settings → API* ([link diretto](https://www.themoviedb.org/settings/api)), richiedi una chiave per uso personale e copia la **API Key** (quella corta, non l'*API Read Access Token*).

Al primo avvio, se manca la chiave, ReNameo apre da solo la finestra **API keys**: incolla la chiave e premi **Save**. La chiave viene verificata subito con TheMovieDB e salvata nel Portachiavi di macOS. Nella stessa finestra puoi aggiungere, se vuoi, le chiavi di OMDb, Fanart.tv e AcoustID. Per cambiarle più tardi: **Impostazioni → API keys**, oppure *API keys* nella command palette (⌘K).

## La finestra

| Zona | Cosa contiene |
| --- | --- |
| Barra superiore | Nome e versione, pulsante per compattare la barra laterale (⌘\\), **command palette** (⌘K), aiuto (F1) e indicatore di stato: verde *Ready*, blu *Matching…*, arancione *N to review*. |
| Barra laterale | **Media Tools**: Rename, Episodes, Subtitles. **Utilities**: SFV, Filter, List, Plugins. In fondo, *Settings*. |
| Intestazione | Titolo della sezione e, in Rename, le azioni principali: **Match**, **Fetch Metadata**, **Format**, Preset, **Undo**, Cronologia e mostra/nascondi inspector (⌘I). |
| Contenuto | In Rename: *Original Files* a sinistra, *Proposed Names* al centro, *Metadata* a destra. |
| Barra di stato | File aggiunti, file abbinati, avvisi, avanzamento e pulsante **Rename** con il menu delle opzioni (⌄). |

## Rinominare: il flusso tipico

### 1. Aggiungi i file

Trascina file o cartelle nel riquadro *Drag & drop media files here*, oppure usa **Browse Files**. Le cartelle vengono lette ricorsivamente.

ReNameo prende **solo video, audio e sottotitoli** (MKV, MP4, AVI, MP3, FLAC, SRT, ASS, …) e le cartelle disco BDMV/VIDEO_TS. File come `.nfo`, `.jpg`, `.png`, `.txt` vengono ignorati: le immagini (poster, fanart) arrivano da TheMovieDB, non dai file locali.

Ogni riga mostra il tipo (film, serie, musica), il nome, la dimensione, il formato e la cartella.

### 2. Trova i dati: Match o Fetch Metadata

- **Match** (⌘M) riconosce da solo, file per file, film, episodi e musica: è la scelta giusta quasi sempre. Se un gruppo di file viene classificato male (per esempio un film nuovo scambiato per una serie), ReNameo riprova in automatico con l'altro tipo, senza chiedere nulla; ti chiede solo i casi davvero ambigui.
- **Fetch Metadata** apre l'elenco delle sorgenti, se vuoi sceglierne una precisa:
  - *Episode Mode*: TheMovieDB (serie), TVmaze e AniDB;
  - *Movie Mode*: TheMovieDB, OMDb;
  - *Music Mode*: AcoustID, tag ID3;
  - *Smart Mode* → **Autodetect**, lo stesso riconoscimento automatico del pulsante Match.

Tenendo premuto **Shift** mentre scegli una sorgente, ReNameo salta il rilevamento automatico del nome e ti chiede cosa cercare (in modalità *Opportunistic*, quella predefinita; si cambia in Fetch Metadata → *Preferences*).

### 3. Controlla il risultato

Nella colonna *Proposed Names* il nuovo nome mostra cosa cambia: in **grigio** le parti invariate, in **verde** quelle aggiunte, in **rosso barrato** quelle rimosse (se il nome è lungo, il confronto completo è nel suggerimento al passaggio del mouse). Sotto il nome trovi il tipo, l'anno o l'episodio e la **sorgente** (TMDB, TMDB TV, TVmaze…). Sotto i file originali compaiono i **badge tecnici** letti dal nome: 4K, HDR, DV, HEVC, DTS-HD, DD+ Atmos, ITA, ENG, SRT…

Ogni nome proposto ha un'etichetta a destra:

| Etichetta | Significato |
| --- | --- |
| **Exact** verde | Abbinamento certo (≥ 95%). |
| **Likely · 82%** blu | Molto probabile (70–94%). |
| **Review · 45%** arancione | Da controllare: nell'anteprima va confermato a mano. |
| **Exists** | Esiste già un file con quel nome. |
| **Unchanged** | Il file ha già il nome corretto. |

Il nome da cercare viene preso dal titolo che la tua libreria usa già: se il file o la cartella si chiama con il titolo originale o italiano (per esempio *Romanzo Criminale*, *Tutti i diavoli sono qui*), ReNameo lo mantiene invece di sostituirlo con quello inglese.

Il pannello **Metadata** (inspector) mostra per la riga selezionata: poster (per gli episodi quello della serie), titolo, voto, trama, ID TMDb/IMDb, stagione ed episodio, badge tecnici, percorso attuale e **nuovo percorso**, più la sezione *From file name* con l'interpretazione del nome (titolo, anno, stagione, episodio, qualità, codec, audio, lingue, gruppo di release).

Per correggere un abbinamento:
- **Change Match…** (nell'inspector o col tasto destro): cerca il titolo, scegli *Movie* o *TV Show*, seleziona il risultato; per le serie gli episodi dei file selezionati vengono allineati da soli;
- riordina con le frecce ↑ ↓ o trascinando;
- escludi una riga con ⊖ o il tasto **Canc**;
- premi **F2** per scrivere un nome a mano;
- fai doppio clic su un nome proposto per aprirlo nell'editor di formato.

### Azioni su più file

Seleziona più righe (⌘ clic o ⇧ clic) e usa il **tasto destro**:

| Azione | Cosa fa |
| --- | --- |
| Match Selected | Riconosce di nuovo solo i file selezionati. |
| Change Match … | Abbinamento manuale per tutti i selezionati. |
| Set Language … | Rifà il match dei selezionati in un'altra lingua. |
| Clear Match | Toglie l'abbinamento, i file restano in lista. |
| Rename Selected … | Apre l'anteprima con spuntati solo i selezionati. |
| Quick Look / Reveal in Finder | Anteprima di macOS o apertura nel Finder. |

### 4. Anteprima e rinomina

Premi **Rename** (o ⌘↵): prima di toccare qualsiasi file si apre **Preview Changes**, una tabella *Original → New Name → New Folder → Source* con lo stato di ogni riga:

- le righe **Needs review** (bassa confidenza) sono escluse finché non le spunti tu;
- i **conflitti** (destinazione già esistente o due file con lo stesso nuovo nome) sono evidenziati prima di partire; scegli in basso cosa fare: *Skip*, *Overwrite* (il file esistente va nel Cestino) o *Auto-number* (`Nome (2).mkv`);
- **Dry Run** simula tutto (file ancora presenti, permessi di scrittura, conflitti) senza toccare nulla;
- **Apply Changes** esegue.

Alla fine una notifica discreta nell'angolo riassume il risultato (*12 files renamed successfully · 2 files require attention*). **⌘Z** o l'icona ↶ annullano l'ultima rinomina, anche dopo aver riavviato l'app.

Il menu **⌄** accanto a Rename contiene:

- **Extension**: *Preserve* mantiene l'estensione originale (predefinito), *Override* la lascia decidere al formato.
- **Action**: *Rename/Move* sposta e rinomina (predefinito); *Copy*, *Keep Link*, *Symlink*, *Hardlink*, *Clone*; *Test* simula senza toccare nulla.
- **After Rename**: *Download poster & fanart* scarica le immagini da TheMovieDB accanto ai file rinominati.
- **Naming Profile**: *Plex*, *Jellyfin*, *Emby*, *Kodi* (rinomina sul posto), *Windows-friendly* e *macOS-friendly* (rinomina sul posto con caratteri sicuri: `:` diventa ` - ` su Windows e `꞉` su macOS), oppure *Custom Format*.

Ogni rinomina finisce nella **Cronologia** (icona orologio nell'intestazione), da cui puoi annullarla.

## Libreria per Plex, Jellyfin, Emby o Kodi

Dal menu **⌄ → Naming Profile** scegli **Plex**, **Jellyfin**, **Emby** o **Kodi**. I file restano dove sono, ma vengono sistemati secondo la convenzione del server:

- la **cartella della serie o del film** viene rinominata sul posto in *Nome (Anno)*, per esempio `Neagley.S01.1080p.AMZN.WEB-DL.DDP5.1.ENG.Atmos.ITA.H265-TBK` → `Neagley (2024)`;
- gli episodi vanno nella cartella della stagione (`Season 01`), creata se manca; se esiste già (anche come `Season 1`) viene riutilizzata;
- il resto della cartella (`.nfo`, sample, altre stagioni) la segue, come se l'avessi rinominata a mano, e la vecchia cartella sparisce;
- un file sciolto in una cartella generica (per esempio direttamente in `Downloads`) cambia solo nome: ReNameo tocca solo le cartelle il cui nome comincia con il titolo della serie o del film.

Tutto si annulla con ⌘Z o dalla cronologia, cartelle comprese.

```
Serie/01. The Haunting of Hill House (2018)/Season 1/The Haunting of Hill House (2018) - S01E01 - Steven Sees a Ghost.mkv
```

Se invece vuoi che ReNameo sposti i file e crei tutta la struttura di cartelle, usa **Format** con `{jellyfin.library('/percorso/libreria')}` (vedi *Formati personalizzati*). ReNameo guarda cosa contiene la cartella indicata e non ripete quello che c'è già:

- **cartella di una libreria** (ad esempio `/Volumes/Media/Serie TV`): le serie vanno direttamente lì dentro, senza aggiungere `Shows` o `TV Shows`;
- **cartella radice** che contiene già `Movies`, `Shows`/`TV Shows`, `Anime` o `Music`: i file vanno nella sottocartella giusta;
- le cartelle esistenti scritte in altro modo vengono riutilizzate: `Season 1` vale come `Season 01`, e `01. Silo (2023)` vale come `Silo (2023) {tmdb-125988}`.

Gli esempi sotto mostrano la struttura completa che `{plex}` e `{jellyfin}` descrivono:

**Plex**
```
Movies/Dune (2021) {tmdb-438631}/Dune (2021) {tmdb-438631}.mkv
TV Shows/Silo (2023) {tmdb-125988}/Season 01/Silo (2023) - S01E01 - Freedom Day.mkv
TV Shows/Silo (2023) {tmdb-125988}/Season 00/Silo (2023) - S00E01 - Titolo speciale.mkv
```

**Jellyfin**
```
Movies/Dune (2021) [tmdbid-438631]/Dune (2021) [tmdbid-438631].mkv
Shows/Silo (2023) [tmdbid-125988]/Season 01/Silo (2023) - S01E01 - Freedom Day.mkv
```

Emby usa `[tmdbid=438631]`, Kodi nessun ID. L'anno e l'ID del database nel nome della cartella evitano confusione tra titoli omonimi o remake. Gli speciali vanno in `Season 00`. La scelta resta salvata finché non ne fai un'altra o non modifichi il formato.

## Contenuti extra

Scene eliminate, dietro le quinte, featurette, interviste, trailer, corti e altri extra vengono riconosciuti in tre modi:

- dalla **cartella**: `Deleted Scenes`, `Behind The Scenes`, `Featurettes`, `Interviews`, `Trailers`, `Shorts`, `Scenes`, `Extras`, `Other`, oppure `Scene Eliminate`, `Dietro le Quinte`, `Contenuti Speciali`;
- dal **suffisso** alla Plex: `Titolo-deleted.mkv`, `-trailer`, `-featurette`, `-interview`, `-behindthescenes`, `-scene`, `-short`, `-other`;
- da **parole chiave inequivocabili** nel nome: *deleted scenes*, *making of*, *featurette*, *trailer*, *bloopers*…

Un extra viene attribuito al film della cartella che lo contiene (non al proprio titolo) e **non** diventa "parte 2" del film. Con i layout Plex/Jellyfin finisce nella sottocartella giusta:

```
Movies/Dune (2021) {tmdb-438631}/Deleted Scenes/Sandworm Test.mkv
```

Con altri formati mantiene il proprio titolo e riceve il suffisso che Plex e Jellyfin riconoscono (`Sandworm Test-deleted.mkv`). I *sample* restano esclusi.

## Formati personalizzati

**Format** apre l'editor con anteprima dal vivo. Le espressioni tra `{…}` vengono sostituite con i dati del file; tutto il resto è testo normale. Alcuni esempi:

| Formato | Risultato |
| --- | --- |
| `{n} ({y})` | `Dune (2021)` |
| `{n} - {s00e00} - {t}` | `Silo - S01E01 - Freedom Day` |
| `{n}/Season {s.pad(2)}/{n} - {s00e00} - {t}` | `Silo/Season 01/Silo - S01E01 - Freedom Day` |
| `/Volumes/Media/{plex}` | layout Plex completo |
| `/Volumes/Media/{jellyfin}` | layout Jellyfin completo |
| `{jellyfin.name}` | solo il nome del file alla Jellyfin, il file resta dov'è (è ciò che usa il Naming Profile) |
| `{jellyfin.library('/Volumes/Media/Serie TV')}` | sposta nella libreria indicata, riusando le cartelle esistenti |
| `{n} ({y})/{extra}/{extraTitle}` | `Dune (2021)/Deleted Scenes/Sandworm Test` (solo per gli extra) |

Binding utili: `{n}` nome, `{y}` anno, `{t}` titolo episodio, `{s}` stagione, `{e}` episodio, `{s00e00}`, `{sxe}`, `{absolute}`, `{vf}` risoluzione, `{vc}` codec video, `{ac}` codec audio, `{lang}` lingua, `{tmdbid}`, `{imdbid}`, `{genre}`, `{director}`, `{rating}`, `{plex}`, `{jellyfin}`, `{extra}`, `{extraTitle}`.

I **Preset** (icona segnalibro) salvano formato, sorgente, lingua e azione insieme; i tasti **1–9** applicano i primi nove.

## Serie molto lunghe

ReNameo riconosce stagioni oltre la 100 ed episodi oltre il 1000:

- `One.Piece.S01E1071.mkv`, `Show.S101E05.mkv`, `Show.S1001E02.mkv`
- `One Piece - 1071 - Titolo.mkv`, `[Gruppo] Show - 1100v2 [1080p].mkv` (numerazione assoluta stile anime)
- `Show.Episode.1071.mkv`, `Show.EP1071.mkv`, `Show 3x1071.mkv`, `Show.S01E1071-E1072.mkv`
- cartelle `Season 101`, `Stagione 120`, `S120`

Anni (`2021`) e risoluzioni (`1080`, `720x480`) non vengono scambiati per numeri di episodio.

## Come sceglie il nome da cercare

Per ogni film ReNameo confronta il nome del file e quello della cartella e usa per primo quello che sembra più curato:

- vince il formato *Titolo (Anno)*;
- contano le maiuscole e minuscole naturali, invece di tutto minuscolo o tutto maiuscolo;
- contano gli spazi veri, invece di punti o trattini bassi;
- penalizza i tag di release.

Nomi generici o offuscati come `movie.mkv`, `VTS_01_1.mkv` o `a3f9c2e1b7d4.mkv` vengono ignorati a favore della cartella. Il nome finale arriva sempre dal database, con maiuscole e minuscole corrette.

## Scorciatoie da tastiera

| Tasto | Azione |
| --- | --- |
| ⌘K | Command palette: scrivi un comando (es. "rename sel", "plex", "undo") o un testo da cercare tra i file |
| ⌘M | Match |
| ⌘O | Aggiungi file |
| ⌘↵ | Anteprima e rinomina |
| ⌘Z | Annulla l'ultima rinomina |
| ⌘I | Mostra / nascondi l'inspector |
| ⌘\\ | Barra laterale compatta (solo icone) |
| Spazio | Quick Look dei file selezionati |
| F1 | Questa guida |
| F2 | Scrivi a mano il nome della riga selezionata |
| 1–9 | Applica il preset corrispondente |
| Canc | Escludi la riga selezionata (Shift/Alt + Canc: solo la cella) |
| Doppio clic su un file | Mostralo nel Finder |
| Doppio clic su un nome | Apri l'editor di formato con quel file come esempio |
| F5 | Console Groovy |
| F7 | Copia negli appunti le informazioni di debug dell'abbinamento |
| Ctrl+Shift+Canc | Svuota la cache |

## Impostazioni

**Settings** in fondo alla barra laterale (oppure ⌘K → "settings") apre un pannello con:

- **Night mode**: tema scuro (predefinito);
- **Compact rows**: righe più basse per liste lunghe;
- **Accent color**: il colore di pulsanti e selezioni;
- **Tools**: *API keys*, *Watch folder*, *Update offline index* e *Plugins* (apre la pagina Plugins), spiegati qui sotto;
- il link alla guida e la versione dell'app.

## Cartella sorvegliata

**Impostazioni → Watch folder** sceglie una cartella (per esempio Downloads), i nomi (Plex, Jellyfin, Emby, Kodi o i nomi predefiniti) e l'azione (sposta, copia, hard link, link simbolico). Mentre ReNameo è aperto, i nuovi file video, audio e sottotitoli che arrivano lì vengono rinominati ogni pochi minuti.

- I file modificati negli ultimi 2 minuti vengono lasciati stare, così i download possono finire.
- I file che non si riescono a riconoscere non vengono ritentati finché non cambiano.
- Ogni rinomina finisce nella cronologia e si può annullare; un messaggio indica quanti file sono stati rinominati.

Per un server o un NAS sempre acceso conviene la modalità *watch* dell'immagine Docker (vedi `docker/README.md`).

## Aggiornare l'indice offline

ReNameo contiene un indice dei film e delle serie più popolari, che permette di riconoscere subito anche i titoli italiani (*Il Trono di Spade* → *Game of Thrones*). **Impostazioni → Update offline index** lo riscarica da TheMovieDB, in inglese e nella lingua del sistema, in circa 20 secondi. Il nuovo indice viene usato dal prossimo avvio.

## Plugin

La pagina **Plugins** nella barra laterale raccoglie i plugin: script Groovy che reagiscono alle rinomine (app, cartella sorvegliata, riga di comando, Docker), aggiungono valori ai formati o azioni da eseguire su una cartella.

- **Available**: i plugin pronti inclusi nell'app; *Install* li copia nella cartella `plugins` (`~/.renameo/plugins` su macOS).
- **Installed**: per ogni plugin l'interruttore *On*, il cestino per rimuoverlo, i campi delle impostazioni (salvati appena lasci il campo) e i pulsanti delle azioni, che chiedono una cartella.
- Mentre un plugin lavora, la sua scheda mostra cosa sta facendo, una barra di avanzamento, il tempo trascorso e **Stop**; dopo, *Last run* dice com'è andata.
- **Plugin log** (trascina il divisore per ingrandirlo): ora, plugin e un segno per inizio ▶, fine ✓, fermato ■ ed errore ✕. Filtra per plugin o *Errors only*, seleziona e copia con ⌘C o clic destro, *Save…* salva in un file, *Clear* svuota.
- In alto, *Open folder* apre la cartella dei plugin e *Reload* li ricarica dopo averli modificati.

| Plugin | Cosa fa |
| --- | --- |
| `jellyfin-refresh`, `emby-refresh` | Aggiornano la libreria dopo le rinomine (indirizzo del server e API key) |
| `plex-refresh` | Fa scansionare a Plex solo le cartelle che hanno ricevuto file (indirizzo e token) |
| `kodi-scan` | Aggiorna la libreria video di Kodi (controllo remoto via HTTP attivo) |
| `notify` | Messaggio su ntfy, Discord, Gotify o Pushover dopo le rinomine; *Send a test* per provarlo |
| `clutter-cleaner` | Dopo uno spostamento sposta nel cestino sample, `.nfo`, `.txt` … e toglie le cartelle di download rimaste vuote; tocca solo le cartelle senza più video, audio o sottotitoli. *Clean a folder* fa lo stesso su una cartella |
| `subtitles-all` | Sottotitoli mancanti per una cartella intera o, con *After renaming* = `yes`, dopo ogni rinomina (serve l'accesso a OpenSubtitles) |
| `missing-episodes` | Scegli la cartella di una serie: elenca gli episodi già andati in onda che mancano (TheMovieDB) |
| `duplicates` | Elenca episodi e film presenti più volte, con la dimensione di ogni copia; non cancella nulla |
| `rename-log` | Scrive ogni rinomina in un file di testo |

Per scrivere un plugin tuo:

```groovy
description "Aggiorna la libreria di Jellyfin dopo le rinomine"
setting "server", "Server", "http://localhost:8096"   // campo nella pagina Plugins, letto con settings.server
secret "apiKey", "API key"                            // come sopra, nascosto

onRename { from, to -> log "rinominato ${from.name}" }   // dopo ogni file
onRenameBatch { renames -> /* una volta per gruppo */ }   // renames = [[from, to], ...]
binding("risoluzione") { m -> m.height >= 2000 ? "4K" : "HD" }   // {plugin.risoluzione} nei formati
action("Conta i video", "…") { folder -> /* pulsante nella pagina Plugins */ }
```

La guida [Creare plugin e script](GUIDA_PLUGIN.md) spiega tutto passo passo, con esempi completi; si apre anche dal pulsante in fondo alla pagina Plugins. I plugin girano con i tuoi permessi: installa solo quelli di cui ti fidi. I formati invece restano protetti e non possono eseguire programmi né modificare file.

## Anteprima dei poster

Con **After Rename → Download poster & fanart** attivo, l'anteprima delle modifiche mostra anche i poster dei film e delle serie per cui verranno scaricate le immagini: togli la spunta a quelli che non vuoi.

## Altre sezioni

- **Episodes**: consulta l'elenco completo degli episodi di una serie, per stagione e ordine (messa in onda, DVD, assoluto).
- **Subtitles**: cerca e scarica sottotitoli da OpenSubtitles; trascina i file video sul riquadro di download. Vedi *Accesso a OpenSubtitles* qui sotto.

### Accesso a OpenSubtitles

ReNameo usa la nuova API di **opensubtitles.com**. Il vecchio sito opensubtitles.org non accetta più nuove applicazioni e risponde `401 Unauthorized`. Ti servono:

1. un account gratuito su [opensubtitles.com](https://www.opensubtitles.com/en/users/sign_up) (quello di opensubtitles.org non vale: registrati di nuovo se serve);
2. una **API key** gratuita: dopo il login sul sito apri [API consumers](https://www.opensubtitles.com/en/consumers), crea un consumer (nome, ad esempio `ReNameo`) e copia la chiave.

Nella scheda Subtitles premi il pulsante utente e inserisci **API Key**, **Username** e **Password**, poi premi **Sign In**. Il messaggio di conferma mostra quanti download ti restano oggi (gli account gratuiti ne hanno un numero limitato al giorno). La password viene salvata nel Portachiavi di macOS, la chiave e il nome utente nelle preferenze. Svuota il nome utente e premi Sign In per uscire.

L'API di opensubtitles.com non permette più di caricare sottotitoli dalle applicazioni: il riquadro di upload apre la pagina di caricamento del sito.

Messaggi di errore: *Wrong username or password* indica credenziali sbagliate; *The API key was not accepted* indica una chiave sbagliata o revocata; *daily download quota* significa che hai finito i download di oggi.
- **SFV**: crea e verifica checksum SFV, MD5, SHA.
- **Filter**: ispeziona gli attributi dei file, estrai archivi, dividi cartelle.
- **List**: genera elenchi di nomi da uno schema.

## Problemi comuni

- **Nessun risultato**: prova **Autodetect** o una sorgente diversa, oppure tieni premuto Shift per scrivere la ricerca a mano.
- **Film sbagliato tra omonimi**: rinomina prima la cartella in formato *Titolo (Anno)*: ReNameo le dà la precedenza.
- **Etichetta Exists**: il file di destinazione c'è già; cambia formato o elimina il duplicato.
- **Risoluzione e codec vuoti** (`{vf}`, `{vc}`, `{ac}`): installa la libreria MediaInfo con `brew install libmediainfo`.
- **TheTVDB**: non è più tra le fonti, perché la sua vecchia API è stata chiusa e la nuova è a pagamento. I preset che lo usavano passano automaticamente a TheMovieDB.
