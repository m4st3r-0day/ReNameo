# Layout of the macOS disk image, used by tools/make-app.sh through dmgbuild:
# the app on the left, Applications on the right, an arrow and short instructions in between.
#
#   dmgbuild -s tools/dmg-settings.py -D app=dist/ReNameo.app -D icon=... -D background=... ReNameo dist/ReNameo.dmg

import os.path

app = defines["app"]
name = os.path.basename(app)

format = "UDZO"
files = [app]
symlinks = {"Applications": "/Applications"}

# the disk shows the app icon in the Finder and on the desktop
icon = defines["icon"]
background = defines["background"]

# 660 x 420 like the background (tools/DmgBackground.java)
window_rect = ((200, 140), (660, 442))
default_view = "icon-view"
show_status_bar = False
show_tab_view = False
show_toolbar = False
show_pathbar = False
show_sidebar = False
icon_size = 112
text_size = 13
icon_locations = {name: (165, 205), "Applications": (495, 205)}
