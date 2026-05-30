"""
Config managers
"""

import json
import os
from abc import ABC, abstractmethod
from typing import Any, Dict, List

from constants import DEFAULT_APP_SETTINGS, DEFAULT_BLACKLIST, DEFAULT_PROXY_CONFIG


class BaseConfigManager(ABC):
    """Abstract base class for configuration managers"""

    def __init__(self, config_path: str):
        self.config_path = config_path
        self._ensure_directory_exists()

    def _ensure_directory_exists(self):
        """Ensure config directory exists"""

        os.makedirs(os.path.dirname(self.config_path), exist_ok=True)

    @abstractmethod
    def load(self) -> Dict[str, Any]:
        """Load config"""

    @abstractmethod
    def save(self, data: Dict[str, Any]):
        """Save config"""


class AppSettingsManager(BaseConfigManager):
    """Manages application settings"""

    def load(self) -> Dict[str, Any]:

        if os.path.exists(self.config_path):
            try:
                with open(self.config_path, "r", encoding="utf-8") as f:
                    loaded = json.load(f)
                    return {**DEFAULT_APP_SETTINGS, **loaded}
            except Exception as e:
                print(f"Error loading settings: {e}")

        return DEFAULT_APP_SETTINGS.copy()

    def save(self, data: Dict[str, Any]):

        data = {**self.load(), **data}

        try:
            with open(self.config_path, "w", encoding="utf-8") as f:
                json.dump(data, f, indent=2)
        except Exception as e:
            print(f"Error saving settings: {e}")


class ProxyConfigManager(BaseConfigManager):
    """Manages proxy settings"""

    def load(self) -> Dict[str, Any]:

        if os.path.exists(self.config_path):
            try:
                with open(self.config_path, "r", encoding="utf-8") as f:
                    loaded = json.load(f)
                    return {**DEFAULT_PROXY_CONFIG, **loaded}
            except Exception as e:
                print(f"Error loading proxy config: {e}")

        return DEFAULT_PROXY_CONFIG.copy()

    def save(self, data: Dict[str, Any]):

        try:
            with open(self.config_path, "w", encoding="utf-8") as f:
                json.dump(data, f, indent=2)
        except Exception as e:
            print(f"Error saving proxy config: {e}")


class BlacklistManager(BaseConfigManager):
    """Manages blacklist"""

    def load(self) -> List[str]:

        if os.path.exists(self.config_path):
            try:
                with open(self.config_path, "r", encoding="utf-8") as f:
                    content = f.read().strip()
                    return [
                        line.strip() for line in content.splitlines() if line.strip()
                    ]
            except Exception as e:
                print(f"Error loading blacklist: {e}")
                return DEFAULT_BLACKLIST.copy()

        self.save(DEFAULT_BLACKLIST)
        return DEFAULT_BLACKLIST.copy()

    def save(self, data: List[str]):

        try:
            with open(self.config_path, "w", encoding="utf-8") as f:
                f.write("\n".join(data))
        except Exception as e:
            print(f"Error saving blacklist: {e}")
