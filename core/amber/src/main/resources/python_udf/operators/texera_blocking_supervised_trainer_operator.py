import logging
import pickle

import pandas
from sklearn.model_selection import train_test_split

from operators.texera_udf_operator_base import TexeraUDFOperator, log_exception

logger = logging.getLogger(__name__)


class TexeraBlockingSupervisedTrainerOperator(TexeraUDFOperator):

    @log_exception
    def __init__(self):
        super().__init__()
        self._x = []
        self._y = []
        self._test_ratio = None
        self._train_args = dict()
        self._model_file_path = None

    @log_exception
    def input_exhausted(self, *args, **kwargs):
        x_train, x_test, y_train, y_test = train_test_split(self._x, self._y, test_size=self._test_ratio, random_state=1)
        model = self.train(x_train, y_train, **self._train_args)
        with open(self._model_file_path, "wb") as file:
            pickle.dump(model, file)

        if x_test:
            y_pred = self.test(model, x_test, y_test)
            self.report(y_test, y_pred)

    @log_exception
    def accept(self, row: pandas.Series, nth_child: int = 0) -> None:
        self._x.append(row[0])
        self._y.append(row[1])

    @staticmethod
    @log_exception
    def train(x_train, y_train, *args, **kwargs):
        raise NotImplementedError

    @staticmethod
    @log_exception
    def test(model, x_test, y_test, *args, **kwargs):
        pass

    @log_exception
    def report(self, y_test, y_pred, *args, **kwargs):
        from sklearn.metrics import classification_report
        matrix = pandas.DataFrame(classification_report(y_test, y_pred, output_dict=True)).transpose()
        matrix['class'] = [label for label, row in matrix.iterrows()]
        cols = matrix.columns.to_list()
        cols = [cols[-1]] + cols[:-1]
        matrix = matrix[cols].round(3)
        for index, row in matrix.iterrows():
            if index != 1:
                self._result_tuples.append(row)
