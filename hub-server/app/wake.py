from __future__ import annotations

import logging
import subprocess
import sys
from datetime import datetime
from typing import Protocol

from .config import REPO_ROOT

log = logging.getLogger(__name__)

SCHEDULE_SCRIPT = REPO_ROOT / "scripts" / "windows" / "schedule_wake.ps1"


class WakeScheduler(Protocol):
    def schedule(self, at: datetime) -> bool: ...

    def cancel(self) -> bool: ...


class NoopWakeScheduler:
    """Used when wake integration is disabled or the host is not Windows."""

    def schedule(self, at: datetime) -> bool:
        log.info("Wake scheduling disabled, would wake at %s", at.isoformat())
        return False

    def cancel(self) -> bool:
        return False


class WindowsWakeScheduler:
    """Registers a Task Scheduler task with WakeToRun via scripts/windows/schedule_wake.ps1."""

    def _run(self, *args: str) -> bool:
        command = [
            "powershell.exe",
            "-NoProfile",
            "-NonInteractive",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            str(SCHEDULE_SCRIPT),
            *args,
        ]
        result = subprocess.run(command, capture_output=True, text=True, timeout=60)
        if result.returncode != 0:
            log.error("schedule_wake.ps1 failed (%s): %s", result.returncode, result.stderr.strip())
            return False
        log.info("schedule_wake.ps1: %s", result.stdout.strip())
        return True

    def schedule(self, at: datetime) -> bool:
        local = at.astimezone().replace(tzinfo=None)
        return self._run("-At", local.isoformat(timespec="seconds"))

    def cancel(self) -> bool:
        return self._run("-Cancel")


def create_wake_scheduler(enabled: bool) -> WakeScheduler:
    if enabled and sys.platform == "win32":
        return WindowsWakeScheduler()
    return NoopWakeScheduler()
