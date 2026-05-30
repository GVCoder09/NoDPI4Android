"""
Managers for services
"""

from abc import ABC, abstractmethod

from kivy.utils import platform


class ServiceManager(ABC):
    """Abstract service manager"""

    @abstractmethod
    def is_running(self) -> bool:
        pass

    @abstractmethod
    def start(self):
        pass

    @abstractmethod
    def stop(self):
        pass


class AndroidServiceManager(ServiceManager):
    """Android-specific service implementation"""

    def is_running(self) -> bool:
        if platform == "android":
            from lib.android.service import is_service_running

            return is_service_running("com.gvcoder.nodpi.ServiceProxy")
        return False

    def start(self):
        if platform == "android":
            from lib.android.service import start_service

            start_service("Proxy")

    def stop(self):
        if platform == "android":
            from lib.android.service import stop_service

            stop_service("Proxy")


class DesktopServiceManager(ServiceManager):
    """Desktop-specific service implementation"""

    def is_running(self) -> bool:
        return False

    def start(self):
        print("Service started (desktop mode)")

    def stop(self):
        print("Service stopped (desktop mode)")


class ServiceManagerFactory:
    """Service manager factory"""

    @staticmethod
    def create() -> ServiceManager:
        if platform == "android":
            return AndroidServiceManager()
        return DesktopServiceManager()
