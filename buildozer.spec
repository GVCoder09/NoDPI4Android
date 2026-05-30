[app]

title = NoDPI
package.name = nodpi
package.domain = com.gvcoder
version = 2.0

source.dir = ./src
source.include_exts = py,png,jpg,kv,txt

requirements = kivy,https://github.com/kivymd/kivymd/archive/master.zip,android,pyjnius,materialyoucolor,pillow,asynckivy,asyncgui

presplash.filename = ./assets/presplash.png
icon.filename = ./assets/ico.png
orientation = portrait
fullscreen = 0

p4a.branch = master

[android]

services = proxy:%(source.dir)s/service.py:foreground:sticky
android.permissions = INTERNET,FOREGROUND_SERVICE,POST_NOTIFICATIONS,REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
android.accept_sdk_license = True

[buildozer]

log_level = 1