# Creare plugin e script

ReNameo si può estendere con piccoli file di testo scritti in **Groovy**, un linguaggio semplice che assomiglia a Java e a JavaScript. Non serve saper programmare in modo approfondito: quasi tutti gli esempi di questa guida si possono copiare e adattare.

Ci sono due modi di farlo:

| | Plugin | Script |
| --- | --- | --- |
| Cos'è | Un file che resta attivo e **reagisce** a quello che fa ReNameo | Un file che **esegui tu** quando serve, come un comando |
| Quando lavora | Dopo ogni rinomina, quando premi un suo pulsante, quando un formato usa `{plugin.nome}` | Solo quando lo lanci con `renameo -script …` |
| Dove si mette | Cartella `plugins` | Cartella `scripts` |
| Si usa da | App, cartella sorvegliata, riga di comando, Docker | Riga di comando, Docker, cron |
| Esempi | Avvisare Jellyfin, mandare una notifica, pulire le cartelle | Ordinare i download in *Serie TV* e *Film*, scaricare sottotitoli per tutto l'archivio |

Le cartelle sono dentro la cartella dei dati di ReNameo:

| Sistema | Cartella dei dati |
| --- | --- |
| macOS | `~/.renameo` |
| Linux | `~/.local/share/renameo` |
| Windows | `%APPDATA%\ReNameo` |
| Docker | `/config` |

Per scrivere i file basta un qualsiasi editor di testo (TextEdit in modalità testo semplice, Blocco note, Visual Studio Code …). Salva con estensione `.groovy` e codifica UTF-8.

## Il tuo primo plugin

1. Apri la pagina **Plugins** nella barra laterale e premi **Open folder**.
2. Crea nella cartella un file `ciao.groovy` con queste righe:

   ```groovy
   description "Il mio primo plugin"

   onRename { da, a ->
       log "Rinominato ${da.name} in ${a.name}"
   }
   ```

3. Torna in ReNameo e premi **Reload**: il plugin compare fra gli *Installed* con la sua descrizione.
4. Rinomina un file qualsiasi. Nel riquadro **Plugin log** in fondo alla pagina compare la riga *Rinominato … in …*.

Fatto: questo è un plugin completo. Il nome del file (senza `.groovy`) è il nome del plugin.

## Cosa può fare un plugin

Un plugin è una lista di istruzioni. Ognuna è facoltativa: usa solo quelle che ti servono.

| Istruzione | A cosa serve |
| --- | --- |
| `description "testo"` | La descrizione mostrata nella pagina Plugins |
| `setting "chiave", "Etichetta", "valore predefinito"` | Un campo nella pagina Plugins, per esempio l'indirizzo di un server |
| `secret "chiave", "Etichetta"` | Come `setting`, ma il testo è nascosto (password, token, API key) |
| `settings.chiave` | Legge quello che l'utente ha scritto in un campo |
| `onRename { da, a -> … }` | Viene chiamato dopo **ogni file** rinominato |
| `onRenameBatch { rinomine -> … }` | Viene chiamato **una volta per gruppo** di file rinominati insieme |
| `binding("nome") { m -> … }` | Crea `{plugin.nome}` da usare nei formati |
| `action("Nome", "Spiegazione") { cartella -> … }` | Un pulsante nella pagina Plugins che lavora su una cartella scelta dall'utente |
| `log "messaggio"` | Scrive nel Plugin log (e nel terminale, dalla riga di comando) |
| `progress fatti, totale, "testo"` | Mostra la barra di avanzamento nella scheda del plugin, per esempio *12 of 40* |
| `cancelled()` | Diventa vero quando l'utente preme **Stop** |
| `trash file` | Sposta un file nel cestino |

### Impostazioni: `setting`, `secret` e `settings`

```groovy
setting "server", "Indirizzo del server", "http://localhost:8096"
secret  "apiKey", "API key"

onRenameBatch { rinomine ->
    log "Userò ${settings.server}"
}
```

Nella pagina Plugins il plugin mostra i campi *Indirizzo del server* e *API key*. Quello che scrivi viene salvato appena lasci il campo e il plugin lo legge con `settings.server` e `settings.apiKey`. Se il campo è vuoto si usa il valore predefinito.

### Reagire alle rinomine: `onRename` e `onRenameBatch`

`da` e `a` sono il file prima e dopo la rinomina: `a.name` è il nome del file, `a.parentFile` la cartella, `a.path` il percorso completo.

```groovy
onRename { da, a ->
    log "${da.name} → ${a.path}"
}
```

Quando rinomini 50 episodi insieme, `onRename` viene chiamato 50 volte. Per le cose da fare una volta sola (avvisare un media server, mandare una notifica) usa `onRenameBatch`: riceve la lista di tutte le coppie `[da, a]`.

```groovy
onRenameBatch { rinomine ->
    log "Rinominati ${rinomine.size()} file"
    rinomine.each { coppia -> log "  ${coppia[1].name}" }   // coppia[0] = prima, coppia[1] = dopo
}
```

Le rinomine di prova (azione **test**) non chiamano i plugin, perché non spostano nulla.

### Nuovi valori per i formati: `binding`

```groovy
binding("qualita") { m ->
    def altezza = 0
    try { altezza = m.height as int } catch (e) { }     // l'altezza del video la legge MediaInfo
    altezza >= 2000 ? "4K" : altezza >= 1000 ? "1080p" : altezza >= 700 ? "720p" : "SD"
}
```

Adesso nei formati puoi scrivere `{n} ({y}) [{plugin.qualita}]` e ottenere per esempio `Inception (2010) [1080p]`. `m` contiene gli stessi valori dei formati: `m.n`, `m.y`, `m.s`, `m.e`, `m.height` …

### Pulsanti: `action`

```groovy
action("Conta i video", "Conta i video in una cartella") { cartella ->
    def quanti = 0
    cartella.eachFileRecurse(groovy.io.FileType.FILES) { f ->
        if (f.name ==~ /(?i).+\.(mkv|mp4|avi)$/) quanti++
    }
    log "${quanti} video in ${cartella}"
}
```

Nella scheda del plugin compare il pulsante **Conta i video**. Quando lo premi ReNameo chiede una cartella, esegue il codice in sottofondo e mostra il risultato nel Plugin log.

### Barra di avanzamento e Stop: `progress` e `cancelled()`

Mentre un plugin lavora, la sua scheda mostra cosa sta facendo, da quanto tempo e una barra che scorre, con il pulsante **Stop**. Se il lavoro è lungo, di' a ReNameo a che punto sei: la barra diventa precisa e l'utente vede *12 of 40 · nome-del-file*.

```groovy
action("Controlla i video", "Esempio con barra e Stop") { cartella ->
    def video = cartella.listFiles().findAll { it.name ==~ /(?i).+\.(mkv|mp4)$/ }
    for (int i = 0; i < video.size(); i++) {
        if (cancelled()) break              // l'utente ha premuto Stop
        progress i + 1, video.size(), video[i].name
        // … il lavoro su video[i] …
    }
}
```

`progress "testo"` mostra solo un messaggio, senza numeri. Dentro le closure come `each` o `eachFileRecurse`, dove `break` non si può usare (e `return` salta solo un elemento), usa `if (cancelled()) throw new InterruptedException()`: ReNameo lo registra come *stopped*, non come errore. Stop prova anche a interrompere le attese in corso, ma non tutte si lasciano interrompere: controllare `cancelled()` è il modo sicuro.

## Esempio completo: notifiche su Telegram

Questo plugin manda un messaggio su Telegram dopo ogni rinomina, ha un pulsante per provarlo e aggiunge `{plugin.qualita}` ai formati.

```groovy
// Manda un messaggio su Telegram quando ReNameo rinomina dei file.

description "Messaggio su Telegram dopo ogni rinomina"

secret  "token", "Token del bot"
setting "chat",  "Chat ID"

def invia = { String testo ->
    def url = "https://api.telegram.org/bot${settings.token}/sendMessage"
    def c = new URL(url).openConnection()
    c.requestMethod = "POST"
    c.doOutput = true
    c.setRequestProperty("Content-Type", "application/json")
    c.outputStream.withWriter("UTF-8") { out ->
        out << groovy.json.JsonOutput.toJson([chat_id: settings.chat, text: testo])
    }
    log "Telegram ha risposto ${c.responseCode}"
}

onRenameBatch { rinomine ->
    def nomi = rinomine.collect { it[1].name }
    invia("ReNameo ha rinominato ${nomi.size()} file:\n" + nomi.take(10).join("\n"))
}

action("Invia un messaggio di prova") { cartella ->
    invia("Prova da ReNameo: funziona!")
}

binding("qualita") { m ->
    def altezza = 0
    try { altezza = m.height as int } catch (e) { }
    altezza >= 2000 ? "4K" : altezza >= 1000 ? "1080p" : altezza >= 700 ? "720p" : "SD"
}
```

Per usarlo:

1. Su Telegram scrivi a **@BotFather**, crea un bot con `/newbot` e copia il token.
2. Manda un messaggio qualsiasi al tuo bot, poi apri `https://api.telegram.org/bot<TOKEN>/getUpdates` nel browser: il numero dopo `"chat":{"id":` è il Chat ID.
3. Salva il plugin come `telegram.groovy` nella cartella dei plugin, premi **Reload**, compila i due campi e premi **Invia un messaggio di prova**.

Il Plugin log mostra *Telegram ha risposto 200* se tutto è a posto. *401* vuol dire token sbagliato, *400* Chat ID sbagliato.

## Provare e correggere un plugin

- Dopo ogni modifica al file premi **Reload** nella pagina Plugins.
- Se il plugin contiene un errore, la sua scheda diventa rossa e mostra il messaggio, per esempio `MissingPropertyException: No such property: sttings`: di solito è un nome scritto male.
- Nel **Plugin log** ogni riga ha l'ora, il nome del plugin e un segno: ▶ inizio, ✓ finito (con la durata), ■ fermato, ✕ errore. Il log tiene le ultime 2000 righe, anche quelle scritte mentre la pagina era chiusa.
- Puoi filtrare per plugin o mostrare solo gli errori, selezionare righe e copiarle (⌘C, oppure clic destro → *Copy*), salvarle con *Save…* o svuotare il log con *Clear*. Comodo per mandare un errore a chi ti aiuta.
- Sotto i pulsanti della scheda, *Last run* dice quando il plugin ha lavorato l'ultima volta e com'è andata.
- Usa `log` per vedere cosa succede: `log "valore: ${qualcosa}"`.
- Per provare `onRename` senza toccare i tuoi file veri, copia un paio di video in una cartella di prova e rinominali lì (azione **move**, non **test**).
- L'interruttore **On** spegne un plugin senza cancellarlo. Il cestino lo toglie del tutto.

Alcune cose di Groovy che fanno inciampare:

- `==~` confronta **tutto** il testo, `=~` cerca **una parte**: `"film.mkv" ==~ /.+\.mkv/` è vero, `"film.mkv" ==~ /\.mkv/` no.
- Un testo con `${…}` non è un normale `String`: se lo metti in una lista o in un insieme da confrontare, aggiungi `.toString()`.
- Nell'app i plugin lavorano in sottofondo; dalla riga di comando il programma aspetta che abbiano finito. Evita attese lunghe.

## Plugin su Docker e NAS

Senza interfaccia grafica le impostazioni si danno con variabili d'ambiente: `PLUGIN_` + nome del plugin + nome dell'impostazione, in maiuscolo e con `_` al posto dei trattini.

```yaml
environment:
  - RENAMEO_PLUGINS=jellyfin-refresh,notify     # installa i plugin pronti
  - PLUGIN_JELLYFIN_REFRESH_SERVER=http://jellyfin:8096
  - PLUGIN_JELLYFIN_REFRESH_APIKEY=la-tua-chiave
  - PLUGIN_TELEGRAM_TOKEN=123456:ABC…            # un tuo plugin telegram.groovy in /config/plugins
  - PLUGIN_TELEGRAM_CHAT=42
```

Le variabili d'ambiente hanno la precedenza su quello che è scritto nella pagina Plugins. Il nome esatto di ogni variabile appare anche passando il mouse su un campo delle impostazioni.

## Il tuo primo script

Uno script è un file `.groovy` nella cartella `scripts` che lanci dal terminale.

1. Crea `scripts/ciao.groovy`:

   ```groovy
   log.info "Ciao! Hai passato ${args.size()} cartelle o file"
   args.each { log.info "  ${it}" }
   ```

2. Lancialo:

   ```bash
   renameo -script fn:ciao ~/Downloads
   ```

`fn:ciao` significa "lo script `ciao.groovy` nella cartella `scripts`". Puoi anche indicare un percorso qualsiasi: `renameo -script /percorso/mio-script.groovy ~/Downloads`.

### Cosa riceve uno script

| Nome | Cosa contiene |
| --- | --- |
| `args` | I file e le cartelle scritti dopo il nome dello script |
| `_def` | I valori passati con `--def nome=valore`, per esempio `_def.azione` |
| `log.info "…"`, `log.warning "…"` | Scrive nel terminale |
| `now` | La data e l'ora attuali |

### Le funzioni di ReNameo negli script

| Funzione | Cosa fa |
| --- | --- |
| `rename(file: lista, …)` o `rename(folder: cartella, …)` | Rinomina, come `renameo -rename` |
| `getMissingSubtitles(folder: cartella, lang: "it")` | Scarica i sottotitoli mancanti |
| `getSubtitles(file: lista, lang: "it")` | Scarica i sottotitoli anche se esistono già |
| `check(file: lista)` / `compute(file: lista)` | Verifica o crea file di checksum (SFV, MD5 …) |
| `extract(file: archivio)` | Estrae archivi RAR, ZIP, 7z |
| `fetchEpisodeList(query: "Dark", db: "TheMovieDB::TV", format: "{s00e00} {t}")` | L'elenco degli episodi di una serie |
| `getMediaInfo(file, "{vf} {ac}")` | Informazioni tecniche su un video |
| `parseEpisodeNumber(file)` | Stagione ed episodio letti dal nome (niente se è un film) |
| `detectSeriesName(file)` / `detectMovie(file, false)` | Riconosce la serie o il film |
| `execute("comando", "argomento")` | Lancia un programma esterno |
| `die "messaggio"` | Ferma lo script con un errore |

I parametri di `rename` sono gli stessi della riga di comando: `db` (`TheMovieDB`, `TheMovieDB::TV`, `TVmaze`, `AniDB` …), `format`, `action` (`test`, `move`, `copy`, `hardlink`, `symlink`), `output`, `conflict`, `lang`, `query`, `order`, `filter` e `strict: false` per essere più tolleranti (serve quando ci sono più serie insieme).

## Esempio completo: ordinare i download

Questo script prende tutti i video di una cartella, anche nelle sottocartelle, e mette gli episodi in *Serie TV* e i film in *Film*, con i nomi giusti.

```groovy
// Rinomina i video di una cartella: le serie in "Serie TV", i film in "Film".
// Uso: renameo -script fn:ordina-download ~/Downloads --def azione=move destinazione=/Volumes/Media
// Senza --def azione=... fa solo una prova (test) e non tocca nulla.

def azione = _def.azione ?: 'test'
def destinazione = _def.destinazione ?: "${System.getProperty('user.home')}/Media"

// tutti i video nelle cartelle passate, anche nelle sottocartelle
def video = []
args.each { cartella ->
    cartella.eachFileRecurse(groovy.io.FileType.FILES) { f ->
        if (f.name ==~ /(?i).+\.(mkv|mp4|avi|m4v)$/) video << f
    }
}

// se nel nome c'è S01E02 o 1x02 è un episodio, altrimenti un film
def (episodi, film) = video.split { parseEpisodeNumber(it) != null }
log.info "${episodi.size()} episodi, ${film.size()} film"

if (episodi) {
    rename(file: episodi, db: 'TheMovieDB::TV', action: azione, strict: false,
           format: "${destinazione}/Serie TV/{n}/Season {s}/{n} - {s00e00} - {t}")
}
if (film) {
    rename(file: film, db: 'TheMovieDB', action: azione, strict: false,
           format: "${destinazione}/Film/{n} ({y})/{n} ({y})")
}
```

Prima fai una prova, che mostra cosa succederebbe senza toccare nulla:

```bash
renameo -script fn:ordina-download ~/Downloads --def destinazione=/Volumes/Media
```

Se i nomi vanno bene, esegui davvero:

```bash
renameo -script fn:ordina-download ~/Downloads --def azione=move destinazione=/Volumes/Media
```

Le rinomine fatte da uno script finiscono nella cronologia (si possono annullare dall'app) e attivano i plugin, per esempio l'avviso a Jellyfin.

### Farlo girare da solo

Su macOS e Linux puoi lanciare lo script a orari fissi con `cron` (`crontab -e`). Questa riga lo esegue ogni ora (`which renameo` mostra il percorso completo da usare):

```bash
0 * * * * /usr/local/bin/renameo -script fn:ordina-download /percorso/Downloads --def azione=move destinazione=/percorso/Media
```

Con Docker metti lo script in `/config/scripts` e lancialo così:

```bash
docker run --rm -e TMDB_API_KEY=la-tua-chiave -v /percorso/config:/config -v /percorso/media:/media \
  ghcr.io/m4st3r-0day/renameo -script fn:ordina-download /media/downloads --def azione=move destinazione=/media
```

## Sicurezza

Plugin e script possono fare tutto quello che puoi fare tu: leggere, spostare e cancellare file, usare la rete. Installa solo file di cui ti fidi e leggili prima di usarli. I **formati** invece sono protetti: `{…}` non può eseguire programmi né modificare file, anche quando usa `{plugin.nome}`.

## Condividere un plugin

Un plugin è un solo file: per condividerlo basta mandare il `.groovy`. Chi lo riceve lo mette nella propria cartella `plugins` e preme **Reload**. I plugin pronti dell'app sono scritti nello stesso modo e sono un buon punto di partenza: si trovano in `source/net/renameo/plugins/catalog` nel repository, e ogni plugin installato si può aprire e modificare con **Open folder**.
