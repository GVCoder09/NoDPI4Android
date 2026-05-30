"""
Constants module
"""

APP_VERSION = "2.0-beta"

BLACKLIST_FILENAME = "blacklist.txt"
PROXY_CONFIG_FILENAME = "proxy_config.json"
APP_SETTINGS_FILENAME = "app_settings.json"

DEFAULT_PROXY_CONFIG = {"host": "127.0.0.1", "port": "8881"}

DEFAULT_APP_SETTINGS = {
    "first_run": True,
    "fragment_method": "sni",
    "blacklist": "standard",
    "matching": "strict",
}

DEFAULT_BLACKLIST = [
    "youtube.com",
    "youtu.be",
    "yt.be",
    "googlevideo.com",
    "ytimg.com",
    "yting.com",
    "ggpht.com",
    "gvt1.com",
    "youtube-nocookie.com",
    "youtube-ui.l.google.com",
    "youtubeembeddedplayer.googleapis.com",
    "youtube.googleapis.com",
    "youtubei.googleapis.com",
    "jnn-pa.googleapis.com",
    "yt-video-upload.l.google.com",
    "wide-youtube.l.google.com",
    "yt3.googleusercontent.com",
    "youtubekids.com"
]
