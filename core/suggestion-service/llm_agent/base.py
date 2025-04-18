"""Base LLM agent interface"""
from abc import ABC, abstractmethod
from typing import Dict, Any, List, Optional


class LLMAgent(ABC):
    """
    Abstract base class for LLM agents that generate workflow suggestions.
    All LLM providers (OpenAI, Anthropic, etc.) should implement this interface.
    """

    @abstractmethod
    def generate_suggestions(self, 
                            prompt: str, 
                            max_suggestions: int = 3,
                            temperature: float = 0.7,
                            max_tokens: Optional[int] = None,
                            **kwargs) -> List[Dict[str, Any]]:
        """
        Generate workflow suggestions using the LLM provider.
        
        Args:
            prompt: The natural language prompt describing the workflow
            max_suggestions: Maximum number of suggestions to generate
            temperature: Sampling temperature (0.0-1.0) where lower is more deterministic
            max_tokens: Maximum number of tokens to generate
            **kwargs: Additional provider-specific parameters
        
        Returns:
            A list of suggestion dictionaries with the following format:
            {
                "suggestion": str,                  # Natural language description of the suggestion
                "changes": {
                    "operatorsToAdd": List[Dict],   # Operators to add to the workflow
                    "linksToAdd": List[Dict],       # Links to add between operators
                    "operatorsToDelete": List[str], # Operator IDs to delete
                    "operatorPropertiesToChange": List[Dict], # Properties to change on existing operators
                }
            }
        """
        pass


class LLMAgentFactory:
    """Factory for creating LLM agents based on provider name."""
    
    _registry = {}
    
    @classmethod
    def register(cls, provider_name: str):
        """Register an LLM agent class with the factory."""
        def inner_wrapper(wrapped_class):
            cls._registry[provider_name] = wrapped_class
            return wrapped_class
        return inner_wrapper
    
    @classmethod
    def create(cls, provider_name: str, **kwargs) -> LLMAgent:
        """
        Create an instance of the requested LLM agent.
        
        Args:
            provider_name: Name of the LLM provider
            **kwargs: Parameters to pass to the LLM agent constructor
        
        Returns:
            An instance of the requested LLM agent
        
        Raises:
            ValueError: If the provider is not registered
        """
        if provider_name not in cls._registry:
            raise ValueError(f"Unknown LLM provider: {provider_name}")
        
        return cls._registry[provider_name](**kwargs) 