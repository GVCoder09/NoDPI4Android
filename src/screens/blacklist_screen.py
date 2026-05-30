"""
Blacklist Editor Screen
"""

import os

from kivy.uix.textinput import TextInput
from kivymd.uix.appbar import MDTopAppBar, MDTopAppBarTitle
from kivymd.uix.boxlayout import MDBoxLayout
from kivymd.uix.button import MDButton, MDButtonText
from kivymd.uix.scrollview import MDScrollView

from models.config_managers import BlacklistManager
from screens.base_screen import BaseScreen


class BlacklistScreen(BaseScreen):
    """Blacklist Editor Screen"""

    def __init__(self, app, **kwargs):
        super().__init__(name="blacklist", app=app, **kwargs)

        self.blacklist_manager = BlacklistManager(
            os.path.join(app.user_data_dir, "blacklist.txt")
        )
        self.build_ui()
        self.load_blacklist()

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
            MDTopAppBarTitle(text="Blacklist Editor", halign="center"),
            type="small",
            elevation=0,
        )

    def _create_content(self):
        """Create content"""

        content = MDBoxLayout(orientation="vertical")

        scroll_view = MDScrollView()

        self.blacklist_input = TextInput(
            text="",
            multiline=True,
            size_hint_y=None,
            height=200,
            hint_text="Enter domains (one per line)",
            padding=(12, 12),
            background_color=(0.95, 0.95, 0.95, 1),
            foreground_color=(0, 0, 0, 1),
            cursor_color=(0, 0, 0, 1),
            selection_color=(0.7, 0.7, 1, 0.5),
            font_size="16sp",
            base_direction="ltr",
            write_tab=False,
            use_bubble=True,
            use_handles=True,
            allow_copy=True,
        )

        def update_height(instance, value):
            instance.height = max(200, instance.minimum_height)

        self.blacklist_input.bind(minimum_height=update_height)

        scroll_view.add_widget(self.blacklist_input)
        content.add_widget(scroll_view)

        save_button = MDButton(
            MDButtonText(text="Save Blacklist"),
            style="filled",
            pos_hint={"center_x": 0.5},
            on_release=lambda x: self.save_blacklist(),
        )
        content.add_widget(save_button)

        return content

    def on_pre_enter(self, *args):
        """Load blacklist on enter"""

        self.load_blacklist()

    def load_blacklist(self):
        """Load blacklist from file"""

        blacklist = self.blacklist_manager.load()
        self.blacklist_input.text = "\n".join(blacklist)

    def save_blacklist(self):
        """Save blacklist to file"""

        text = self.blacklist_input.text.strip()
        domains = [line.strip() for line in text.splitlines() if line.strip()]

        self.blacklist_manager.save(domains)
        self.show_snackbar(f"Blacklist saved with {len(domains)} domains")
        self.app.switch_screen("main")
