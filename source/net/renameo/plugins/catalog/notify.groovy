// Send a short message when files were renamed. Fill in only the services you use.
//   ntfy:     the topic address, e.g. https://ntfy.sh/my-secret-topic
//   Discord:  Server settings > Integrations > Webhooks > Copy webhook URL
//   Gotify:   server address and an application token
//   Pushover: your user key and an application token

description "Get a message on your phone (ntfy, Discord, Gotify, Pushover) after renaming"

setting "ntfy", "ntfy topic URL"
setting "discord", "Discord webhook"
setting "gotify", "Gotify server"
secret "gotifyToken", "Gotify token"
setting "pushoverUser", "Pushover user"
secret "pushoverToken", "Pushover token"

def post = { String url, String type, String body, Map headers = [:] ->
    def c = new URL(url).openConnection()
    c.requestMethod = "POST"
    c.doOutput = true
    c.connectTimeout = 10000
    c.setRequestProperty("Content-Type", type)
    headers.each { k, v -> c.setRequestProperty(k, v) }
    c.outputStream.withWriter("UTF-8") { it << body }
    return c.responseCode
}

def form = { Map m -> m.collect { k, v -> "${k}=${URLEncoder.encode(v as String, 'UTF-8')}" }.join('&') }

def send = { String title, String text ->
    if (settings.ntfy) {
        log "ntfy: HTTP ${post(settings.ntfy, 'text/plain; charset=utf-8', text, [Title: title])}"
    }
    if (settings.discord) {
        log "Discord: HTTP ${post(settings.discord, 'application/json', groovy.json.JsonOutput.toJson([content: "**${title}**\n${text}".toString()]))}"
    }
    if (settings.gotify && settings.gotifyToken) {
        log "Gotify: HTTP ${post(settings.gotify.replaceAll('/+$', '') + '/message', 'application/x-www-form-urlencoded', form(title: title, message: text), ['X-Gotify-Key': settings.gotifyToken])}"
    }
    if (settings.pushoverUser && settings.pushoverToken) {
        log "Pushover: HTTP ${post('https://api.pushover.net/1/messages.json', 'application/x-www-form-urlencoded', form(token: settings.pushoverToken, user: settings.pushoverUser, title: title, message: text))}"
    }
}

onRenameBatch { renames ->
    def names = renames.collect { it[1].name }
    def text = names.take(10).join('\n') + (names.size() > 10 ? "\n… and ${names.size() - 10} more" : '')
    send("ReNameo: ${names.size()} ${names.size() == 1 ? 'file' : 'files'} renamed", text)
}

action("Send a test", "Send a test message to the services you filled in") { folder ->
    send("ReNameo", "Test message: notifications work.")
}
