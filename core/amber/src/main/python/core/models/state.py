from pyarrow import Table
from pandas import DataFrame


class State:
    def __init__(self):
        self.data = {}

    def add(self, key, value):
        self.data[key] = value

    def get(self, key):
        return self.data[key]

    def to_table(self):
        return Table.from_pandas(df=DataFrame([self.data]))

    def from_dict(self, dictionary):
        for key, value in dictionary.items():
            self.add(key, value)
        return self

    def __setitem__(self, key, value):
        self.data[key] = value

    def __getitem__(self, key):
        return self.data[key]

    def __str__(self) -> str:
        content = ", ".join(
            [repr(key) + ": " + repr(value) for key, value in self.data.items()]
        )
        return f"State[{content}]"

    __repr__ = __str__
