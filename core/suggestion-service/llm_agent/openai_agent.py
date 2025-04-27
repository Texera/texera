import json
from typing import Dict, Any, List, Optional

from openai import OpenAI

from llm_agent.base import LLMAgent, LLMAgentFactory
from llm_agent.utils.operator_metadata_converter import extract_json_schema
from model.llm.sanitizor import OperatorSchema, SuggestionSanitization
from model.llm.suggestion import SuggestionList


@LLMAgentFactory.register("openai")
class OpenAIAgent(LLMAgent):
    """
    Implementation of the LLM agent using OpenAI's `responses.create` API
    to both generate and sanitize workflow suggestions.
    """

    def __init__(self, model, tools, api_key, project, organization):
        """
        Initialize the OpenAI agent.

        Args:
            model: OpenAI model name (e.g., "gpt-4o-2024-08-06")
            api_key: API key for OpenAI
            project: Project ID for usage (optional)
        """
        self.model = model
        self.tools = tools
        self.client = OpenAI(
            api_key=api_key, project=project, organization=organization
        )

        # Load first phase instruction
        with open("files/instruction_for_suggestion.md", "r") as f:
            self.instruction_for_suggestion = f.read()

        # Load sanitizer instruction
        with open("files/instruction_for_sanitizor.md", "r") as f:
            self.instruction_for_sanitizor = f.read()

    def generate_suggestions(
        self,
        prompt: str,
        temperature: float = 0.7,
        max_tokens: Optional[int] = None,
        **kwargs,
    ) -> List[Dict[str, Any]]:
        """
        Generate workflow suggestions using OpenAI's `responses.create` endpoint with schema enforcement.

        Args:
            prompt: Workflow description
            temperature: Sampling temperature
            max_tokens: Maximum tokens allowed in output
            **kwargs: Additional options

        Returns:
            A list of sanitized suggestion dicts.
        """
        try:
            # Step 1: Generate raw suggestions
            raw_response = self.client.responses.create(
                model=self.model,
                instructions=self.instruction_for_suggestion,
                input=prompt,
                tools=self.tools,
                text={
                    "format": {
                        "type": "json_schema",
                        "name": "workflow_suggestions",
                        "schema": SuggestionList.model_json_schema(),
                        "strict": False,  # Allow flexible output in generation
                    }
                },
            )

            raw_suggestions = json.loads(raw_response.output_text)["suggestions"]

            # Step 2: Sanitize the suggestions
            sanitized_suggestions = self._sanitize_suggestions(raw_suggestions)

            return sanitized_suggestions

        except Exception as e:
            print(f"Error generating suggestions: {e}")
            return []

    def _sanitize_suggestions(
        self, suggestions: List[Dict[str, Any]]
    ) -> List[Dict[str, Any]]:
        """
        Internal method to sanitize raw suggestions using OpenAI.

        Args:
            suggestions: The raw suggestions to sanitize

        Returns:
            A sanitized list of suggestions
        """
        try:
            # Extract involved operator types
            operator_types = set()
            for suggestion in suggestions:
                for op in suggestion["changes"]["operatorsToAdd"]:
                    operator_types.add(op["operatorType"])

            # Prepare operator schemas
            operator_schemas = [
                OperatorSchema(**extract_json_schema(op_type, properties_only=True))
                for op_type in operator_types
            ]

            # Build sanitizer input
            sanitization_input = SuggestionSanitization(
                suggestions=SuggestionList(suggestions=suggestions),
                schemas=operator_schemas,
            )

            sanitize_response = self.client.responses.create(
                model=self.model,
                instructions=self.instruction_for_sanitizor,
                input=sanitization_input.model_dump_json(),
                tools=self.tools,
                text={
                    "format": {
                        "type": "json_schema",
                        "name": "workflow_suggestions",
                        "schema": SuggestionList.model_json_schema(),
                        "strict": False,
                    }
                },
            )

            sanitized = json.loads(sanitize_response.output_text)
            return sanitized["suggestions"]

        except Exception as e:
            print(f"Error sanitizing suggestions: {e}")
            return []
