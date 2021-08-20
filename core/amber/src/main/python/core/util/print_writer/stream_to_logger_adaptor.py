from loguru import logger


class StreamToLoggerAdaptor:
    """
    This class is used to redirect `print` to loguru's logger, instead of stdout.
    """

    def __init__(self, level=logger.level("PRINT", no=38)):
        self._level = level

    def write(self, buffer):
        for line in buffer.rstrip().splitlines():
            logger.opt(depth=1).log("PRINT", line.rstrip())

    def flush(self):
        pass
