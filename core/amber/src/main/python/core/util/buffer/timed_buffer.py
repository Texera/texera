from datetime import datetime
from typing import Tuple, List, Iterator

from core.util.buffer.buffer_base import IBuffer


class TimedBuffer(IBuffer):
    def __init__(self, max_message_num=10, max_flush_interval_in_ms=500):
        self._max_message_num = max_message_num
        self._max_flush_interval_in_ms = max_flush_interval_in_ms
        self._buffer: List[Tuple[datetime, str]]() = list()
        self._last_output_time = datetime.now()

    def put(self, message: str) -> None:
        self._buffer.append((datetime.now(), message))

    def get(self, flush: bool = False) -> Iterator[Tuple[datetime, str]]:
        if (
            flush
            or len(self._buffer) >= self._max_message_num
            or (datetime.now() - self._last_output_time).seconds
            >= self._max_flush_interval_in_ms / 1000
        ):
            self._last_output_time = datetime.now()
            yield from self._buffer
            self._buffer.clear()
