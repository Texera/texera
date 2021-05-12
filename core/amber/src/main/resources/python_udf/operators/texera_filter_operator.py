import logging
from typing import Callable

import pandas

from operators.texera_udf_operator_base import TexeraUDFOperator, log_exception

logger = logging.getLogger(__name__)


class TexeraFilterOperator(TexeraUDFOperator):
    """
    Base class for filter operators. Either inherit this class (in case you want to
    override open() and close(), e.g., open and close a model file.) or init this class object with a filter function.
    The filter function should return a boolean value indicating whether the input tuple meets the filter criteria.
    If use inherit, then script should have an attribute named `operator_instance` that is an instance of the
    inherited class; If only use filter function, simply define a `filter_function` in the script.
    """

    @log_exception
    def __init__(self, filter_function: Callable):
        super().__init__()
        if filter_function is None:
            raise NotImplementedError
        self._filter_function: Callable = filter_function

    @log_exception
    def accept(self, row: pandas.Series, nth_child: int = 0) -> None:
        if self._filter_function(row, *self._args):
            self._result_tuples.append(row)
