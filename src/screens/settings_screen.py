"""
Settings Screen
"""

import os

from kivy.metrics import dp
from kivy.uix.widget import Widget
from kivymd.uix.appbar import MDTopAppBar, MDTopAppBarTitle
from kivymd.uix.boxlayout import MDBoxLayout
from kivymd.uix.button import MDButton, MDButtonText
from kivymd.uix.card import MDCard
from kivymd.uix.label import MDLabel
from kivymd.uix.menu import MDDropdownMenu
from kivymd.uix.scrollview import MDScrollView
from kivymd.uix.textfield import MDTextField, MDTextFieldHintText

from models.config_managers import AppSettingsManager, ProxyConfigManager
from screens.base_screen import BaseScreen


class SettingsScreen(BaseScreen):
    """Settings Screen"""

    def __init__(self, app, **kwargs):
        super().__init__(name="settings", app=app, **kwargs)

        self.proxy_manager = ProxyConfigManager(
            os.path.join(app.user_data_dir, "proxy_config.json")
        )
        self.settings_manager = AppSettingsManager(
            os.path.join(app.user_data_dir, "app_settings.json")
        )
        self.build_ui()
        self.load_settings()

    def build_ui(self):
        """Build UI"""

        self.md_bg_color = self.theme_cls.secondaryContainerColor

        main_layout = MDBoxLayout(orientation="vertical")
        title_bar = self._create_title_bar()
        main_layout.add_widget(title_bar)

        content = self._create_content()
        main_layout.add_widget(content)

        self.add_widget(main_layout)

    def _create_title_bar(self):
        """Create title bar"""

        return MDTopAppBar(
            MDTopAppBarTitle(text="Settings", halign="center"),
            type="small",
        )

    def _create_content(self):
        """Create content"""

        scroll_view = MDScrollView()

        main_content = MDBoxLayout(
            orientation="vertical",
            adaptive_height=True,
            padding=["20dp", "20dp", "20dp", "40dp"],
            spacing="25dp",
            size_hint_y=None,
        )
        main_content.bind(minimum_height=main_content.setter("height"))

        proxy_section = self._create_proxy_section()
        main_content.add_widget(proxy_section)

        fragment_section = self._create_fragment_section()
        main_content.add_widget(fragment_section)

        matching_section = self._create_matching_section()
        main_content.add_widget(matching_section)

        options_section = self._create_blacklist_section()
        main_content.add_widget(options_section)

        spacer = Widget(size_hint_y=1)
        main_content.add_widget(spacer)

        save_button = MDButton(
            MDButtonText(text="Save Settings"),
            style="filled",
            size_hint_y=None,
            height=dp(50),
            on_release=lambda x: self.save_settings(),
        )

        main_content.add_widget(save_button)
        scroll_view.add_widget(main_content)

        return scroll_view

    def _create_proxy_section(self):
        """Create proxy section"""

        section = MDCard(
            style="elevated", size_hint_y=None, height=dp(180), padding="15dp"
        )

        content_layout = MDBoxLayout(orientation="vertical", spacing="15dp")

        section_title = MDLabel(
            text="Proxy Settings", size_hint_y=None, height=dp(25), bold=True
        )
        content_layout.add_widget(section_title)

        host_layout = MDBoxLayout(
            orientation="horizontal", spacing="10dp", size_hint_y=None, height=dp(52)
        )

        self.host_input = MDTextField(
            MDTextFieldHintText(text="Host (e.g. 127.0.0.1)"),
            mode="outlined",
            size_hint_x=0.7,
            size_hint_y=None,
            height=dp(48),
        )

        host_layout.add_widget(self.host_input)
        content_layout.add_widget(host_layout)

        port_layout = MDBoxLayout(
            orientation="horizontal", size_hint_y=None, height=dp(52), spacing="10dp"
        )

        self.port_input = MDTextField(
            MDTextFieldHintText(text="Port (e.g. 8881)"),
            mode="outlined",
            size_hint_x=0.7,
            size_hint_y=None,
            height=dp(48),
            input_filter="int",
        )

        port_layout.add_widget(self.port_input)
        content_layout.add_widget(port_layout)
        section.add_widget(content_layout)

        return section

    def _create_fragment_section(self):
        """Create fragment section"""

        section = MDCard(
            style="elevated", size_hint_y=None, height=dp(130), padding="15dp"
        )

        content_layout = MDBoxLayout(orientation="vertical", spacing="15dp")

        section_title = MDLabel(
            text="Fragment Method", size_hint_y=None, height=dp(25), bold=True
        )
        content_layout.add_widget(section_title)

        method_layout = MDBoxLayout(
            orientation="horizontal", spacing="10dp", size_hint_y=None, height=dp(48)
        )

        self.text_fragment_button = MDButtonText(
            text="Random",
        )

        self.fragment_button = MDButton(
            self.text_fragment_button,
            style="outlined",
            size_hint_x=0.6,
            size_hint_y=None,
            height=dp(48),
            on_release=lambda x: self.show_fragment_menu(),
        )

        method_layout.add_widget(self.fragment_button)
        content_layout.add_widget(method_layout)
        section.add_widget(content_layout)

        return section

    def _create_blacklist_section(self):
        """Create blacklist section"""

        section = MDCard(
            style="elevated", size_hint_y=None, height=dp(130), padding="15dp"
        )

        content_layout = MDBoxLayout(orientation="vertical", spacing="15dp")

        section_title = MDLabel(
            text="Blacklist", size_hint_y=None, height=dp(25), bold=True
        )
        content_layout.add_widget(section_title)

        method_layout = MDBoxLayout(
            orientation="horizontal", spacing="10dp", size_hint_y=None, height=dp(48)
        )

        self.text_blacklist_button = MDButtonText(
            text="STANDARD",
        )
        self.blacklist_button = MDButton(
            self.text_blacklist_button,
            style="outlined",
            size_hint_x=0.6,
            size_hint_y=None,
            height=dp(48),
            on_release=lambda x: self.show_blacklist_menu(),
        )

        method_layout.add_widget(self.blacklist_button)
        content_layout.add_widget(method_layout)
        section.add_widget(content_layout)

        return section

    def _create_matching_section(self):
        """Create matching section"""

        section = MDCard(
            style="elevated", size_hint_y=None, height=dp(130), padding="15dp"
        )

        content_layout = MDBoxLayout(orientation="vertical", spacing="15dp")

        section_title = MDLabel(
            text="Domain Matching", size_hint_y=None, height=dp(25), bold=True
        )
        content_layout.add_widget(section_title)

        method_layout = MDBoxLayout(
            orientation="horizontal", spacing="10dp", size_hint_y=None, height=dp(48)
        )

        self.text_matching_button = MDButtonText(
            text="STRICT",
        )
        self.matching_button = MDButton(
            self.text_matching_button,
            style="outlined",
            size_hint_x=0.6,
            size_hint_y=None,
            height=dp(48),
            on_release=lambda x: self.show_matching_menu(),
        )

        method_layout.add_widget(self.matching_button)
        content_layout.add_widget(method_layout)
        section.add_widget(content_layout)

        return section

    def show_fragment_menu(self):
        """Show fragment menu"""

        menu_items = [
            {
                "text": "Random",
                "on_release": lambda x="Random": self.set_fragment_method(x),
            },
            {
                "text": "SNI",
                "on_release": lambda x="SNI": self.set_fragment_method(x),
            },
        ]

        self.fragment_menu = MDDropdownMenu(
            caller=self.fragment_button,
            items=menu_items,
        )
        self.fragment_menu.open()

    def set_fragment_method(self, method: str):
        """Set fragment method"""

        self.text_fragment_button.text = method
        self.fragment_menu.dismiss()

    def show_blacklist_menu(self):
        """Show blacklist menu"""

        menu_items = [
            {
                "text": "STANDARD",
                "on_release": lambda x="STANDARD": self.set_blacklist_method(x),
            },
            {
                "text": "ALL",
                "on_release": lambda x="ALL": self.set_blacklist_method(x),
            },
            {
                "text": "AUTO",
                "on_release": lambda x="AUTO": self.set_blacklist_method(x),
            },
        ]

        self.blacklist_menu = MDDropdownMenu(
            caller=self.blacklist_button,
            items=menu_items,
        )
        self.blacklist_menu.open()

    def set_blacklist_method(self, method: str):
        """Set blacklist method"""

        self.text_blacklist_button.text = method
        self.blacklist_menu.dismiss()

    def show_matching_menu(self):
        """Show matching menu"""

        menu_items = [
            {
                "text": "STRICT",
                "on_release": lambda x="STANDARD": self.set_matching_method(x),
            },
            {
                "text": "LOOSE",
                "on_release": lambda x="LOOSE": self.set_matching_method(x),
            },
        ]

        self.matching_menu = MDDropdownMenu(
            caller=self.matching_button,
            items=menu_items,
        )
        self.matching_menu.open()

    def set_matching_method(self, method: str):
        """Set matching method"""

        self.text_matching_button.text = method
        self.matching_menu.dismiss()

    def load_settings(self):
        """Load all settings"""

        proxy_config = self.proxy_manager.load()
        self.host_input.text = proxy_config.get("host", "127.0.0.1")
        self.port_input.text = str(proxy_config.get("port", "8881"))

        app_settings = self.settings_manager.load()
        self.text_fragment_button.text = app_settings.get(
            "fragment_method", "random"
        ).upper()
        self.text_blacklist_button.text = app_settings.get(
            "blacklist", "standard"
        ).upper()
        self.text_matching_button.text = app_settings.get(
            "matching", "strict").upper()

    def save_settings(self):
        """Save all settings"""

        proxy_config = {
            "host": self.host_input.text.strip(),
            "port": self.port_input.text.strip(),
        }
        self.proxy_manager.save(proxy_config)

        app_settings = {
            "fragment_method": self.text_fragment_button.text.lower(),
            "blacklist": self.text_blacklist_button.text.lower(),
            "matching": self.text_matching_button.text.lower(),
        }
        self.settings_manager.save(app_settings)

        self.show_snackbar("Settings saved successfully")
        self.app.switch_screen("main")
