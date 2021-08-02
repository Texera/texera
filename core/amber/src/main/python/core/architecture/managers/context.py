from proto.edu.uci.ics.amber.engine.architecture.worker import WorkerState
from .pause_manager import PauseManager
from .state_manager import StateManager
from .statistics_manager import StatisticsManager
from ..packaging.batch_to_tuple_converter import BatchToTupleConverter
from ..packaging.tuple_to_batch_converter import TupleToBatchConverter


class Context:
    """
    This class manages context of command handlers. Many of those attributes belongs to DP thread,
    they are managed here to show a clean interface what handlers can or should access.
    Context class can be viewed as a friend of DataProcessor.
    """
    def __init__(self, dp):
        self.dp = dp
        self.input_queue = dp._input_queue
        self.udf_operator = dp._udf_operator
        self.state_manager = StateManager({
            WorkerState.UNINITIALIZED: {WorkerState.READY},
            WorkerState.READY:         {WorkerState.PAUSED, WorkerState.RUNNING},
            WorkerState.RUNNING:       {WorkerState.PAUSED, WorkerState.COMPLETED},
            WorkerState.PAUSED:        {WorkerState.RUNNING},
            WorkerState.COMPLETED:     set(),

        }, WorkerState.UNINITIALIZED)

        self.statistics_manager = StatisticsManager()
        self.pause_manager = PauseManager()
        self.tuple_to_batch_converter = TupleToBatchConverter()
        self.batch_to_tuple_converter = BatchToTupleConverter()
