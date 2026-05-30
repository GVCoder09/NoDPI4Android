#!/usr/bin/env python3

"""
NoDPI
=====

NoDPI is a utility for bypassing the DPI (Deep Packet Inspection) system
"""

import os

from kivy.clock import Clock
from kivy.config import Config
from kivy.core.window import Window
from kivymd.app import MDApp
from kivymd.uix.boxlayout import MDBoxLayout
from kivymd.uix.button import MDButton, MDButtonText
from kivymd.uix.dialog import (
    MDDialog,
    MDDialogButtonContainer,
    MDDialogHeadlineText,
    MDDialogSupportingText,
)
from kivymd.uix.navigationbar import MDNavigationBar
from kivymd.uix.screenmanager import MDScreenManager

from models.config_managers import AppSettingsManager
from screens.about_screen import AboutScreen
from screens.blacklist_screen import BlacklistScreen
from screens.main_screen import MainScreen
from screens.settings_screen import SettingsScreen
from widgets.navigation import NavigationItem

Config.set("kivy", "log_level", "warning")
Config.write()


class ProxyApp(MDApp):
    """Main app class"""

    def __init__(self, **kwargs):
        super().__init__(**kwargs)

        self.screen_manager = None
        self.navigation_bar = None
        self.main_nav_item = None
        self.blacklist_nav_item = None
        self.settings_nav_item = None
        self.about_nav_item = None
        self.settings_manager = None

    def build(self):
        """Build app"""

        self.theme_cls.theme_style = "Dark"
        self.theme_cls.primary_palette = "Mediumslateblue"

        Window.bind(on_keyboard=self.on_keyboard)

        self.settings_manager = AppSettingsManager(
            os.path.join(self.user_data_dir, "app_settings.json")
        )

        self.screen_manager = MDScreenManager()

        screens = [
            MainScreen(self),
            BlacklistScreen(self),
            SettingsScreen(self),
            AboutScreen(self),
        ]

        for screen in screens:
            self.screen_manager.add_widget(screen)

        main_layout = MDBoxLayout(orientation="vertical")
        main_layout.add_widget(self.screen_manager)

        self.navigation_bar = self._create_navigation_bar()
        self.navigation_bar.set_active_item(self.main_nav_item)
        main_layout.add_widget(self.navigation_bar)

        self.check_first_run()

        return main_layout

    def on_keyboard(self, window, key, *args):
        """Handle keyboard events"""

        if key == 27:
            screen = self.screen_manager.current

            if screen != "main":
                self.screen_manager.current = "main"
                self.navigation_bar.set_active_item(self.main_nav_item)

                return True

            return False

    def on_switch_tabs(self, bar, item, item_icon, item_text):
        """Handle navigation bar events"""

        screen_map = {
            "Control": "main",
            "Blacklist": "blacklist",
            "Settings": "settings",
            "About": "about",
        }

        screen_name = screen_map.get(item_text, "main")
        self.switch_screen(screen_name)

    def switch_screen(self, screen_name: str):
        """Switch screen"""

        self.screen_manager.current = screen_name

    def check_first_run(self):
        """Check if first run"""

        settings = self.settings_manager.load()

        if settings.get("first_run", True):
            Clock.schedule_once(self.show_disclaimer_dialog, 1)
            settings["first_run"] = False
            self.settings_manager.save(settings)

    def show_disclaimer_dialog(self, dt):
        """Show disclaimer dialog"""

        disclaimer_dialog = MDDialog(
            MDDialogHeadlineText(text="Disclaimer"),
            MDDialogSupportingText(
                text="""
IMPORTANT: Please read this disclaimer carefully before using NoDPI.

The developer and/or supplier of this software shall not be liable for any loss or damage, including but not limited to direct, indirect, incidental, punitive or consequential damages arising out of the use of or inability to use this software.

The user is solely responsible for compliance with all applicable laws and regulations when using this software. Use at your own risk.
""",
                halign="left",
            ),
            MDDialogButtonContainer(
                MDButton(
                    MDButtonText(text="I Understand & Accept"),
                    style="filled",
                    on_release=lambda x: disclaimer_dialog.dismiss(),
                ),
            ),
        )
        disclaimer_dialog.open()


if __name__ == "__main__":
    ProxyApp().run()
