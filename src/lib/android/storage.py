"""
Android storage
"""

from android.storage import app_storage_path


def get_app_path() -> str:
    """Get app path"""

    return str(app_storage_path())
