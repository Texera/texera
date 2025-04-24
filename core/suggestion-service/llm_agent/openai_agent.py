import os
import json
from typing import Dict, Any, List, Optional

from openai import OpenAI

from llm_agent.base import LLMAgent, LLMAgentFactory


@LLMAgentFactory.register("openai")
class OpenAIAgent(LLMAgent):
    """
    Implementation of the LLM agent using OpenAI's `responses.create` API with JSON Schema validation.
    """

    def __init__(self,
                 model: str = "gpt-4o-2024-08-06",
                 tools: list = [],
                 api_key: Optional[str] = None,
                 project: Optional[str] = None,
                 ):
        """
        Initialize the OpenAI agent.

        Args:
            model: OpenAI model name (e.g., "gpt-4o-2024-08-06")
            api_key: API key for OpenAI
            project: Project ID for usage (optional)
        """
        self.model = model
        self.tools = tools
        self.client = OpenAI(api_key=api_key or os.environ.get("OPENAI_API_KEY"),
                             project=project or os.environ.get("OPENAI_PROJECT_ID"))

        # Load JSON Schema from file
        with open("output_format.json", "r") as f:
            self.schema = json.load(f)

        # Load instruction from file
        with open("instruction.md", "r") as f:
            self.instruction = f.read()

    def generate_suggestions(self,
                             prompt: str,
                             max_suggestions: int = 3,
                             temperature: float = 0.7,
                             max_tokens: Optional[int] = None,
                             **kwargs) -> List[Dict[str, Any]]:
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
                        "schema": self.schema,
                        "strict": True
                    }
                }
            )

            suggestions = json.loads(response.output_text)
            return suggestions[:max_suggestions]

        except Exception as e:
            print(f"Error generating suggestions: {e}")
            return []
