"""
Navigation Items
"""

from kivymd.uix.navigationbar import MDNavigationItem
from kivymd.uix.navigationbar import MDNavigationItemIcon, MDNavigationItemLabel


class NavigationItem(MDNavigationItem):
    """Custom navigation item"""

    def __init__(self, icon: str, text: str, **kwargs):
        super().__init__(**kwargs)
        self.add_widget(MDNavigationItemIcon(icon=icon))
        self.add_widget(MDNavigationItemLabel(text=text))
