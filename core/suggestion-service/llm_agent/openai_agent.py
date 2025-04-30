import json
import uuid
from typing import Dict, Any, List, Optional

from openai import OpenAI

from llm_agent.base import LLMAgent, LLMAgentFactory
from llm_agent.utils.operator_metadata_converter import extract_json_schema
from model.llm.sanitizor import OperatorSchema, SuggestionSanitization
from model.llm.suggestion import SuggestionList, Suggestion


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

        self.enable_llm_sanitizor = False

    def generate_suggestions(
        self,
        prompt: str,
        temperature: float = 0.7,
        max_tokens: Optional[int] = None,
        **kwargs,
    ) -> SuggestionList:
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
                # text_format=SuggestionList
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
            with open("raw_suggestions_debug.json", "w") as f:
                json.dump(raw_suggestions, f, indent=2)
            # Step 2: Sanitize the suggestions
            sanitized_suggestions = self._sanitize_suggestions(raw_suggestions)

            return sanitized_suggestions

        except Exception as e:
            print(f"Error generating suggestions: {e}")
            return SuggestionList(suggestions=[])

    def _sanitize_suggestions(
            self, suggestions: List[Dict[str, Any]]
    ) -> SuggestionList:
        """
        Internal method to sanitize raw suggestions. Optionally uses LLM
        if enabled, otherwise just validates and returns structured output.

        Args:
            suggestions: The raw suggestions to sanitize

        Returns:
            A sanitized list of suggestions
        """
        try:
            # Filter valid suggestions based on operator type
            valid_suggestions = []
            operator_types_seen = set()

            for suggestion in suggestions:
                operator_types = {
                    op["operatorType"] for op in suggestion["changes"]["operatorsToAdd"]
                }
                try:
                    for op_type in operator_types:
                        # Check validity by attempting schema extraction
                        extract_json_schema(op_type, properties_only=True)
                        operator_types_seen.add(op_type)

                    # Fix missing linkIDs
                    for link in suggestion["changes"]["linksToAdd"]:
                        if "linkID" not in link or not link["linkID"]:
                            link["linkID"] = f"link-{uuid.uuid4()}"

                    valid_suggestions.append(suggestion)
                except ValueError:
                    continue  # Skip suggestion if operatorType is invalid

            # If LLM sanitization is disabled, directly parse into Pydantic models
            if not self.enable_llm_sanitizor:
                structured = [Suggestion(**s) for s in valid_suggestions]
                return SuggestionList(suggestions=structured)

            # Otherwise: Use LLM for further validation/sanitization
            operator_schemas = [
                OperatorSchema(**extract_json_schema(op_type, properties_only=True))
                for op_type in operator_types_seen
            ]
            sanitization_input = SuggestionSanitization(
                suggestions=SuggestionList(suggestions=[Suggestion(**s) for s in valid_suggestions]),
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

            sanitized_result = json.loads(sanitize_response.output_text)
            return SuggestionList(
                suggestions=[Suggestion(**s) for s in sanitized_result["suggestions"]]
            )

        except Exception as e:
            print(f"Error sanitizing suggestions: {e}")
            return SuggestionList(suggestions=[])
