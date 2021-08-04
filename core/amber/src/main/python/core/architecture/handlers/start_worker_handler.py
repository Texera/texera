from proto.edu.uci.ics.amber.engine.architecture.worker import StartWorkerV2, WorkerState
from .handler_base import Handler
from ..managers.context import Context
from ...models.marker import EndMarker, EndOfAllMarker


class StartWorkerHandler(Handler):
    cmd = StartWorkerV2

    def __call__(self, context: Context, command: StartWorkerV2, *args, **kwargs):
        if context.dp._udf_operator.is_source:
            context.state_manager.transit_to(WorkerState.RUNNING)
            context.input_queue.put(EndMarker())
            context.input_queue.put(EndOfAllMarker())
        state = context.state_manager.get_current_state()
        return state
