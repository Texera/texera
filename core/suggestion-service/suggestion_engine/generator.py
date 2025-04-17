from typing import Dict, List, Any, Optional
import json
import uuid

from workflow_interpretation.interpreter import WorkflowInterpreter, InterpretationMethod
from model.Tuple import Tuple
from model.DataSchema import DataSchema, Attribute, AttributeType


class SuggestionGenerator:
    """
    SuggestionGenerator is responsible for generating workflow suggestions
    based on the current workflow state, compilation information, and result data.
    """
    
    def __init__(self):
        """
        Initialize the suggestion generator.
        """
        self.workflow_interpreter = WorkflowInterpreter()
        
    def generate_suggestions(
        self,
        workflow_json: Dict[str, Any],
        compilation_state: Dict[str, Any],
        result_tables: Dict[str, Dict[str, Any]],
        execution_state: Optional[Dict[str, Any]] = None
    ) -> List[Dict[str, Any]]:
        """
        Generate workflow suggestions based on the current workflow, compilation state, execution state, and result tables.
        
        Args:
            workflow_json: The current workflow configuration
            compilation_state: Compilation information and errors
            result_tables: Result data for each operator
            execution_state: Current execution state of the workflow
            
        Returns:
            A list of workflow suggestions
        """
        # For debugging purposes
        print(f"Generating suggestions for workflow with {len(workflow_json.get('content', {}).get('operators', []))} operators")
        print(f"Compilation state: {compilation_state['state']}")
        if execution_state:
            print(f"Execution state: {execution_state['state']}")
        print(f"Result tables available for {len(result_tables)} operators")
        
        # Generate natural language description of the workflow
        workflow_description = self._generate_workflow_prompt(
            workflow_json, 
            compilation_state.get("operatorInputSchemaMap"), 
            compilation_state.get("operatorErrors")
        )
        
        print("Generated workflow description:")
        print(workflow_description)
        
        # Extract operators from the workflow
        operators = workflow_json.get("content", {}).get("operators", [])
        
        # Create a list to store suggestions
        suggestions = []
        
        # Add suggestions based on the workflow state
        if compilation_state["state"] == "Succeeded":
            # Add mock suggestion 1: Add a KeywordSearch operator
            suggestion1 = {
                "id": f"suggestion-{uuid.uuid4()}",
                "description": "Add a KeywordSearch operator with sentiment analysis",
                "operatorsToAdd": [
                    {
                        "operatorType": "KeywordSearch",
                        "position": {"x": 400, "y": 300},
                        "properties": {"keyword": "climate change", "attributes": ["content", "title"]}
                    },
                    {
                        "operatorType": "SentimentAnalysis",
                        "position": {"x": 600, "y": 300},
                        "properties": {"attribute": "content", "resultAttribute": "sentiment"}
                    }
                ],
                "operatorPropertiesToChange": [
                    {
                        "operatorId": "View-Results-1",
                        "properties": {"limit": 20, "offset": 0}
                    }
                ],
                "operatorsToDelete": [],
                "linksToAdd": [
                    {
                        "source": {"operatorId": "Source-Scan-1", "portId": "output-0"},
                        "target": {"operatorId": "KeywordSearch-1", "portId": "input-0"}
                    },
                    {
                        "source": {"operatorId": "KeywordSearch-1", "portId": "output-0"},
                        "target": {"operatorId": "SentimentAnalysis-1", "portId": "input-0"}
                    },
                    {
                        "source": {"operatorId": "SentimentAnalysis-1", "portId": "output-0"},
                        "target": {"operatorId": "View-Results-1", "portId": "input-0"}
                    }
                ],
                "isPreviewActive": False
            }
            suggestions.append(suggestion1)
            
            # Add suggestions based on execution state
            if execution_state and execution_state["state"] == "Completed":
                # Add a suggestion for optimization if workflow completed successfully
                suggestion3 = {
                    "id": f"suggestion-{uuid.uuid4()}",
                    "description": "Optimize workflow with projection and sorting",
                    "operatorsToAdd": [
                        {
                            "operatorType": "Projection",
                            "position": {"x": 400, "y": 200},
                            "properties": {"attributes": ["id", "name", "price", "category"]}
                        },
                        {
                            "operatorType": "Sort",
                            "position": {"x": 600, "y": 200},
                            "properties": {
                                "sortAttributesList": [
                                    {
                                        "attributeName": "price",
                                        "order": "desc"
                                    }
                                ]
                            }
                        }
                    ],
                    "operatorPropertiesToChange": [
                        {
                            "operatorId": "Source-Scan-1",
                            "properties": {"tableName": "products", "limit": 1000}
                        }
                    ],
                    "operatorsToDelete": [],
                    "linksToAdd": [
                        {
                            "source": {"operatorId": "Source-Scan-1", "portId": "output-0"},
                            "target": {"operatorId": "Projection-1", "portId": "input-0"}
                        },
                        {
                            "source": {"operatorId": "Projection-1", "portId": "output-0"},
                            "target": {"operatorId": "Sort-1", "portId": "input-0"}
                        },
                        {
                            "source": {"operatorId": "Sort-1", "portId": "output-0"},
                            "target": {"operatorId": "View-Results-1", "portId": "input-0"}
                        }
                    ],
                    "isPreviewActive": False
                }
                suggestions.append(suggestion3)
            
            # If execution state has errors, add suggestions for fixing them
            if execution_state and execution_state["state"] == "Failed" and execution_state.get("errorMessages"):
                suggestion_error_fix = {
                    "id": f"suggestion-{uuid.uuid4()}",
                    "description": "Fix data type issues in workflow",
                    "operatorsToAdd": [
                        {
                            "operatorType": "TypeConversion",
                            "position": {"x": 300, "y": 250},
                            "properties": {"targetType": "string", "attributeToConvert": "id"}
                        }
                    ],
                    "operatorPropertiesToChange": [],
                    "operatorsToDelete": [],
                    "linksToAdd": [
                        {
                            "source": {"operatorId": "Source-Scan-1", "portId": "output-0"},
                            "target": {"operatorId": "TypeConversion-1", "portId": "input-0"}
                        }
                    ],
                    "isPreviewActive": False
                }
                suggestions.append(suggestion_error_fix)
        
        # Add suggestion based on the operator types
        if any(op.get("operatorType") == "CSVFileScan" for op in operators):
            suggestion2 = {
                "id": f"suggestion-{uuid.uuid4()}",
                "description": "Replace ScanSource with CSVFileScan for better performance",
                "operatorsToAdd": [
                    {
                        "operatorType": "CSVFileScan",
                        "position": {"x": 200, "y": 200},
                        "properties": {"fileName": "data.csv", "limit": -1, "offset": 0, "schema": "auto"}
                    }
                ],
                "operatorPropertiesToChange": [],
                "operatorsToDelete": ["Source-Scan-1"],
                "linksToAdd": [
                    {
                        "source": {"operatorId": "CSVFileScan-1", "portId": "output-0"},
                        "target": {"operatorId": "View-Results-1", "portId": "input-0"}
                    }
                ],
                "isPreviewActive": False
            }
            suggestions.append(suggestion2)
        
        return suggestions 

    def _generate_workflow_prompt(
        self,
        workflow_json: Dict[str, Any],
        input_schema: Optional[Dict[str, Any]] = None,
        operator_errors: Optional[Dict[str, Any]] = None,
        method: InterpretationMethod = InterpretationMethod.BY_PATH
    ) -> str:
        """
        Generate a natural language description of the workflow for use in prompts.
        
        Args:
            workflow_json: The workflow dictionary
            input_schema: The input schema dictionary for each operator
            operator_errors: Dictionary of static errors for each operator
            method: The interpretation method to use
            
        Returns:
            A natural language description of the workflow
        """
        try:
            # Use the workflow interpreter to generate a description
            description = self.workflow_interpreter.interpret_workflow(
                workflow_json,
                input_schema,
                operator_errors,
                method
            )
            
            return description
        except Exception as e:
            print(f"Error generating workflow prompt: {str(e)}")
            # Fallback to a simple description if interpretation fails
            return f"Workflow with {len(workflow_json.get('content', {}).get('operators', []))} operators" 