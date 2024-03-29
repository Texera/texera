from overrides import overrides
from threading import Thread, Event

from core.models.internal_queue import InternalQueue
from core.runnables import MainLoop, NetworkReceiver, NetworkSender, Heartbeat
from core.util.runnable.runnable import Runnable
from core.util.stoppable.stoppable import Stoppable


class PythonWorker(Runnable, Stoppable):
    def __init__(self, worker_id: str, host: str, output_port: int):
        self._input_queue = InternalQueue()
        self._output_queue = InternalQueue()
        # start the server
        self._network_receiver = NetworkReceiver(self._input_queue, host=host)
        # let Java knows where Python starts (do handshake)
        self._network_sender = NetworkSender(
            self._output_queue,
            host=host,
            port=output_port,
            handshake_port=self._network_receiver.proxy_server.get_port_number(),
        )
        self._stop_event = Event()
        self._heartbeat = Heartbeat(host, output_port, 5, self._stop_event)

        self._main_loop = MainLoop(worker_id, self._input_queue, self._output_queue)
        self._network_receiver.register_shutdown(self.stop)

    @overrides
    def run(self) -> None:
        network_sender_thread = Thread(
            target=self._network_sender.run, name="network_sender"
        )
        main_loop_thread = Thread(target=self._main_loop.run, name="main_loop_thread")

        heartbeat_thread = Thread(
            target=self._heartbeat.run,
            name="heartbeat_thread",
        )

        network_sender_thread.start()
        main_loop_thread.start()
        heartbeat_thread.start()
        main_loop_thread.join()
        network_sender_thread.join()

        # if everything finishes, the heartbeat should stop
        self._stop_event.set()

        heartbeat_thread.join()

    @overrides
    def stop(self):
        self._main_loop.stop()
        self._network_sender.stop()
        self._heartbeat.stop()
