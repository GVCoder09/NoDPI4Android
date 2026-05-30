"""
Android module
"""

from kivy import platform

if platform != 'android':
    raise ImportError('This module can only be imported on Android devices!')
