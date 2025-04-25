"""
Workflow Interpreter module for generating descriptions of workflows.
"""

from typing import Dict, List, Any, Optional, Tuple
import json
import traceback
from enum import Enum
from collections import defaultdict

from model.texera.TexeraWorkflow import TexeraWorkflow


class InterpretationMethod(Enum):
    """Enum for different workflow interpretation methods."""

    RAW = "raw"
    BY_PATH = "by_path"


class WorkflowInterpreter:
    """
    WorkflowInterpreter is responsible for interpreting a workflow and its related information
    (e.g., schema, errors) into a natural language description that can be used
    by the LLM agent for generating suggestions.
    """

    def __init__(self):
        """Initialize the interpreter."""
        pass

    def interpret_workflow(
        self,
        workflow: Dict[str, Any],
        input_schema: Optional[Dict[str, Any]] = None,
        operator_errors: Optional[Dict[str, Any]] = None,
        method: InterpretationMethod = InterpretationMethod.BY_PATH,
    ) -> str:
        """
        Interpret the workflow and generate a natural language description.

        Args:
            workflow: The workflow dictionary
            input_schema: The input schema dictionary for each operator
            operator_errors: Dictionary of static errors for each operator
            method: The interpretation method to use

        Returns:
            A natural language description of the workflow
        """
        try:
            if method == InterpretationMethod.RAW:
                return self._interpret_raw(workflow, input_schema, operator_errors)
            elif method == InterpretationMethod.BY_PATH:
                return self._interpret_by_path(workflow, input_schema, operator_errors)
            else:
                raise ValueError(f"Unsupported interpretation method: {method}")
        except Exception as e:
            stack_trace = traceback.format_exc()
            return (
                f"Error interpreting workflow: {str(e)}\n\nStacktrace:\n{stack_trace}"
            )

    def _interpret_raw(
        self,
        workflow: Dict[str, Any],
        input_schema: Optional[Dict[str, Any]] = None,
        operator_errors: Optional[Dict[str, Any]] = None,
    ) -> str:
        """
        Generate a raw description of the workflow using a simple template.

        Args:
            workflow: The workflow dictionary
            input_schema: The input schema dictionary for each operator
            operator_errors: Dictionary of static errors for each operator

        Returns:
            A raw description of the workflow
        """
        description = "Here is the workflow dict:\n"
        description += json.dumps(workflow, indent=2)

        if input_schema:
            description += "\n\nHere is the input schema for each operator:\n"
            description += json.dumps(input_schema, indent=2)

        if operator_errors:
            description += "\n\nHere are the static errors for each operator:\n"
            description += json.dumps(operator_errors, indent=2)

        return description

    def _interpret_by_path(
        self,
        workflow: Dict[str, Any],
        input_schema: Optional[Dict[str, Any]] = None,
        operator_errors: Optional[Dict[str, Any]] = None,
    ) -> str:
        """
        Interprets a workflow by analyzing execution paths.

        Args:
            workflow: JSON representation of the workflow
            input_schema: Optional dictionary mapping operator IDs to their input schemas
            operator_errors: Optional dictionary mapping operator IDs to their errors

        Returns:
            A natural language interpretation of the workflow
        """
        try:
            # Create a TexeraWorkflow object from the workflow dictionary
            texera_workflow = TexeraWorkflow(
                workflow_dict=workflow,
                input_schema=input_schema or {},
                operator_errors=operator_errors or {},
            )

            # Get all paths through the workflow
            paths = texera_workflow.get_all_paths()

            if not paths:
                return "This workflow doesn't contain any complete execution paths."

            interpretation = "This workflow contains the following execution paths:\n\n"

            # Generate descriptions for each path
            for i, path in enumerate(paths):
                try:
                    interpretation += f"Path {i+1}:\n"

                    # Extract the subworkflow for this path
                    path_workflow = texera_workflow.extract_path_workflow(path)

                    # Generate description for this path workflow
                    path_description = self._describe_path_workflow(path_workflow)
                    interpretation += path_description + "\n"
                except Exception as e:
                    interpretation += f"Error processing path {i+1}: {str(e)}\n\n"

            return interpretation

        except Exception as e:
            stack_trace = traceback.format_exc()
            return f"Error interpreting workflow by path: {str(e)}\n\nStacktrace:\n{stack_trace}"

    def _describe_path_workflow(self, path_workflow: TexeraWorkflow) -> str:
        """
        Generate a description for a path workflow.

        Args:
            path_workflow: A TexeraWorkflow object representing a path

        Returns:
            A description of the path
        """
        try:
            description = ""

            # Get operator dictionaries directly from the workflow content
            operators = path_workflow.workflow_dict.get("content", {}).get(
                "operators", []
            )
            links = path_workflow.workflow_dict.get("content", {}).get("links", [])

            # Sort operators based on their position in the path
            ordered_operators = self._sort_operators_by_links(operators, links)

            # Describe each operator and its connections
            for i, operator in enumerate(ordered_operators):
                try:
                    operator_id = operator[
                        "operatorID"
                    ]  # This should exist in every operator
                    operator_type = operator.get("operatorType", "Unknown")
                    # Use customDisplayName if available, otherwise operatorType as fallback
                    operator_name = operator.get("customDisplayName", operator_type)

                    # Get input schema for this operator if available
                    input_schema_list = path_workflow._get_input_schemas_for_operator(
                        operator_id
                    )

                    description += f"- {operator_name} ({operator_type})"

                    # Add operator properties if available
                    if "operatorProperties" in operator:
                        props = operator["operatorProperties"]
                        description += "\n  Properties:"
                        for prop_name, prop_value in props.items():
                            # Display full property values without truncation
                            description += f"\n    - {prop_name}: {prop_value}"

                    # Add any static errors if they exist
                    if (
                        path_workflow.operator_errors
                        and operator_id in path_workflow.operator_errors
                    ):
                        operator_error = path_workflow.operator_errors[operator_id]
                        if operator_error:  # Only add if there's an actual error
                            description += f"\n  ERROR: {operator_error}"

                    # Add schema information if available
                    if input_schema_list and len(input_schema_list) > 0:
                        description += "\n  Input Schema:"
                        for schema_item in input_schema_list:
                            # Check if schema_item is a dictionary
                            if isinstance(schema_item, dict):
                                port = schema_item.get("port", "unknown")
                                attributes = schema_item.get("attributes", [])
                                description += f"\n    - Port: {port}"
                                description += f"\n      Attributes: {attributes}"
                            # For list schema items or other types, display differently
                            elif isinstance(schema_item, list):
                                description += (
                                    f"\n    - Schema items: {len(schema_item)}"
                                )
                                # Show all items without truncation
                                for idx, attr in enumerate(schema_item):
                                    description += f"\n      Item {idx}: {attr}"
                            else:
                                description += f"\n    - Schema: {str(schema_item)}"

                    # For operators other than the last one, show where it connects to
                    if i < len(ordered_operators) - 1:
                        next_operator = ordered_operators[i + 1]
                        next_op_id = next_operator["operatorID"]
                        connection = self._find_connection(
                            operator_id, next_op_id, links
                        )

                        if connection:
                            source_port = connection["source"]["portID"]
                            target_port = connection["target"]["portID"]
                            next_op_name = next_operator.get(
                                "customDisplayName",
                                next_operator.get("operatorType", "Unknown"),
                            )
                            description += f"\n  Connects from port '{source_port}' to '{next_op_name}' port '{target_port}'"

                    description += "\n"
                except Exception as e:
                    description += f"Error describing operator: {str(e)}\n"

            return description
        except Exception as e:
            stack_trace = traceback.format_exc()
            return f"Error describing path workflow: {str(e)}\n\nStacktrace:\n{stack_trace}"

    def _sort_operators_by_links(
        self, operators: List[Dict[str, Any]], links: List[Dict[str, Any]]
    ) -> List[Dict[str, Any]]:
        """
        Sort operators based on their connections in the links.

        Args:
            operators: List of operator dictionaries
            links: List of link dictionaries

        Returns:
            List of sorted operator dictionaries
        """
        try:
            # Create a map of operator IDs to operators
            operator_map = {op["operatorID"]: op for op in operators}

            # Create a directed graph representing operator connections
            graph = defaultdict(list)
            for link in links:
                source_id = link["source"]["operatorID"]
                target_id = link["target"]["operatorID"]
                graph[source_id].append(target_id)

            # Find source operators (operators with no incoming links)
            incoming_links = defaultdict(int)
            for link in links:
                target_id = link["target"]["operatorID"]
                incoming_links[target_id] += 1

            source_operators = [
                op for op in operators if incoming_links.get(op["operatorID"], 0) == 0
            ]

            # Perform topological sort
            visited = set()
            ordered_operators = []

            def dfs(op_id):
                if op_id in visited:
                    return
                visited.add(op_id)
                for neighbor in graph.get(op_id, []):
                    dfs(neighbor)
                if op_id in operator_map:
                    ordered_operators.append(operator_map[op_id])

            for op in source_operators:
                dfs(op["operatorID"])

            # Reverse to get correct order from source to sink
            return ordered_operators[::-1]

        except Exception as e:
            print(f"Error in _sort_operators_by_links: {str(e)}")
            # If sorting fails, return operators as is
            return operators

    def _find_connection(
        self, source_id: str, target_id: str, links: List[Dict[str, Any]]
    ) -> Optional[Dict[str, Any]]:
        """
        Find the link connecting two operators.

        Args:
            source_id: ID of the source operator
            target_id: ID of the target operator
            links: List of link dictionaries

        Returns:
            The link dictionary if found, None otherwise
        """
        try:
            for link in links:
                if (
                    link["source"]["operatorID"] == source_id
                    and link["target"]["operatorID"] == target_id
                ):
                    return link
            return None
        except Exception as e:
            print(f"Error in _find_connection: {str(e)}")
            return None
