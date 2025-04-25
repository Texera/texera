from typing import Dict, List, Any, Optional
import json
import uuid
import os
from dotenv import load_dotenv

from workflow_interpretation.interpreter import (
    WorkflowInterpreter,
    InterpretationMethod,
)
from model.Tuple import Tuple
from model.DataSchema import DataSchema, Attribute, AttributeType
from llm_agent.base import LLMAgentFactory


# Load environment variables from .env file if present
load_dotenv()


class SuggestionGenerator:
    """
    SuggestionGenerator is responsible for generating workflow suggestions
    based on the current workflow state, compilation information, and result data.
    """

    def __init__(self):
        """
        Initialize the suggestion generator.

        Args:
            llm_provider: The LLM provider to use (defaults to environment variable LLM_PROVIDER)
            llm_model: The LLM model to use (defaults to environment variable LLM_MODEL)
            llm_api_key: The API key for the LLM provider (defaults to environment variable based on provider)
        """
        self.workflow_interpreter = WorkflowInterpreter()

        # Determine provider and model
        self.llm_provider = os.environ.get("LLM_PROVIDER")

        if self.llm_provider == "openai":
            self.llm_model = os.environ.get("OPENAI_MODEL")
        elif self.llm_provider == "anthropic":
            self.llm_model = os.environ.get("ANTHROPIC_MODEL")

        # Create the LLM agent
        try:
            extra_params = {}

            if self.llm_provider == "openai":
                tools = []
                vector_store_ids_raw = os.environ.get("OPENAI_VECTOR_STORE_IDS", "")
                vector_store_ids = [
                    v.strip() for v in vector_store_ids_raw.split(",") if v.strip()
                ]
                if vector_store_ids:
                    tools.append(
                        {"type": "file_search", "vector_store_ids": vector_store_ids}
                    )
                extra_params["tools"] = tools
                extra_params["project"] = os.environ.get("OPENAI_PROJECT_ID")
                extra_params["organization"] = os.environ.get("OPENAI_ORG_ID")
            self.llm_agent = LLMAgentFactory.create(
                self.llm_provider, model=self.llm_model, **extra_params
            )
        except ValueError as e:
            print(f"Error creating LLM agent: {str(e)}")
            self.llm_agent = None

    def generate_suggestions(
        self,
        workflow_json: Dict[str, Any],
        compilation_state: Dict[str, Any],
        result_tables: Dict[str, Dict[str, Any]],
        execution_state: Optional[Dict[str, Any]] = None,
    ) -> List[Dict[str, Any]]:
        """
        Generate workflow suggestions based on the current workflow, compilation state, execution state, and result tables.

        Args:
            workflow_json: The current workflow configuration
            compilation_state: Compilation information and errors
            result_tables: Result data for each operator
            execution_state: Current execution state of the workflow
            max_suggestions: Maximum number of suggestions to generate

        Returns:
            A list of workflow suggestions
        """
        # For debugging purposes
        print(
            f"Generating suggestions for workflow with {len(workflow_json.get('content', {}).get('operators', []))} operators"
        )
        print(f"Compilation state: {compilation_state['state']}")
        if execution_state:
            print(f"Execution state: {execution_state['state']}")
        print(f"Result tables available for {len(result_tables)} operators")

        # Generate natural language description of the workflow
        workflow_description = self._generate_workflow_prompt(
            workflow_json,
            compilation_state.get("operatorInputSchemaMap"),
            compilation_state.get("operatorErrors"),
        )

        print("Generated workflow description:")
        print(workflow_description)

        # If we have a valid LLM agent, use it to generate suggestions
        if self.llm_agent:
            try:
                # Add context to the workflow description about compilation and execution state
                enriched_prompt = self._enhance_prompt_with_state_info(
                    workflow_description, compilation_state, execution_state
                )

                # Get suggestions from the LLM agent
                suggestions = self.llm_agent.generate_suggestions(
                    prompt=enriched_prompt,
                    temperature=0.7,  # Lower temperature for more focused suggestions
                )

                # Convert suggestions to the expected format for the frontend
                # formatted_suggestions = self._convert_to_frontend_format(suggestions)

                # Return generated suggestions (if any were generated)
                return suggestions
            except Exception as e:
                print(f"Error generating suggestions with LLM: {str(e)}")
                # Fall back to mock suggestions on error

        # If LLM generation failed or agent is not available, return mock suggestions
        return self._generate_mock_suggestions(
            workflow_json, compilation_state, execution_state
        )

    def _generate_workflow_prompt(
        self,
        workflow_json: Dict[str, Any],
        input_schema: Optional[Dict[str, Any]] = None,
        operator_errors: Optional[Dict[str, Any]] = None,
        method: InterpretationMethod = InterpretationMethod.BY_PATH,
    ) -> str:
        """
        Generate a natural language description of the workflow for use in prompts.

        Args:
            workflow_json: The workflow dictionary
            input_schema: The input schema dictionary for each operator
            operator_errors: Dictionary of static errors for each operator
            method: The interpretation method to use

        Returns:
            A natural language description of the workflow
        """
        try:
            # Use the workflow interpreter to generate a description
            description = self.workflow_interpreter.interpret_workflow(
                workflow_json, input_schema, operator_errors, method
            )

            return description
        except Exception as e:
            print(f"Error generating workflow prompt: {str(e)}")
            # Fallback to a simple description if interpretation fails
            return f"Workflow with {len(workflow_json.get('content', {}).get('operators', []))} operators"

    def _enhance_prompt_with_state_info(
        self,
        workflow_description: str,
        compilation_state: Dict[str, Any],
        execution_state: Optional[Dict[str, Any]],
    ) -> str:
        """
        Enhance the workflow description with compilation and execution state information.

        Args:
            workflow_description: Natural language description of the workflow
            compilation_state: Compilation information and errors
            execution_state: Current execution state of the workflow

        Returns:
            Enhanced workflow description
        """
        prompt = workflow_description + "\n\n"

        # Add compilation state info
        prompt += f"Compilation State: {compilation_state['state']}\n"

        # Add compilation errors if any
        if compilation_state["state"] == "Failed" and compilation_state.get(
            "operatorErrors"
        ):
            prompt += "Compilation Errors:\n"
            for op_id, error in compilation_state["operatorErrors"].items():
                if error:
                    prompt += f"- Operator {op_id}: {error}\n"

        # Add execution state info if available
        if execution_state:
            prompt += f"\nExecution State: {execution_state['state']}\n"

            # Add execution errors if any
            if execution_state["state"] == "Failed" and execution_state.get(
                "errorMessages"
            ):
                prompt += "Execution Errors:\n"
                for error in execution_state["errorMessages"]:
                    prompt += f"- {error}\n"

        # Add final instruction
        prompt += "\nBased on this workflow description and state information, suggest improvements or fixes."

        return prompt

    def _convert_to_frontend_format(
        self, suggestions: List[Dict[str, Any]]
    ) -> List[Dict[str, Any]]:
        """
        Convert LLM-generated suggestions to the format expected by the frontend.

        Args:
            suggestions: List of suggestions from the LLM agent

        Returns:
            List of suggestions formatted for the frontend
        """
        formatted_suggestions = []

        for suggestion in suggestions:
            # Create a new suggestion with the required format
            formatted = {
                "id": f"suggestion-{uuid.uuid4()}",
                "description": suggestion["suggestion"],
                "operatorsToAdd": [],
                "operatorPropertiesToChange": [],
                "operatorsToDelete": suggestion["changes"].get("operatorsToDelete", []),
                "linksToAdd": [],
                "isPreviewActive": False,
            }

            # Format operators to add
            for operator in suggestion["changes"].get("operatorsToAdd", []):
                formatted_operator = {
                    "operatorType": operator["operatorType"],
                    "position": {"x": 400, "y": 300},  # Default position
                    "properties": operator.get("operatorProperties", {}),
                }

                # Add custom display name if provided
                if operator.get("customDisplayName"):
                    formatted_operator["customDisplayName"] = operator[
                        "customDisplayName"
                    ]

                formatted["operatorsToAdd"].append(formatted_operator)

            # Format operator properties to change
            for prop_change in suggestion["changes"].get(
                "operatorPropertiesToChange", []
            ):
                formatted_prop_change = {
                    "operatorId": prop_change["operatorID"],
                    "properties": prop_change["properties"],
                }
                formatted["operatorPropertiesToChange"].append(formatted_prop_change)

            # Format links to add
            for link in suggestion["changes"].get("linksToAdd", []):
                formatted_link = {
                    "source": {
                        "operatorId": link["source"]["operatorID"],
                        "portId": link["source"]["portID"],
                    },
                    "target": {
                        "operatorId": link["target"]["operatorID"],
                        "portId": link["target"]["portID"],
                    },
                }
                formatted["linksToAdd"].append(formatted_link)

            formatted_suggestions.append(formatted)

        return formatted_suggestions

    def _generate_mock_suggestions(
        self,
        workflow_json: Dict[str, Any],
        compilation_state: Dict[str, Any],
        execution_state: Optional[Dict[str, Any]],
    ) -> List[Dict[str, Any]]:
        """
        Generate mock suggestions for testing or when LLM generation fails.

        Args:
            workflow_json: The current workflow configuration
            compilation_state: Compilation information and errors
            execution_state: Current execution state of the workflow

        Returns:
            A list of mock workflow suggestions
        """
        # Extract operators from the workflow
        operators = workflow_json.get("content", {}).get("operators", [])

        # Create a list to store suggestions
        suggestions = []

        # Add suggestions based on the workflow state
        if compilation_state["state"] == "Succeeded":
            # Add mock suggestion 1: Add a KeywordSearch operator
            suggestion1 = {
                "id": f"suggestion-{uuid.uuid4()}",
                "description": "Add a KeywordSearch operator with sentiment analysis",
                "operatorsToAdd": [
                    {
                        "operatorType": "KeywordSearch",
                        "position": {"x": 400, "y": 300},
                        "properties": {
                            "keyword": "climate change",
                            "attributes": ["content", "title"],
                        },
                    },
                    {
                        "operatorType": "SentimentAnalysis",
                        "position": {"x": 600, "y": 300},
                        "properties": {
                            "attribute": "content",
                            "resultAttribute": "sentiment",
                        },
                    },
                ],
                "operatorPropertiesToChange": [
                    {
                        "operatorId": "View-Results-1",
                        "properties": {"limit": 20, "offset": 0},
                    }
                ],
                "operatorsToDelete": [],
                "linksToAdd": [
                    {
                        "source": {"operatorId": "Source-Scan-1", "portId": "output-0"},
                        "target": {
                            "operatorId": "KeywordSearch-1",
                            "portId": "input-0",
                        },
                    },
                    {
                        "source": {
                            "operatorId": "KeywordSearch-1",
                            "portId": "output-0",
                        },
                        "target": {
                            "operatorId": "SentimentAnalysis-1",
                            "portId": "input-0",
                        },
                    },
                    {
                        "source": {
                            "operatorId": "SentimentAnalysis-1",
                            "portId": "output-0",
                        },
                        "target": {"operatorId": "View-Results-1", "portId": "input-0"},
                    },
                ],
                "isPreviewActive": False,
            }
            suggestions.append(suggestion1)

            # Add suggestions based on execution state
            if execution_state and execution_state["state"] == "Completed":
                # Add a suggestion for optimization if workflow completed successfully
                suggestion3 = {
                    "id": f"suggestion-{uuid.uuid4()}",
                    "description": "Optimize workflow with projection and sorting",
                    "operatorsToAdd": [
                        {
                            "operatorType": "Projection",
                            "position": {"x": 400, "y": 200},
                            "properties": {
                                "attributes": ["id", "name", "price", "category"]
                            },
                        },
                        {
                            "operatorType": "Sort",
                            "position": {"x": 600, "y": 200},
                            "properties": {
                                "sortAttributesList": [
                                    {"attributeName": "price", "order": "desc"}
                                ]
                            },
                        },
                    ],
                    "operatorPropertiesToChange": [
                        {
                            "operatorId": "Source-Scan-1",
                            "properties": {"tableName": "products", "limit": 1000},
                        }
                    ],
                    "operatorsToDelete": [],
                    "linksToAdd": [
                        {
                            "source": {
                                "operatorId": "Source-Scan-1",
                                "portId": "output-0",
                            },
                            "target": {
                                "operatorId": "Projection-1",
                                "portId": "input-0",
                            },
                        },
                        {
                            "source": {
                                "operatorId": "Projection-1",
                                "portId": "output-0",
                            },
                            "target": {"operatorId": "Sort-1", "portId": "input-0"},
                        },
                        {
                            "source": {"operatorId": "Sort-1", "portId": "output-0"},
                            "target": {
                                "operatorId": "View-Results-1",
                                "portId": "input-0",
                            },
                        },
                    ],
                    "isPreviewActive": False,
                }
                suggestions.append(suggestion3)

            # If execution state has errors, add suggestions for fixing them
            if (
                execution_state
                and execution_state["state"] == "Failed"
                and execution_state.get("errorMessages")
            ):
                suggestion_error_fix = {
                    "id": f"suggestion-{uuid.uuid4()}",
                    "description": "Fix data type issues in workflow",
                    "operatorsToAdd": [
                        {
                            "operatorType": "TypeConversion",
                            "position": {"x": 300, "y": 250},
                            "properties": {
                                "targetType": "string",
                                "attributeToConvert": "id",
                            },
                        }
                    ],
                    "operatorPropertiesToChange": [],
                    "operatorsToDelete": [],
                    "linksToAdd": [
                        {
                            "source": {
                                "operatorId": "Source-Scan-1",
                                "portId": "output-0",
                            },
                            "target": {
                                "operatorId": "TypeConversion-1",
                                "portId": "input-0",
                            },
                        }
                    ],
                    "isPreviewActive": False,
                }
                suggestions.append(suggestion_error_fix)

        # Add suggestion based on the operator types
        if any(op.get("operatorType") == "CSVFileScan" for op in operators):
            suggestion2 = {
                "id": f"suggestion-{uuid.uuid4()}",
                "description": "Replace ScanSource with CSVFileScan for better performance",
                "operatorsToAdd": [
                    {
                        "operatorType": "CSVFileScan",
                        "position": {"x": 200, "y": 200},
                        "properties": {
                            "fileName": "data.csv",
                            "limit": -1,
                            "offset": 0,
                            "schema": "auto",
                        },
                    }
                ],
                "operatorPropertiesToChange": [],
                "operatorsToDelete": ["Source-Scan-1"],
                "linksToAdd": [
                    {
                        "source": {"operatorId": "CSVFileScan-1", "portId": "output-0"},
                        "target": {"operatorId": "View-Results-1", "portId": "input-0"},
                    }
                ],
                "isPreviewActive": False,
            }
            suggestions.append(suggestion2)

        return suggestions
