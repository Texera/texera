import json
from typing import Dict, Any, List, Optional

from openai import OpenAI

from llm_agent.base import LLMAgent, LLMAgentFactory
from model.llm.output_format import SuggestionList


@LLMAgentFactory.register("openai")
class OpenAIAgent(LLMAgent):
    """
    Implementation of the LLM agent using OpenAI's `responses.create` API with JSON Schema validation.
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

        # Load JSON Schema from file
        with open("files/output_format.json", "r") as f:
            self.schema = json.load(f)

        # Load instruction from file
        with open("files/instruction.md", "r") as f:
            self.instruction = f.read()

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
            max_suggestions: Max number of suggestions to return
            temperature: Sampling temperature
            max_tokens: Maximum tokens allowed in output
            **kwargs: Additional options

        Returns:
            A list of workflow suggestion dicts.
        """
        try:
            response = self.client.responses.create(
                model=self.model,
                instructions=self.instruction,
                input=prompt,
                tools=self.tools,
                text={
                    "format": {
                        "type": "json_schema",
                        "name": "workflow_suggestions",
                        "schema": SuggestionList.model_json_schema(),  # from Pydantic
                        "strict": False,
                    }
                },
            )
            suggestions = json.loads(response.output_text)
            return suggestions["suggestions"]

        except Exception as e:
            print(f"Error generating suggestions: {e}")
            return []
