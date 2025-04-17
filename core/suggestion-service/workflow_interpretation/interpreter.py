"""
Workflow Interpreter module for generating descriptions of workflows.
"""
from typing import Dict, List, Any, Optional, Tuple
import json
from enum import Enum

from model.texera.TexeraWorkflow import TexeraWorkflow


class InterpretationMethod(Enum):
    """Enum for different workflow interpretation methods."""
    RAW = "raw"
    BY_PATH = "by_path"


class WorkflowInterpreter:
    """
    WorkflowInterpreter is responsible for interpreting a workflow and its related information
    (e.g., schema, errors) into a natural language description that can be used
    by the LLM agent for generating suggestions.
    """
    
    def __init__(self):
        """Initialize the interpreter."""
        pass
    
    def interpret_workflow(
        self,
        workflow: Dict[str, Any],
        input_schema: Optional[Dict[str, Any]] = None,
        operator_errors: Optional[Dict[str, Any]] = None,
        method: InterpretationMethod = InterpretationMethod.BY_PATH
    ) -> str:
        """
        Interpret the workflow and generate a natural language description.
        
        Args:
            workflow: The workflow dictionary
            input_schema: The input schema dictionary for each operator
            operator_errors: Dictionary of static errors for each operator
            method: The interpretation method to use
            
        Returns:
            A natural language description of the workflow
        """
        if method == InterpretationMethod.RAW:
            return self._interpret_raw(workflow, input_schema, operator_errors)
        elif method == InterpretationMethod.BY_PATH:
            return self._interpret_by_path(workflow, input_schema, operator_errors)
        else:
            raise ValueError(f"Unsupported interpretation method: {method}")
    
    def _interpret_raw(
        self,
        workflow: Dict[str, Any],
        input_schema: Optional[Dict[str, Any]] = None,
        operator_errors: Optional[Dict[str, Any]] = None
    ) -> str:
        """
        Generate a raw description of the workflow using a simple template.
        
        Args:
            workflow: The workflow dictionary
            input_schema: The input schema dictionary for each operator
            operator_errors: Dictionary of static errors for each operator
            
        Returns:
            A raw description of the workflow
        """
        description = "Here is the workflow dict:\n"
        description += json.dumps(workflow, indent=2)
        
        if input_schema:
            description += "\n\nHere is the input schema for each operator:\n"
            description += json.dumps(input_schema, indent=2)
        
        if operator_errors:
            description += "\n\nHere are the static errors for each operator:\n"
            description += json.dumps(operator_errors, indent=2)
        
        return description
    
    def _interpret_by_path(
        self,
        workflow: Dict[str, Any],
        input_schema: Optional[Dict[str, Any]] = None,
        operator_errors: Optional[Dict[str, Any]] = None
    ) -> str:
        """
        Generate a description of the workflow by extracting and describing paths from source to sink.
        
        Args:
            workflow: The workflow dictionary
            input_schema: The input schema dictionary for each operator
            operator_errors: Dictionary of static errors for each operator
            
        Returns:
            A description of the workflow organized by execution paths
        """
        try:
            # Create a TexeraWorkflow with the dictionary input
            texera_workflow = TexeraWorkflow(workflow_dict=workflow)
            
            # Extract all paths from source to sink
            paths = self._extract_paths(texera_workflow)
            
            # Generate description from paths
            description = "Here are the existing paths in this workflow and related schemas:\n\n"
            
            for i, path in enumerate(paths, 1):
                description += f"Path {i}:\n"
                description += self._describe_path(
                    path, texera_workflow, input_schema, operator_errors)
                description += "\n\n"
            
            return description
        except Exception as e:
            # If path extraction fails, fall back to raw interpretation
            print(f"Error in path interpretation: {str(e)}. Falling back to raw interpretation.")
            return self._interpret_raw(workflow, input_schema, operator_errors)
    
    def _extract_paths(self, workflow: TexeraWorkflow) -> List[List[str]]:
        """
        Extract all paths from source operators to sink operators.
        
        Args:
            workflow: The TexeraWorkflow object
            
        Returns:
            A list of paths, where each path is a list of operator IDs
        """
        # Find source operators (operators with no inputs)
        source_operators = []
        for op_id, operator in workflow.operators.items():
            has_input = False
            for link in workflow.links:
                if link.target.operator_id == op_id:
                    has_input = True
                    break
            if not has_input:
                source_operators.append(op_id)
        
        # Find sink operators (operators with no outputs)
        sink_operators = []
        for op_id, operator in workflow.operators.items():
            has_output = False
            for link in workflow.links:
                if link.source.operator_id == op_id:
                    has_output = True
                    break
            if not has_output:
                sink_operators.append(op_id)
        
        # For each source, find all paths to sinks
        all_paths = []
        for source in source_operators:
            paths = self._find_paths(source, sink_operators, workflow)
            all_paths.extend(paths)
        
        return all_paths
    
    def _find_paths(
        self, 
        current: str, 
        sinks: List[str], 
        workflow: TexeraWorkflow, 
        path: Optional[List[str]] = None, 
        visited: Optional[set] = None
    ) -> List[List[str]]:
        """
        Recursively find all paths from the current operator to any sink operator.
        
        Args:
            current: The current operator ID
            sinks: List of sink operator IDs
            workflow: The TexeraWorkflow object
            path: The current path being built
            visited: Set of visited operator IDs to avoid cycles
            
        Returns:
            A list of paths, where each path is a list of operator IDs
        """
        if path is None:
            path = []
        if visited is None:
            visited = set()
        
        # Add current operator to path and visited
        path = path + [current]
        visited = visited.union({current})
        
        # If current is a sink, we've found a path
        if current in sinks:
            return [path]
        
        # Find all next operators
        next_operators = []
        for link in workflow.links:
            if link.source.operator_id == current:
                next_operators.append(link.target.operator_id)
        
        # Recursively find paths for each next operator
        all_paths = []
        for next_op in next_operators:
            # Avoid cycles
            if next_op not in visited:
                new_paths = self._find_paths(next_op, sinks, workflow, path, visited)
                all_paths.extend(new_paths)
        
        return all_paths
    
    def _describe_path(
        self,
        path: List[str],
        workflow: TexeraWorkflow,
        input_schema: Optional[Dict[str, Any]] = None,
        operator_errors: Optional[Dict[str, Any]] = None
    ) -> str:
        """
        Generate a description of a specific path in the workflow.
        
        Args:
            path: List of operator IDs representing a path
            workflow: The TexeraWorkflow object
            input_schema: The input schema dictionary for each operator
            operator_errors: Dictionary of static errors for each operator
            
        Returns:
            A description of the path
        """
        description = ""
        
        # Describe each operator in the path
        for i, op_id in enumerate(path):
            operator = workflow.operators[op_id]
            description += f"  {i+1}. {operator.operator_type}"
            
            # Add operator properties if available
            if operator.properties:
                description += " with properties:\n"
                for prop_key, prop_value in operator.properties.items():
                    description += f"     - {prop_key}: {prop_value}\n"
            else:
                description += "\n"
            
            # Add schema information if available
            if input_schema and op_id in input_schema:
                schema_info = input_schema[op_id]
                description += "     Schema:\n"
                description += f"     {json.dumps(schema_info, indent=6)}\n"
            
            # Add error information if available
            if operator_errors and op_id in operator_errors:
                error_info = operator_errors[op_id]
                description += "     Errors:\n"
                description += f"     {json.dumps(error_info, indent=6)}\n"
            
            # Add connector information if not the last operator
            if i < len(path) - 1:
                # Find the link between this operator and the next
                next_op_id = path[i + 1]
                for link in workflow.links:
                    if link.source.operator_id == op_id and link.target.operator_id == next_op_id:
                        description += f"     → Connected to {next_op_id} via ports {link.source.port_id} → {link.target.port_id}\n"
                        break
        
        return description 