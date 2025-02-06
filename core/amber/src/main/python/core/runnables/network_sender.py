from typing import Optional

from loguru import logger
from overrides import overrides
import pyarrow as pa
from core.models import DataPayload, InternalQueue, DataFrame, MarkerFrame, State

from core.models.internal_queue import (
    InternalQueueElement,
    DataElement,
    ControlElement,
    ChannelMarkerElement,
)
from core.proxy import ProxyClient
from core.util import StoppableQueueBlockingRunnable
from proto.edu.uci.ics.amber.engine.architecture.rpc import ChannelMarkerPayload
from proto.edu.uci.ics.amber.engine.common import (
    ControlPayloadV2,
    PythonControlMessage,
    PythonDataHeader,
)
from proto.edu.uci.ics.amber.core import ChannelIdentity


class NetworkSender(StoppableQueueBlockingRunnable):
    """
    Serialize and send messages.
    """

    def __init__(
        self,
        shared_queue: InternalQueue,
        host: str,
        port: int,
        handshake_port: Optional[int] = None,
    ):
        super().__init__(self.__class__.__name__, queue=shared_queue)
        self._proxy_client = ProxyClient(
            host=host, port=port, handshake_port=handshake_port
        )

    @overrides(check_signature=False)
    def receive(self, next_entry: InternalQueueElement):
        if isinstance(next_entry, DataElement):
            self._send_data(next_entry.tag, next_entry.payload)
        elif isinstance(next_entry, ControlElement):
            self._send_control(next_entry.tag, next_entry.payload)
        elif isinstance(next_entry, ChannelMarkerElement):
            self._send_channel_marker(next_entry.tag, next_entry.payload)
        else:
            raise TypeError(f"Unexpected entry {next_entry}")

    @logger.catch(reraise=True)
    def _send_channel_marker(
        self, to: ChannelIdentity, data_payload: ChannelMarkerPayload
    ) -> None:
        """
        Sends a channel marker payload to the specified channel.

        Args:
            to (ChannelIdentity): The target channel to which the marker should be sent.
            data_payload (ChannelMarkerPayload): The channel marker payload to send.

        This function constructs a `PythonDataHeader` with the appropriate metadata,
        serializes the payload into an Arrow table, and sends it using the proxy client.
        """
        data_header = PythonDataHeader(tag=to, payload_type="ChannelMarker")
        schema = pa.schema([("payload", pa.binary())])
        data = [pa.array([bytes(data_payload)])]
        table = pa.Table.from_arrays(data, schema=schema)
        self._proxy_client.send_data(bytes(data_header), table)

    @logger.catch(reraise=True)
    def _send_data(self, to: ChannelIdentity, data_payload: DataPayload) -> None:
        """
        Send data payload to the given target actor. This method is to be used
        internally only.

        :param to: The target ChannelIdentity
        :param data_payload: The data payload to be sent, can be either DataFrame or
            EndOfInputChannel
        """

        if isinstance(data_payload, DataFrame):
            data_header = PythonDataHeader(tag=to, payload_type="Data")
            self._proxy_client.send_data(bytes(data_header), data_payload.frame)
        elif isinstance(data_payload, MarkerFrame):
            data_header = PythonDataHeader(
                tag=to, payload_type=data_payload.frame.__class__.__name__
            )
            table = (
                data_payload.frame.to_table()
                if isinstance(data_payload.frame, State)
                else None
            )
            self._proxy_client.send_data(bytes(data_header), table)
        else:
            raise TypeError(f"Unexpected payload {data_payload}")

    @logger.catch(reraise=True)
    def _send_control(
        self, to: ChannelIdentity, control_payload: ControlPayloadV2
    ) -> None:
        """
        Send the control payload to the given target actor. This method is to be used
        internally only.

        :param to: The target ChannelIdentity
        :param control_payload: The control payload to be sent, can be either
            ControlInvocation or ReturnInvocation.
        """
        python_control_message = PythonControlMessage(tag=to, payload=control_payload)
        int.from_bytes(
            self._proxy_client.call_action("control", bytes(python_control_message)),
            byteorder="little",
        )  # returned credits
