"""
Android service
"""

import logging

from android import mActivity  # pylint: disable=no-name-in-module
from jnius import autoclass, cast  # pylint: disable=no-name-in-module


def is_service_running(name_service: str) -> bool:
    """Check if service is running"""

    PythonActivity = autoclass("org.kivy.android.PythonActivity")
    context = PythonActivity.mActivity

    activity_manager = cast(
        "android.app.ActivityManager",
        context.getSystemService(context.ACTIVITY_SERVICE),
    )

    running_services = activity_manager.getRunningServices(1000)

    for service in running_services:
        logging.info(service.service.getClassName())
        if name_service in service.service.getClassName():
            return True
    return False


def start_service(name_service: str) -> None:
    """Start service"""

    context = mActivity.getApplicationContext()
    service = autoclass(str(context.getPackageName()) +
                        ".Service" + name_service)
    service.start(mActivity, "")


def stop_service(name_service: str) -> None:
    """Stop service"""

    context = mActivity.getApplicationContext()
    service = autoclass(str(context.getPackageName()) +
                        ".Service" + name_service)
    service.stop(mActivity)
