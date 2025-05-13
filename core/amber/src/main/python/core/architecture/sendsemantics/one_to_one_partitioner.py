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

import typing
from typing import Iterator

from overrides import overrides
from core.architecture.sendsemantics.partitioner import Partitioner
from core.models import Tuple
from core.models.marker import Marker
from core.util import set_one_of
from proto.edu.uci.ics.amber.engine.architecture.sendsemantics import (
    OneToOnePartitioning,
    Partitioning,
)
from proto.edu.uci.ics.amber.core import ActorVirtualIdentity


class OneToOnePartitioner(Partitioner):
    def __init__(self, partitioning: OneToOnePartitioning, worker_id: str):
        super().__init__(set_one_of(Partitioning, partitioning))
        self.batch_size = partitioning.batch_size
        self.batch: list[Tuple] = list()
        for channel in partitioning.channels:
            if channel.from_worker_id.name == worker_id:
                self.receiver = channel.to_worker_id
                break  # one to one will have only one receiver.

    @overrides
    def add_tuple_to_batch(
        self, tuple_: Tuple
    ) -> Iterator[typing.Tuple[ActorVirtualIdentity, typing.List[Tuple]]]:
        self.batch.append(tuple_)
        if len(self.batch) == self.batch_size:
            yield self.receiver, self.batch
            self.reset()

    @overrides
    def flush(
        self, marker: Marker
    ) -> Iterator[
        typing.Tuple[ActorVirtualIdentity, typing.Union[Marker, typing.List[Tuple]]]
    ]:
        if len(self.batch) > 0:
            yield self.receiver, self.batch
        self.reset()
        yield self.receiver, marker

    @overrides
    def reset(self) -> None:
        self.batch = list()
