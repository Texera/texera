import json
import uuid
from typing import Dict, Any, List, Optional

from openai import OpenAI
from llm_agent.base import LLMAgent, LLMAgentFactory
from llm_agent.utils.operator_metadata_converter import extract_json_schemas
from model.llm.sanitizor import OperatorSchema, SuggestionSanitization
from model.llm.suggestion import SuggestionList, Suggestion


@LLMAgentFactory.register("openai")
class OpenAIAgent(LLMAgent):
    """
    Implementation of the LLM agent using OpenAI's `responses.create` API
    to both generate and sanitize workflow suggestions.
    """

    def __init__(
        self,
        model,
        tools,
        api_key,
        project,
        organization,
        use_function_calls: bool = True,
    ):
        """
        Initialize the OpenAI agent.

        Args:
            model: OpenAI model name (e.g., "gpt-4o-2024-08-06")
            api_key: API key for OpenAI
            project: Project ID for usage (optional)
        """
        super().__init__(model, api_key)
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

        with open("files/instruction_for_function_call.md", "r") as f:
            self.instruction_for_suggestion_fc = f.read()

        self.function_tools = [
            {
                "type": "function",
                "name": "extract_json_schemas",
                "description": (
                    "Return a list of JSON schemas for given Texera operator types. "
                ),
                "parameters": {
                    "type": "object",
                    "properties": {
                        "operator_types": {
                            "type": "array",
                            "description": "List of operatorType names to fetch.",
                            "items": {"type": "string"},
                        },
                    },
                    "required": ["operator_types"],
                    "additionalProperties": False,
                },
                "strict": True,
            }
        ]

        self.use_function_calls = use_function_calls
        self.enable_llm_sanitizor = False

    def generate_suggestions(
        self,
        prompt: str,
        temperature: float = 0.7,
        max_tokens: Optional[int] = None,
        **kwargs,
    ) -> SuggestionList:
        if not self.use_function_calls:
            # fall back to the old single‑shot method
            return self._generate_suggestions_one_shot(prompt, temperature, max_tokens)
        else:
            # NEW two‑turn method
            return self._generate_suggestions_with_function_calls(
                prompt, temperature, max_tokens
            )

    def _generate_suggestions_one_shot(self, prompt, temperature, max_tokens):
        """
        Generate workflow suggestions using OpenAI's `responses.create` endpoint with schema enforcement.

        Args:
            prompt: Workflow description
            temperature: Sampling temperature
            max_tokens: Maximum tokens allowed in output

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
                        extract_json_schemas([op_type])
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
                OperatorSchema(**extract_json_schemas([op_type])[0])
                for op_type in operator_types_seen
            ]
            sanitization_input = SuggestionSanitization(
                suggestions=SuggestionList(
                    suggestions=[Suggestion(**s) for s in valid_suggestions]
                ),
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

    def _call_function(self, name, args):
        if name == "extract_json_schemas":
            return extract_json_schemas(**args)
        else:
            raise ValueError(f"Unknown function: {name}")

    def _generate_suggestions_with_function_calls(
        self, prompt: str, temperature: float, max_tokens: Optional[int]
    ) -> SuggestionList:
        """
        Two‑step approach:
          1) Ask the model which operator types it needs & have it call
             `extract_json_schemas` (one call per distinct type).
          2) Execute those calls locally, hand results back, let the model
             produce the final SuggestionList JSON.
        """
        input_messages = [{"role": "user", "content": prompt}]
        # -- 1️⃣  first turn: let the model issue tool calls ------------------
        first_resp = self.client.responses.create(
            model=self.model,
            instructions=self.instruction_for_suggestion_fc,
            input=input_messages,
            tool_choice="required",
            tools=self.function_tools,
        )

        for tool_call in first_resp.output:
            if tool_call.type != "function_call":
                continue

            name = tool_call.name
            args = json.loads(tool_call.arguments)

            result = self._call_function(name, args)
            input_messages.append(
                {
                    "type": "function_call",
                    "call_id": tool_call.call_id,
                    "name": tool_call.name,
                    "arguments": tool_call.arguments,
                }
            )
            input_messages.append(
                {
                    "type": "function_call_output",
                    "call_id": tool_call.call_id,
                    "output": str(result),
                }
            )

        # -- 3️⃣  second turn: give the model the schemas & ask for suggestions
        second_resp = self.client.responses.create(
            model=self.model,
            input=input_messages,
            instructions=self.instruction_for_suggestion_fc,
            tools=self.function_tools,
            tool_choice="none",
            text={
                "format": {  # enforce SuggestionList schema again
                    "type": "json_schema",
                    "name": "workflow_suggestions",
                    "schema": SuggestionList.model_json_schema(),
                    "strict": False,
                }
            },
        )
        raw_suggestions = json.loads(second_resp.output_text)["suggestions"]
        with open("raw_suggestions_debug.json", "w") as f:
            json.dump(raw_suggestions, f, indent=2)

        sanitized_suggestions = self._sanitize_suggestions(raw_suggestions)

        return sanitized_suggestions
