"""
Base Screen
"""

from kivy.metrics import dp
from kivymd.uix.screen import MDScreen
from kivymd.uix.snackbar import MDSnackbar, MDSnackbarText


class BaseScreen(MDScreen):
    """Base Screen"""

    def __init__(self, name: str, app, **kwargs):
        super().__init__(name=name, **kwargs)
        self.app = app

    def show_snackbar(self, message: str):
        """Show snackbar message"""

        MDSnackbar(
            MDSnackbarText(text=message),
            y=dp(24),
            pos_hint={"center_x": 0.5},
            size_hint_x=0.9,
        ).open()
