"""Tells the phones on the same network where this PC is, without a written address."""

from __future__ import annotations

import logging
import socket

log = logging.getLogger(__name__)

SERVICE_TYPE = "_nexus._tcp.local."


def lan_ip() -> str:
    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        probe.connect(("192.0.2.1", 1))
        return probe.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        probe.close()


class Announcer:
    """Best-effort mDNS advert. A failure here must not stop the hub."""

    def __init__(self, port: int):
        self._zeroconf = None
        self._info = None
        try:
            from zeroconf import IPVersion, ServiceInfo, Zeroconf

            ip = lan_ip()
            self._info = ServiceInfo(
                SERVICE_TYPE,
                f"NEXUS.{SERVICE_TYPE}",
                addresses=[socket.inet_aton(ip)],
                port=port,
                properties={"path": "/"},
                server="NEXUS.local.",
            )
            self._zeroconf = Zeroconf(ip_version=IPVersion.V4Only)
            self._zeroconf.register_service(self._info)
            log.info("Announcing NEXUS on %s:%s", ip, port)
        except Exception:
            log.warning("Could not announce NEXUS on the local network", exc_info=True)
            self.close()

    def close(self) -> None:
        if self._zeroconf is None:
            return
        try:
            if self._info is not None:
                self._zeroconf.unregister_service(self._info)
            self._zeroconf.close()
        except Exception:
            log.warning("Could not stop the network announcement", exc_info=True)
        self._zeroconf = None
