from typing import Dict, List, Any, Optional
import json
import uuid

from model.Tuple import Tuple
from model.DataSchema import DataSchema, Attribute, AttributeType


class SuggestionGenerator:
    """
    Class responsible for generating workflow suggestions
    """
    
    def __init__(self):
        """Initialize the suggestion generator"""
        pass
        
    def generate_suggestions(
        self,
        workflow_json: Dict[str, Any],
        compilation_state: Dict[str, Any],
        result_tables: Dict[str, Dict[str, Any]]
    ) -> List[Dict[str, Any]]:
        """
        Generate workflow suggestions based on the current workflow,
        compilation state, and result tables.
        
        Args:
            workflow_json: The workflow as a parsed JSON object
            compilation_state: Compilation state information
            result_tables: Result tables from operators
            
        Returns:
            A list of workflow suggestions
        """
        # Convert result tables to Tuple objects
        tuples_by_operator = {}
        for op_id, table in result_tables.items():
            tuples = []
            for row in table["rows"]:
                tuples.append(Tuple(row))
            tuples_by_operator[op_id] = tuples
        
        # Here you would implement actual suggestion logic
        # For now, return mock suggestions
        
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
            
            # Add mock suggestion 2: Replace ScanSource with CSVFileScan
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