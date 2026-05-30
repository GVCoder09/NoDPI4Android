"""
About Screen
"""

from kivymd.uix.appbar import MDTopAppBar, MDTopAppBarTitle
from kivymd.uix.boxlayout import MDBoxLayout
from kivymd.uix.button import MDButton, MDButtonText
from kivymd.uix.dialog import (
    MDDialog,
    MDDialogButtonContainer,
    MDDialogHeadlineText,
    MDDialogSupportingText,
)
from kivymd.uix.label import MDLabel
from kivymd.uix.scrollview import MDScrollView

from constants import APP_VERSION
from screens.base_screen import BaseScreen


class AboutScreen(BaseScreen):
    """About Screen"""

    def __init__(self, app, **kwargs):
        super().__init__(name="about", app=app, **kwargs)

        self.build_ui()

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
            MDTopAppBarTitle(text="About", halign="center"), type="small", elevation=0
        )

    def _create_content(self):
        """Create content"""

        content = MDScrollView()

        about_layout = MDBoxLayout(
            orientation="vertical",
            padding=["20dp", "20dp", "20dp", "20dp"],
            spacing="20dp",
            size_hint_y=None,
            adaptive_height=True,
        )

        app_info = MDLabel(
            text=f"""NoDPI for Android v{APP_VERSION}

A graphical interface for the NoDPI utility that helps bypass DPI (Deep Packet Inspection) systems.

Features:
• HTTPS proxy with TCP fragmentation
• Custom blacklist management  
• Multiple fragmentation methods
• No data collection

Written in Python using KivyMD.
""",
            size_hint_x=1,
            size_hint_y=None,
            adaptive_height=True,
            halign="left",
            valign="top",
            line_height=1.2,
            padding=["0dp", "0dp", "0dp", "0dp"],
            theme_text_color="Primary",
        )

        def update_text_size(instance, value):
            instance.text_size = (instance.width, None)

        app_info.bind(width=update_text_size)
        app_info.text_size = (app_info.width, None)

        about_layout.add_widget(app_info)

        disclaimer_button = MDButton(
            MDButtonText(text="View Disclaimer"),
            style="outlined",
            pos_hint={"center_x": 0.5},
            on_release=lambda x: self.show_disclaimer(),
        )
        about_layout.add_widget(disclaimer_button)

        about_layout.bind(minimum_height=about_layout.setter("height"))
        content.add_widget(about_layout)

        return content

    def show_disclaimer(self):
        """Show the disclaimer dialog"""

        disclaimer_dialog = MDDialog(
            MDDialogHeadlineText(text="Disclaimer"),
            MDDialogSupportingText(
                text="""
The developer and/or supplier of this software shall not be liable for any loss or damage, including but not limited to direct, indirect, incidental, punitive or consequential damages arising out of the use of or inability to use this software, even if the developer or supplier has been advised of the possibility of such damages.

The developer and/or supplier of this software shall not be liable for any legal consequences arising out of the use of this software. This includes, but is not limited to, violation of laws, rules or regulations, as well as any claims or suits arising out of the use of this software. The user is solely responsible for compliance with all applicable laws and regulations when using this software.
""",
                halign="left",
            ),
            MDDialogButtonContainer(
                MDButton(
                    MDButtonText(text="I Understand"),
                    style="text",
                    on_release=lambda x: disclaimer_dialog.dismiss(),
                ),
            ),
        )
        disclaimer_dialog.open()
