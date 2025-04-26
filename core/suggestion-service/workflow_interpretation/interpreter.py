from model.llm.interpretation import RawInterpretation, PathInterpretation
from model.texera.TexeraWorkflow import TexeraWorkflow
from enum import Enum
from typing import Dict, List, Any, Optional
import traceback
import json


class InterpretationMethod(Enum):
    RAW = "raw"
    BY_PATH = "by_path"


class WorkflowInterpreter:
    def __init__(self):
        pass

    def interpret_workflow(
        self,
        workflow: Dict[str, Any],
        input_schema: Optional[Dict[str, Any]] = None,
        operator_errors: Optional[Dict[str, Any]] = None,
        method: InterpretationMethod = InterpretationMethod.BY_PATH,
    ) -> str:
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
        interpretation = RawInterpretation(
            workflow=workflow,
            inputSchema=input_schema,
            operatorStaticErrors=operator_errors,
        )
        return interpretation.model_dump_json(indent=2)

    def _interpret_by_path(
        self,
        workflow: Dict[str, Any],
        input_schema: Optional[Dict[str, Any]] = None,
        operator_errors: Optional[Dict[str, Any]] = None,
    ) -> str:
        try:
            texera_workflow = TexeraWorkflow(
                workflow_dict=workflow,
                input_schema=input_schema or {},
                operator_errors=operator_errors or {},
            )
            paths = texera_workflow.get_all_paths()
            if not paths:
                return json.dumps({"paths": []}, indent=2)

            path_workflows = [
                texera_workflow.extract_path_workflow(path).ToPydantic()
                for path in paths
            ]
            interpretation = PathInterpretation(paths=path_workflows)
            return interpretation.model_dump_json(indent=2)

        except Exception as e:
            stack_trace = traceback.format_exc()
            return f"Error interpreting workflow by path: {str(e)}\n\nStacktrace:\n{stack_trace}"
