from typing import Dict, List, Any, Optional
import json
import uuid
import os
from dotenv import load_dotenv

from model.llm.suggestion import SuggestionList
from model.web.input import CompilationStateInfo, ExecutionStateInfo
from workflow_interpretation.interpreter import (
    WorkflowInterpreter,
)
from model.llm.prompt import Prompt
from model.llm.interpretation import (
    PathInterpretation,
    RawInterpretation,
    OperatorInterpretation,
    BaseInterpretation,
    InterpretationMethod,
)
from llm_agent.base import LLMAgentFactory
from distutils.util import strtobool  # stdlib helper


# Load environment variables from .env file if present
load_dotenv()


# helper that treats 1/true/yes/y (case‑insensitive) as True
def env_bool(key: str, default: bool = False) -> bool:
    try:
        return bool(strtobool(os.getenv(key, str(default))))
    except ValueError:
        return default


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
        self.workflow_interpretation_method = InterpretationMethod(
            os.environ.get("INTERPRETATION_METHOD")
        )
        self.workflow_interpreter = WorkflowInterpreter(
            self.workflow_interpretation_method
        )

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
                extra_params["use_function_calls"] = env_bool(
                    "OPENAI_USE_FUNCTION_CALLS", default=True
                )
            self.llm_agent = LLMAgentFactory.create(
                self.llm_provider, model=self.llm_model, **extra_params
            )
        except ValueError as e:
            print(f"Error creating LLM agent: {str(e)}")
            self.llm_agent = None

    def generate_suggestions(
        self,
        workflow: str,
        compilation_state: CompilationStateInfo,
        execution_state: ExecutionStateInfo,
        intention: str,
        focusing_operator_ids: List[str],
    ) -> SuggestionList:
        """
        Generate workflow suggestions based on the current workflow, compilation state, execution state, and result tables.

        Args:
            workflow: The current workflow configuration
            compilation_state: Compilation information and errors
            execution_state: Current execution state of the workflow
            intention: The intention of the workflow
            focusing_operator_ids: List of operator IDs to focus on

        Returns:
            A list of workflow suggestions
        """
        # If LLM generation failed or agent is not available, return mock suggestions
        if not self.llm_agent:
            return SuggestionList(suggestions=[])
        workflow_json = json.loads(workflow)
        # Generate natural language description of the workflow
        interpretation = self.workflow_interpreter.interpret_workflow(
            workflow_json,
            compilation_state,
        )

        # Determine intention (fallback)
        if not intention:
            intention = "Recommend improvements and fixes of current workflows"

        workflow_intp = interpretation.get_base_workflow_interpretation()
        focusing_operators: List[OperatorInterpretation] = []
        for oid in focusing_operator_ids:
            op = workflow_intp.operators.get(oid)
            if op:
                focusing_operators.append(op)

        prompt_obj = Prompt(
            intention=intention,
            focusingOperators=focusing_operators,
            workflowInterpretation=interpretation,
        )

        # Serialize prompt to JSON string to feed into LLM
        workflow_description = prompt_obj.model_dump_json(indent=2)

        # Get suggestions from the LLM agent
        suggestions = self.llm_agent.generate_suggestions(
            prompt=workflow_description,
            temperature=0.7,  # Lower temperature for more focused suggestions
        )
        return suggestions

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
