"""
Main Screen
"""

from kivy.clock import Clock, mainthread
from kivymd.uix.appbar import MDTopAppBar, MDTopAppBarTitle
from kivymd.uix.boxlayout import MDBoxLayout
from kivymd.uix.card import MDCard
from kivymd.uix.label import MDLabel

from models.service_managers import ServiceManagerFactory
from screens.base_screen import BaseScreen


class MainScreen(BaseScreen):
    """Main Screen"""

    def __init__(self, app: "ProxyApp", **kwargs):
        super().__init__(name="main", app=app, **kwargs)

        self.service_manager = ServiceManagerFactory.create()
        self.build_ui()
        self.update_button_state()

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
            MDTopAppBarTitle(text="NoDPI for Android", halign="center"), type="small"
        )

    def _create_content(self):
        """Create content"""

        content = MDBoxLayout(
            orientation="vertical", padding="12dp", spacing="12dp", size_hint_y=0.9
        )

        service_card = MDCard(
            style="filled",
            padding="40dp",
            size_hint=(0.8, 0.6),
            pos_hint={"center_x": 0.5, "center_y": 0.5},
            on_release=lambda x: self.toggle_service(),
        )

        service_content = MDBoxLayout(
            orientation="vertical",
            adaptive_height=True,
            pos_hint={"center_x": 0.5, "center_y": 0.5},
        )

        self.status_label = MDLabel(
            id="status_label",
            text="START",
            font_style="Headline",
            theme_font_size="Custom",
            font_size="48sp",
            halign="center",
            adaptive_size=True,
            bold=True,
        )

        self.substatus_label = MDLabel(
            id="substatus_label",
            text="Service is stopped",
            halign="center",
            adaptive_size=True,
        )

        service_content.add_widget(self.status_label)
        service_content.add_widget(self.substatus_label)
        service_card.add_widget(service_content)
        content.add_widget(service_card)

        return content

    def toggle_service(self):
        """Toggle service"""

        if self.service_manager.is_running():
            self.stop_service()
        else:
            self.start_service()

    def start_service(self):
        """Start service"""

        self.service_manager.start()
        Clock.schedule_once(lambda x: self.update_button_state(), 1.5)
        self.show_snackbar("Service started")

    def stop_service(self):
        """Stop service"""

        self.service_manager.stop()
        Clock.schedule_once(lambda x: self.update_button_state(), 1.5)
        self.show_snackbar("Service stopped")

    @mainthread
    def update_button_state(self):
        """Update button state"""

        if self.service_manager.is_running():
            self.status_label.text = "STOP"
            self.substatus_label.text = "Service is running"
        else:
            self.status_label.text = "START"
            self.substatus_label.text = "Service is stopped"
