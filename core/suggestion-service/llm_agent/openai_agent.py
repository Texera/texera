"""OpenAI LLM agent."""
import os
import json
from typing import Dict, Any, List, Optional

import openai
from openai import OpenAI

from llm_agent.base import LLMAgent, LLMAgentFactory
from output_formatter.formatter import format_raw_suggestions


@LLMAgentFactory.register("openai")
class OpenAIAgent(LLMAgent):
    """
    Implementation of the LLM agent interface using OpenAI's API.
    
    This agent supports different model types from OpenAI, such as:
    - gpt-4-turbo-preview
    - gpt-4
    - gpt-3.5-turbo
    """
    
    def __init__(self, 
                 model: str = "gpt-4-turbo-preview",
                 api_key: Optional[str] = None):
        """
        Initialize the OpenAI agent.
        
        Args:
            model: The OpenAI model to use
            api_key: The OpenAI API key (if None, uses OPENAI_API_KEY environment variable)
        """
        self.model = model
        self.client = OpenAI(api_key=api_key or os.environ.get("OPENAI_API_KEY"))
        
    def generate_suggestions(self, 
                            prompt: str, 
                            max_suggestions: int = 3,
                            temperature: float = 0.7,
                            max_tokens: Optional[int] = None,
                            **kwargs) -> List[Dict[str, Any]]:
        """
        Generate workflow suggestions using OpenAI's API.
        
        Args:
            prompt: The natural language prompt describing the workflow
            max_suggestions: Maximum number of suggestions to generate
            temperature: Sampling temperature (0.0-1.0) where lower is more deterministic
            max_tokens: Maximum number of tokens to generate
            **kwargs: Additional OpenAI-specific parameters
            
        Returns:
            A list of suggestion dictionaries formatted according to the interface
        """
        # Enhance the prompt with instruction about the output format
        system_prompt = self._create_system_prompt(max_suggestions)
        
        try:
            response = self.client.chat.completions.create(
                model=self.model,
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": prompt}
                ],
                temperature=temperature,
                max_tokens=max_tokens,
                n=1,  # Generate a single completion
                **kwargs
            )
            
            # Extract content from the response
            raw_content = response.choices[0].message.content
            
            # Parse and format the suggestions
            suggestions = format_raw_suggestions(raw_content)
            
            return suggestions[:max_suggestions]
            
        except Exception as e:
            print(f"Error generating suggestions with OpenAI: {str(e)}")
            return []
            
    def _create_system_prompt(self, max_suggestions: int) -> str:
        """Create the system prompt for the OpenAI model."""
        return f"""You are an AI assistant that helps users improve their Texera workflows by suggesting useful modifications.

Analyze the provided workflow description and generate {max_suggestions} suggestions to improve it.
Your suggestions should address common issues, optimizations, or additional useful features.

For each suggestion, provide:
1. A brief natural language description of the recommendation
2. Detailed changes required to implement the suggestion

Structure each suggestion as a valid JSON object with the following format:
{{
    "suggestion": "A clear description of the improvement",
    "changes": {{
        "operatorsToAdd": [
            {{
                "operatorType": "TypeOfOperator",
                "operatorID": "OperatorType-operator-UUID",
                "operatorProperties": {{
                    "property1": "value1",
                    "property2": "value2"
                }},
                "customDisplayName": "Logical name describing function"
            }}
        ],
        "linksToAdd": [
            {{
                "linkID": "link-UUID",
                "source": {{
                    "operatorID": "SourceOperatorID",
                    "portID": "output-N"
                }},
                "target": {{
                    "operatorID": "TargetOperatorID",
                    "portID": "input-N"
                }}
            }}
        ],
        "operatorsToDelete": ["OperatorID1", "OperatorID2"],
        "operatorPropertiesToChange": [
            {{
                "operatorID": "ExistingOperatorID",
                "properties": {{
                    "propertyToChange": "newValue"
                }}
            }}
        ]
    }}
}}

Output all suggestions as a valid JSON array and ensure each suggestion follows the exact format above.""" 