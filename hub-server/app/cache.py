from __future__ import annotations

import threading
import time
from typing import Callable, Generic, TypeVar

T = TypeVar("T")


class TtlCache(Generic[T]):
    """Caches the result of a zero-argument loader for `ttl_seconds`."""

    def __init__(self, loader: Callable[[], T], ttl_seconds: float, clock: Callable[[], float] = time.monotonic):
        self._loader = loader
        self._ttl = ttl_seconds
        self._clock = clock
        self._lock = threading.Lock()
        self._value: T | None = None
        self._expires_at = 0.0

    def get(self) -> T:
        with self._lock:
            if self._value is not None and self._clock() < self._expires_at:
                return self._value
            self._value = self._loader()
            self._expires_at = self._clock() + self._ttl
            return self._value

    def invalidate(self) -> None:
        with self._lock:
            self._value = None
            self._expires_at = 0.0
