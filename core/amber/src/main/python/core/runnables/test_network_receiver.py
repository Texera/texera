# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

import threading

import pytest
from pyarrow import Table

from core.models.internal_queue import (
    InternalQueue,
    DCMElement,
    DataElement,
    ECMElement,
)
from core.models.payload import DataFrame
from core.proxy import ProxyClient
from core.runnables.network_receiver import NetworkReceiver
from core.runnables.network_sender import NetworkSender
from core.util.proto import set_one_of
from proto.edu.uci.ics.amber.engine.architecture.rpc import (
    ControlInvocation,
    EmbeddedControlMessage,
    EmbeddedControlMessageType,
    EmptyRequest,
    AsyncRpcContext,
    ControlRequest,
)
from proto.edu.uci.ics.amber.engine.common import DirectControlMessagePayloadV2
from proto.edu.uci.ics.amber.core import (
    ActorVirtualIdentity,
    ChannelIdentity,
    EmbeddedControlMessageIdentity,
)


class TestNetworkReceiver:
    @pytest.fixture
    def input_queue(self):
        return InternalQueue()

    @pytest.fixture
    def output_queue(self):
        return InternalQueue()

    @pytest.fixture
    def network_receiver(self, output_queue):
        network_receiver = NetworkReceiver(output_queue, host="localhost", port=5555)
        yield network_receiver
        network_receiver.stop()

    class MockFlightMetadataReader:
        """
        MockFlightMetadataReader is a mocked FlightMetadataReader class to ultimately
        mock a credit value to be returned from Scala server to Python client
        """

        class MockBuffer:
            def to_pybytes(self):
                dummy_credit = 31
                return dummy_credit.to_bytes(8, "little")

        def read(self):
            return self.MockBuffer()

    @pytest.fixture
    def network_sender_thread(self, input_queue):
        network_sender = NetworkSender(input_queue, host="localhost", port=5555)

        # mocking do_put, read, to_pybytes to return fake credit values
        def mock_do_put(
            self,
            FlightDescriptor_descriptor,
            Schema_schema,
            FlightCallOptions_options=None,
        ):
            """
            Mocking FlightClient.do_put that is called in ProxyClient to return
            a MockFlightMetadataReader instead of a FlightMetadataReader

            :param self: an instance of FlightClient (would be ProxyClient in this case)
            :param FlightDescriptor_descriptor: descriptor
            :param Schema_schema: schema
            :param FlightCallOptions_options: options, None by default
            :return: writer : FlightStreamWriter, reader : MockFlightMetadataReader
            """
            writer, _ = super(ProxyClient, self).do_put(
                FlightDescriptor_descriptor, Schema_schema, FlightCallOptions_options
            )
            reader = TestNetworkReceiver.MockFlightMetadataReader()
            return writer, reader

        mock_proxy_client = network_sender._proxy_client
        mock_proxy_client.do_put = mock_do_put.__get__(
            mock_proxy_client, ProxyClient
        )  # override do_put with mock_do_put

        network_sender_thread = threading.Thread(target=network_sender.run)
        yield network_sender_thread
        network_sender.stop()

    @pytest.fixture
    def data_payload(self):
        return DataFrame(
            frame=Table.from_pydict(
                {
                    "Brand": ["Honda Civic", "Toyota Corolla", "Ford Focus", "Audi A4"],
                    "Price": [22000, 25000, 27000, 35000],
                }
            )
        )

    @pytest.mark.timeout(10)
    def test_network_receiver_can_receive_data_messages(
        self,
        data_payload,
        output_queue,
        input_queue,
        network_receiver,
        network_sender_thread,
    ):
        network_sender_thread.start()
        worker_id = ActorVirtualIdentity(name="test")
        channel_id = ChannelIdentity(worker_id, worker_id, False)
        input_queue.put(DataElement(tag=channel_id, payload=data_payload))
        element: DataElement = output_queue.get()
        assert len(element.payload.frame) == len(data_payload.frame)
        assert element.tag == channel_id

    @pytest.mark.timeout(10)
    def test_network_receiver_can_receive_control_messages(
        self,
        data_payload,
        output_queue,
        input_queue,
        network_receiver,
        network_sender_thread,
    ):
        worker_id = ActorVirtualIdentity(name="test")
        control_payload = set_one_of(DirectControlMessagePayloadV2, ControlInvocation())
        channel_id = ChannelIdentity(worker_id, worker_id, False)
        input_queue.put(DCMElement(tag=channel_id, payload=control_payload))
        network_sender_thread.start()
        element: DCMElement = output_queue.get()
        assert element.payload == control_payload
        assert element.tag == channel_id

    @pytest.mark.timeout(10)
    def test_network_receiver_can_receive_ecm(
        self,
        output_queue,
        input_queue,
        network_receiver,
        network_sender_thread,
    ):
        network_sender_thread.start()
        worker_id = ActorVirtualIdentity(name="test")
        channel_id = ChannelIdentity(worker_id, worker_id, False)
        ecm_id = EmbeddedControlMessageIdentity("test_ecm")
        scope = [channel_id]
        rpc_context = AsyncRpcContext(worker_id, worker_id)
        command_mapping = {
            str(worker_id): ControlInvocation(
                "NoOperation",
                ControlRequest(empty_request=EmptyRequest()),
                rpc_context,
                12,
            )
        }
        input_queue.put(
            ECMElement(
                tag=channel_id,
                payload=EmbeddedControlMessage(
                    ecm_id,
                    EmbeddedControlMessageType.ALL_ALIGNMENT,
                    scope,
                    command_mapping,
                ),
            )
        )
        element: DataElement = output_queue.get()
        assert isinstance(element.payload, EmbeddedControlMessage)
        assert element.payload.ecm_type == EmbeddedControlMessageType.ALL_ALIGNMENT
        assert element.payload.id == ecm_id
        assert element.payload.command_mapping == command_mapping
        assert element.payload.scope == scope
        assert element.tag == channel_id
