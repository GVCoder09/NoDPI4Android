"""
Android notifications
"""

from jnius import autoclass, cast  # pylint: disable=no-name-in-module

String = autoclass("java.lang.String")

PythonActivity = autoclass("org.kivy.android.PythonService")
Activity = PythonActivity.mService
AppContext = Activity.getApplication().getApplicationContext()


def create_notify_channel(
    channel_id: str, channel_name: str, channel_description: str
) -> None:
    """Creates notification channel"""

    NotificationManager = autoclass("android.app.NotificationManager")
    NotificationChannel = autoclass("android.app.NotificationChannel")

    channel = NotificationChannel(
        String(channel_id),
        cast("java.lang.CharSequence", String(channel_name)),
        NotificationManager.IMPORTANCE_HIGH,
    )
    channel.setDescription(String(channel_description))
    channel.enableLights(True)
    channel.enableVibration(True)

    notification_manager = AppContext.getSystemService(NotificationManager)
    notification_manager.createNotificationChannel(channel)


def notify(id: int, channel_id: str, title: str, text: str, ticker: str = "") -> None:
    """Send notification"""

    Builder = autoclass("android.app.Notification$Builder")
    Notification = autoclass("android.app.Notification")

    mBuilder = Builder(Activity, channel_id)
    mNManager = Activity.getSystemService(PythonActivity.NOTIFICATION_SERVICE)

    mBuilder.setContentTitle(String(title.encode("utf-8")))
    mBuilder.setContentText(String(text.encode("utf-8")))
    mBuilder.setTicker(String(ticker.encode("utf-8")))
    mBuilder.setSmallIcon(AppContext.getApplicationInfo().icon)
    mBuilder.setAutoCancel(True)
    mBuilder.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_ALL)
    notify1 = mBuilder.build()
    mNManager.notify(id, notify1)
