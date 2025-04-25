"""
Test script for workflow interpretation.
"""

import json
from typing import Dict, Any

from workflow_interpretation.interpreter import (
    WorkflowInterpreter,
    InterpretationMethod,
)

# Sample workflow
SAMPLE_WORKFLOW = {
    "id": "test-workflow",
    "name": "Test Workflow",
    "content": {
        "operators": [
            {
                "operatorID": "source-1",
                "operatorType": "ScanSource",
                "properties": {"tableName": "employee", "limit": 100},
            },
            {
                "operatorID": "filter-1",
                "operatorType": "Filter",
                "properties": {
                    "condition": {
                        "attributeName": "salary",
                        "comparisonType": "greaterThan",
                        "value": "50000",
                    }
                },
            },
            {
                "operatorID": "sink-1",
                "operatorType": "ViewResults",
                "properties": {"limit": 10, "offset": 0},
            },
        ],
        "links": [
            {
                "linkID": "link-1",
                "source": {"operatorID": "source-1", "portID": "output-0"},
                "target": {"operatorID": "filter-1", "portID": "input-0"},
            },
            {
                "linkID": "link-2",
                "source": {"operatorID": "filter-1", "portID": "output-0"},
                "target": {"operatorID": "sink-1", "portID": "input-0"},
            },
        ],
    },
}

# Sample input schema
SAMPLE_INPUT_SCHEMA = {
    "source-1": [],  # No input schema for source operators
    "filter-1": [
        [
            {"attributeName": "id", "attributeType": "integer"},
            {"attributeName": "name", "attributeType": "string"},
            {"attributeName": "salary", "attributeType": "double"},
            {"attributeName": "department", "attributeType": "string"},
        ]
    ],
    "sink-1": [
        [
            {"attributeName": "id", "attributeType": "integer"},
            {"attributeName": "name", "attributeType": "string"},
            {"attributeName": "salary", "attributeType": "double"},
            {"attributeName": "department", "attributeType": "string"},
        ]
    ],
}

# Sample operator errors
SAMPLE_OPERATOR_ERRORS = {}  # No errors for this sample


def test_raw_interpretation():
    """Test the RAW interpretation method."""
    interpreter = WorkflowInterpreter()

    try:
        description = interpreter.interpret_workflow(
            SAMPLE_WORKFLOW,
            SAMPLE_INPUT_SCHEMA,
            SAMPLE_OPERATOR_ERRORS,
            InterpretationMethod.RAW,
        )

        print("\n===== RAW INTERPRETATION =====")
        print(description)
        print("==============================\n")

        return True
    except Exception as e:
        print(f"Error testing RAW interpretation: {str(e)}")
        return False


def test_by_path_interpretation():
    """Test the BY_PATH interpretation method."""
    interpreter = WorkflowInterpreter()

    try:
        description = interpreter.interpret_workflow(
            SAMPLE_WORKFLOW,
            SAMPLE_INPUT_SCHEMA,
            SAMPLE_OPERATOR_ERRORS,
            InterpretationMethod.BY_PATH,
        )

        print("\n===== BY_PATH INTERPRETATION =====")
        print(description)
        print("==================================\n")

        return True
    except Exception as e:
        print(f"Error testing BY_PATH interpretation: {str(e)}")
        return False


if __name__ == "__main__":
    print("Testing workflow interpretation...")

    # Test RAW interpretation
    raw_result = test_raw_interpretation()

    # Test BY_PATH interpretation
    by_path_result = test_by_path_interpretation()

    if raw_result and by_path_result:
        print("All tests passed!")
    else:
        print("Some tests failed.")
