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

from typing import Dict, Set, Tuple, Union

from typing_extensions import T


class InvalidStateException(Exception):
    pass


class InvalidTransitionException(Exception):
    pass


class StateManager:
    """
    A generalized StateManager that provides APIs for state transition, assertion,
    and confirmation.
    """

    def __init__(self, state_transition_graph: Dict[T, Set[T]], initial_state: T):
        self._state_transition_graph = state_transition_graph
        self._current_state: T = initial_state

    def assert_state(self, state: T) -> None:
        """
        Assert the current state to be the expected state, raise exception if otherwise.
        :param state: the expected state.
        """
        if self._current_state != state:
            raise InvalidStateException(
                f"Excepted state = {state} but current state = {self._current_state}"
            )

    def confirm_state(self, *states: Union[T, Tuple[T]]) -> bool:
        """
        Check if current state is in one of the states.

        :param states: Union[T, Tuple[T]], a series of states to be checked.
        :return: bool
        """
        return any(self._current_state == state for state in states)

    def transit_to(self, state: T) -> None:
        """
        Transit the current state into the target state.

        :param state: T, the target state to transit to.
        :return:
        """

        # do nothing if the current state is already the target state
        if state == self._current_state:
            return

        if state not in self._state_transition_graph.get(self._current_state, set()):
            raise InvalidTransitionException(
                f"Cannot transit from {self._current_state} to {state}"
            )

        self._current_state = state

    def get_current_state(self) -> T:
        """
        Return the current state.
        :return:
        """
        return self._current_state
