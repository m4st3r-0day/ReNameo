#!/usr/bin/env python3
"""Convert the user guides in docs/ to the HTML help pages bundled with the app.

Supports the Markdown subset used by the guides: headings, paragraphs, bullet and
numbered lists, tables, fenced code blocks, inline code, bold, italic and links.

Usage: python3 tools/md2html.py   (run from the project root after editing the guides in docs/)
"""

import html
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "source", "net", "renameo", "resources", "help")
# source => (page, language, section)
PAGES = {
    "GUIDA_GUI.md": ("guida-gui.html", "it", "gui"),
    "GUIDA_CLI.md": ("guida-cli.html", "it", "cli"),
    "USER_GUIDE_GUI.md": ("guide-gui.html", "en", "gui"),
    "USER_GUIDE_CLI.md": ("guide-cli.html", "en", "cli"),
    "GUIDA_PLUGIN.md": ("guida-plugin.html", "it", "plugins"),
    "USER_GUIDE_PLUGINS.md": ("guide-plugins.html", "en", "plugins"),
}
LABELS = {"it": {"gui": "Interfaccia grafica", "cli": "Riga di comando", "plugins": "Plugin e script"}, "en": {"gui": "Desktop app", "cli": "Command line", "plugins": "Plugins & scripts"}}

CSS = """
:root { color-scheme: dark; }
body { margin: 0; background: #0D111C; color: #E8ECF4; font: 14px/1.6 -apple-system, "Helvetica Neue", "Segoe UI", sans-serif; }
main { max-width: 860px; margin: 0 auto; padding: 32px 28px 64px; }
nav { display: flex; gap: 8px; margin-bottom: 24px; }
nav .lang { margin-left: auto; }
nav a { padding: 6px 14px; border-radius: 10px; background: #121827; border: 1px solid #232B3E; color: #E8ECF4; text-decoration: none; }
nav a.active { background: #3B82F6; border-color: #3B82F6; color: #fff; }
h1 { font-size: 28px; margin: 0 0 16px; }
h2 { font-size: 20px; margin: 36px 0 10px; padding-top: 12px; border-top: 1px solid #232B3E; }
h3 { font-size: 16px; margin: 24px 0 8px; }
p, li { color: #C9D0DE; }
a { color: #60A5FA; }
code { font: 12.5px/1.5 "SF Mono", Menlo, Consolas, monospace; background: #1A2133; border-radius: 6px; padding: 1px 6px; color: #E8ECF4; }
pre { background: #0A0E17; border: 1px solid #232B3E; border-radius: 12px; padding: 14px 16px; overflow-x: auto; }
pre code { background: none; padding: 0; }
table { border-collapse: collapse; width: 100%; margin: 12px 0; font-size: 13px; }
th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #232B3E; vertical-align: top; }
th { color: #8B93A7; font-weight: 600; }
strong { color: #E8ECF4; }
"""


def inline(text):
    parts = re.split(r"(`[^`]+`)", text)
    out = []
    for part in parts:
        if part.startswith("`") and part.endswith("`") and len(part) > 1:
            out.append("<code>%s</code>" % html.escape(part[1:-1]))
            continue
        s = html.escape(part, quote=False)
        s = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", lambda m: '<a href="%s">%s</a>' % (link(m.group(2)), m.group(1)), s)
        s = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", s)
        s = re.sub(r"(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])", r"<em>\1</em>", s)
        out.append(s)
    return "".join(out)


def link(target):
    return PAGES[target][0] if target in PAGES else target


def page_for(lang, section):
    for page, l, s in PAGES.values():
        if l == lang and s == section:
            return page


def convert(lines):
    out = []
    i = 0
    while i < len(lines):
        line = lines[i].rstrip("\n")
        if line.startswith("```"):
            code = []
            i += 1
            while i < len(lines) and not lines[i].startswith("```"):
                code.append(lines[i].rstrip("\n"))
                i += 1
            out.append("<pre><code>%s</code></pre>" % html.escape("\n".join(code)))
            i += 1
            continue
        m = re.match(r"^(#{1,3})\s+(.*)$", line)
        if m:
            level = len(m.group(1))
            out.append("<h%d>%s</h%d>" % (level, inline(m.group(2)), level))
            i += 1
            continue
        if line.startswith("|"):
            rows = []
            while i < len(lines) and lines[i].startswith("|"):
                rows.append([c.strip() for c in lines[i].strip().strip("|").split(" | ")])
                i += 1
            head, body = rows[0], [r for r in rows[1:] if not re.match(r"^-+$", r[0].replace(" ", ""))]
            out.append("<table><thead><tr>%s</tr></thead><tbody>%s</tbody></table>" % (
                "".join("<th>%s</th>" % inline(c) for c in head),
                "".join("<tr>%s</tr>" % "".join("<td>%s</td>" % inline(c) for c in r) for r in body)))
            continue
        m = re.match(r"^(\s*)([-*]|\d+\.)\s+(.*)$", line)
        if m:
            tag = "ol" if m.group(2)[0].isdigit() else "ul"
            items = []
            while i < len(lines):
                current = lines[i].rstrip("\n")
                m = re.match(r"^(\s*)([-*]|\d+\.)\s+(.*)$", current)
                if m:
                    items.append([inline(m.group(3))])
                    i += 1
                    continue
                if not current.strip():
                    # a blank line ends the list, unless an indented block or the next item follows
                    j = i
                    while j < len(lines) and not lines[j].strip():
                        j += 1
                    if j < len(lines) and (lines[j].startswith("  ") or re.match(r"^([-*]|\d+\.)\s", lines[j])):
                        i = j
                        continue
                    break
                if current.startswith("  ") and current.strip().startswith("```"):
                    # fenced code inside a list item
                    indent = len(current) - len(current.lstrip())
                    code = []
                    i += 1
                    while i < len(lines) and not lines[i].strip().startswith("```"):
                        code.append(lines[i].rstrip("\n")[indent:])
                        i += 1
                    items[-1].append("<pre><code>%s</code></pre>" % html.escape("\n".join(code)))
                    i += 1
                    continue
                if current.startswith("  "):
                    items[-1].append("<p>%s</p>" % inline(current.strip()))
                    i += 1
                    continue
                break
            out.append("<%s>%s</%s>" % (tag, "".join("<li>%s</li>" % "".join(parts) for parts in items), tag))
            continue
        if not line.strip():
            i += 1
            continue
        para = []
        while i < len(lines) and lines[i].strip() and not re.match(r"^(#|```|\||\s*([-*]|\d+\.)\s)", lines[i]):
            para.append(lines[i].strip())
            i += 1
        out.append("<p>%s</p>" % inline(" ".join(para)))
    return "\n".join(out)


def main():
    os.makedirs(OUT, exist_ok=True)
    for source, (target, lang, section) in PAGES.items():
        with open(os.path.join(ROOT, "docs", source), encoding="utf-8") as f:
            lines = f.readlines()
        title = re.sub(r"^#\s+", "", lines[0]).strip()
        nav = "".join('<a href="%s"%s>%s</a>' % (page_for(lang, s), ' class="active"' if s == section else "", LABELS[lang][s]) for s in ("gui", "cli", "plugins"))
        other = "en" if lang == "it" else "it"
        nav += '<a class="lang" href="%s">%s</a>' % (page_for(other, section), "English" if other == "en" else "Italiano")
        page = '<!doctype html><html lang="%s"><head><meta charset="utf-8"><title>%s</title><style>%s</style></head><body><main><nav>%s</nav>%s</main></body></html>\n' % (
            lang, html.escape(title), CSS, nav, convert(lines))
        with open(os.path.join(OUT, target), "w", encoding="utf-8") as f:
            f.write(page)
        print("wrote", os.path.relpath(os.path.join(OUT, target), ROOT))


if __name__ == "__main__":
    main()
