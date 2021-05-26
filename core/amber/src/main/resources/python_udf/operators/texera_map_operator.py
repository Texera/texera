import logging
from typing import Callable

import pandas

from operators.texera_udf_operator_base import TexeraUDFOperator, log_exception

logger = logging.getLogger(__name__)


class TexeraMapOperator(TexeraUDFOperator):
    """
    Base class for one-input-tuple to one-output-tuple mapping operator. Either inherit this class (in case you want to
    override open() and close(), e.g., open and close a model file.) or init this class object with a map function.
    The map function should return the result tuple. If use inherit, then script should have an attribute named
    `operator_instance` that is an instance of the inherited class; If only use filter function, simply define a
    `map_function` in the script.
    """

    @log_exception
    def __init__(self, map_function: Callable):
        super().__init__()
        if map_function is None:
            raise NotImplementedError
        self._map_function: Callable = map_function

    @log_exception
    def accept(self, row: pandas.Series, nth_child: int = 0) -> None:
        self._result_tuples.append(self._map_function(row, *self._args))  # must take args
