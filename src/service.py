import asyncio
import json
import logging
import os
import random
import threading

from lib.android.storage import app_storage_path
from lib.android.notification import create_notify_channel, notify

from constants import (
    BLACKLIST_FILENAME,
    PROXY_CONFIG_FILENAME,
    APP_SETTINGS_FILENAME,
    DEFAULT_BLACKLIST,
    DEFAULT_PROXY_CONFIG,
    DEFAULT_APP_SETTINGS,
)


class FileBlacklistManager:
    """File-based blacklist with strict/loose domain matching"""

    def __init__(self, blacklist_path: str, matching: str = "strict"):
        self.matching = matching
        self.blocked = []
        self._load(blacklist_path)

    def _load(self, path: str) -> None:
        if not os.path.exists(path):
            logging.info("No blacklist file found, using default")
            self.blocked = [d.lower() for d in DEFAULT_BLACKLIST]
            return

        try:
            with open(path, "r", encoding="utf-8", errors="ignore") as f:
                for line in f:
                    line = line.strip()
                    if len(line) < 2 or line.startswith("#"):
                        continue
                    self.blocked.append(line.lower().replace("www.", ""))
            if not self.blocked:
                raise ValueError("Empty blacklist")
            logging.info(f"Read {len(self.blocked)} entries from blacklist")
        except Exception as e:
            logging.warning(f"Error reading blacklist: {e}, using default")
            self.blocked = [d.lower() for d in DEFAULT_BLACKLIST]

    def is_blocked(self, domain: str) -> bool:
        domain = domain.lower().replace("www.", "")

        if self.matching == "loose":
            for blocked_domain in self.blocked:
                if blocked_domain in domain:
                    return True

        if domain in self.blocked:
            return True

        parts = domain.split(".")
        for i in range(1, len(parts)):
            parent = ".".join(parts[i:])
            if parent in self.blocked:
                return True

        return False


class AutoBlacklistManager:
    """Automatically detects blocked domains via TLS handshake timeout"""

    def __init__(self, blacklist_path: str):
        self.blacklist_path = blacklist_path
        self.blocked = []
        self.whitelist = []

    def is_blocked(self, domain: str) -> bool:
        return domain.lower() in self.blocked

    async def check_domain(self, domain: str) -> None:
        domain = domain.lower()
        if domain in self.blocked or domain in self.whitelist:
            return

        try:
            import ssl
            from urllib.request import urlopen, Request
            from urllib.error import URLError

            loop = asyncio.get_event_loop()

            def _check():
                try:
                    req = Request(
                        f"https://{domain}",
                        headers={"User-Agent": "Mozilla/5.0"},
                    )
                    context = ssl._create_unverified_context()
                    with urlopen(req, timeout=4, context=context):
                        return False
                except URLError as e:
                    reason = str(e.reason)
                    if "handshake operation timed out" in reason:
                        return True
                    return False
                except Exception:
                    return False

            blocked = await loop.run_in_executor(None, _check)
            if blocked:
                self.blocked.append(domain)
                try:
                    with open(self.blacklist_path, "a", encoding="utf-8") as f:
                        f.write(domain + "\n")
                except Exception as e:
                    logging.warning(f"Could not append to blacklist: {e}")
            else:
                self.whitelist.append(domain)
        except Exception as e:
            logging.warning(f"Auto-check error for {domain}: {e}")


class NoBlacklistManager:
    """Fragments all domains without exception"""

    def is_blocked(self, domain: str) -> bool:
        return True

    async def check_domain(self, domain: str) -> None:
        pass


def _extract_sni_position(data: bytes):
    """Find SNI field bounds in TLS ClientHello payload"""

    i = 0
    while i < len(data) - 8:
        if all(data[i + j] == 0x00 for j in [0, 1, 2, 4, 6, 7]):
            ext_len = data[i + 3]
            server_name_list_len = data[i + 5]
            server_name_len = data[i + 8]
            if (
                ext_len - server_name_list_len == 2
                and server_name_list_len - server_name_len == 3
            ):
                sni_start = i + 9
                sni_end = sni_start + server_name_len
                return sni_start, sni_end
        i += 1

    return None


def _fragment_sni(data: bytes) -> bytes:
    """Fragment TLS record splitting around SNI field"""

    sni_pos = _extract_sni_position(data)
    if not sni_pos:
        return _fragment_random(data)

    parts = []
    part_start = data[: sni_pos[0]]
    sni_data = data[sni_pos[0]: sni_pos[1]]
    part_end = data[sni_pos[1]:]

    parts.append(
        bytes.fromhex("160304") +
        len(part_start).to_bytes(2, "big") + part_start
    )
    for i in range(0, len(sni_data), 2):
        chunk = sni_data[i: i + 2]
        parts.append(bytes.fromhex("160304") +
                     len(chunk).to_bytes(2, "big") + chunk)
    parts.append(bytes.fromhex("160304") +
                 len(part_end).to_bytes(2, "big") + part_end)

    return b"".join(parts)


def _fragment_random(data: bytes) -> bytes:
    """Fragment TLS record with random chunk sizes"""

    parts = []

    host_end = data.find(b"\x00")
    if host_end != -1:
        parts.append(
            bytes.fromhex("160304")
            + (host_end + 1).to_bytes(2, "big")
            + data[: host_end + 1]
        )
        data = data[host_end + 1:]

    while data:
        chunk_len = random.randint(1, len(data))
        parts.append(
            bytes.fromhex("160304") + chunk_len.to_bytes(2,
                                                         "big") + data[:chunk_len]
        )
        data = data[chunk_len:]

    return b"".join(parts)


TASKS = []


async def pipe(reader, writer):
    try:
        while not reader.at_eof() and not writer.is_closing():
            data = await reader.read(1500)
            writer.write(data)
            await writer.drain()
    except Exception:
        pass
    finally:
        writer.close()


async def new_conn(reader, writer, blacklist_manager, fragment_method):

    try:
        http_data = await reader.read(1500)
        if not http_data:
            writer.close()
            return

        headers = http_data.split(b"\r\n")
        first_line = headers[0].split(b" ")
        method = first_line[0]
        url = first_line[1]

        if method == b"CONNECT":
            host_port = url.split(b":")
            host = host_port[0]
            port = int(host_port[1]) if len(host_port) > 1 else 443
        else:
            host_header = next(
                (h for h in headers if h.startswith(b"Host: ")), None)
            if not host_header:
                raise ValueError("Missing Host header")
            host_port = host_header[6:].split(b":")
            host = host_port[0]
            port = int(host_port[1]) if len(host_port) > 1 else 80

        if method == b"CONNECT":
            writer.write(b"HTTP/1.1 200 Connection Established\r\n\r\n")
            await writer.drain()

            remote_reader, remote_writer = await asyncio.open_connection(
                host.decode(), port
            )

            if isinstance(blacklist_manager, AutoBlacklistManager):
                await blacklist_manager.check_domain(host.decode())

            await _handle_tls(
                reader, remote_writer, host.decode(), blacklist_manager, fragment_method
            )
        else:
            remote_reader, remote_writer = await asyncio.open_connection(
                host.decode(), port
            )
            remote_writer.write(http_data)
            await remote_writer.drain()

        TASKS.extend(
            [
                asyncio.create_task(pipe(reader, remote_writer)),
                asyncio.create_task(pipe(remote_reader, writer)),
            ]
        )
    except Exception as e:
        try:
            writer.write(b"HTTP/1.1 500 Internal Server Error\r\n\r\n")
            await writer.drain()
        except Exception:
            pass
        writer.close()


async def _handle_tls(reader, writer, domain, blacklist_manager, fragment_method):
    """Read initial TLS record and apply fragmentation if domain is blocked"""

    try:
        head = await reader.read(5)
        data = await reader.read(2048)
    except Exception as e:
        logging.warning(f"TLS read error for {domain}: {e}")
        return

    if not blacklist_manager.is_blocked(domain):
        writer.write(head + data)
        await writer.drain()
        return

    if fragment_method == "sni":
        fragmented = _fragment_sni(data)
    else:
        fragmented = _fragment_random(data)

    writer.write(fragmented)
    await writer.drain()


def _load_proxy_config() -> dict:
    path = os.path.join(app_storage_path(), PROXY_CONFIG_FILENAME)
    if os.path.exists(path):
        try:
            with open(path, "r", encoding="utf-8") as f:
                cfg = json.load(f)
                return {**DEFAULT_PROXY_CONFIG, **cfg}
        except Exception as e:
            logging.warning(f"Error reading proxy config: {e}")
    return DEFAULT_PROXY_CONFIG.copy()


def _load_app_settings() -> dict:
    path = os.path.join(app_storage_path(), APP_SETTINGS_FILENAME)
    if os.path.exists(path):
        try:
            with open(path, "r", encoding="utf-8") as f:
                cfg = json.load(f)
                return {**DEFAULT_APP_SETTINGS, **cfg}
        except Exception as e:
            logging.warning(f"Error reading app settings: {e}")
    return DEFAULT_APP_SETTINGS.copy()


def _build_blacklist_manager(settings: dict):
    blacklist_mode = settings.get("blacklist", "standard").lower()
    matching = settings.get("matching", "strict").lower()
    blacklist_path = os.path.join(app_storage_path(), BLACKLIST_FILENAME)

    if blacklist_mode == "all":
        logging.info("Blacklist mode: ALL (fragment everything)")
        return NoBlacklistManager()
    elif blacklist_mode == "auto":
        logging.info("Blacklist mode: AUTO")
        return AutoBlacklistManager(blacklist_path)

    logging.info(f"Blacklist mode: STANDARD, matching: {matching}")
    return FileBlacklistManager(blacklist_path, matching)


class ProxyServer:
    def __init__(self):
        proxy_cfg = _load_proxy_config()
        self.host = proxy_cfg.get("host", "127.0.0.1")
        self.port = str(proxy_cfg.get("port", "8881"))

        app_settings = _load_app_settings()
        self.fragment_method = app_settings.get(
            "fragment_method", "sni").lower()
        self.blacklist_manager = _build_blacklist_manager(app_settings)

        self.server = None
        self.loop = None
        self.running = False

    def start(self):
        if self.running:
            return

        notify(
            0,
            "0",
            "NoDPI",
            f"Proxy server is starting on {self.host}:{self.port} Enjoy watching!",
        )

        self.running = True
        self.thread = threading.Thread(target=self._run_server, daemon=True)
        self.thread.start()

    def _run_server(self):
        try:
            self.loop = asyncio.new_event_loop()
            asyncio.set_event_loop(self.loop)

            blacklist_manager = self.blacklist_manager
            fragment_method = self.fragment_method

            async def server_main():
                self.server = await asyncio.start_server(
                    lambda r, w: new_conn(
                        r, w, blacklist_manager, fragment_method),
                    self.host,
                    self.port,
                )
                async with self.server:
                    await self.server.serve_forever()

            self.loop.run_until_complete(server_main())
        except Exception as e:
            logging.error(f"Server error: {e}")
        finally:
            if self.loop and self.loop.is_running():
                self.loop.stop()
            if self.loop:
                self.loop.close()
            self.running = False

    def stop(self):
        if not self.running:
            return

        self.running = False

        if self.server:
            self.server.close()
            if self.loop and self.loop.is_running():
                self.loop.call_soon_threadsafe(self.loop.stop)


if __name__ == "__main__":

    from kivy import platform

    if platform != "android":
        raise Exception("This module only for Android devices!")

    logging.info("Starting Proxy Service")

    try:
        logging.info("Creating notification channel")
        create_notify_channel("0", "Proxy Service", "")
        logging.info("Notification channel created")
    except Exception as e:
        logging.error(f"Error creating notification channel: {e}")

    try:
        logging.info("Initializing proxy server")
        proxy = ProxyServer()
        logging.info(
            f"Fragment method: {proxy.fragment_method}, "
            f"Blacklist: {type(proxy.blacklist_manager).__name__}"
        )
        logging.info("Starting proxy server")
        proxy.start()
        logging.info("Proxy server started")

        logging.info("Entering main loop")
        while proxy.running:
            threading.Event().wait(1)

    except Exception as e:
        logging.error(f"Main error: {e}")
    finally:
        logging.info("Shutting down")
        if "proxy" in locals():
            proxy.stop()
        logging.info("Service stopped")
