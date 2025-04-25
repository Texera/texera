import json
import requests

# Define the API endpoint URL
API_URL = "http://localhost:8000/api/suggest"

# Sample workflow JSON
workflow_json = {
    "operators": {
        "Source-Scan-1": {
            "operatorID": "Source-Scan-1",
            "operatorType": "SourceScan",
            "operatorProperties": {"tableName": "users", "limit": 100},
            "inputPorts": [],
            "outputPorts": [{"portID": "output-0", "displayName": "output"}],
        },
        "View-Results-1": {
            "operatorID": "View-Results-1",
            "operatorType": "ViewResults",
            "operatorProperties": {},
            "inputPorts": [{"portID": "input-0", "displayName": "input"}],
            "outputPorts": [],
        },
    },
    "links": [
        {
            "linkID": "link-1",
            "source": {"operatorID": "Source-Scan-1", "portID": "output-0"},
            "target": {"operatorID": "View-Results-1", "portID": "input-0"},
        }
    ],
    "operatorPositions": {
        "Source-Scan-1": {"x": 100, "y": 100},
        "View-Results-1": {"x": 400, "y": 100},
    },
}

# Sample compilation state
compilation_state = {
    "state": "Succeeded",
    "physicalPlan": {
        "operators": {
            "Source-Scan-1": {
                "operatorID": "Source-Scan-1",
                "operatorType": "SourceScan",
            },
            "View-Results-1": {
                "operatorID": "View-Results-1",
                "operatorType": "ViewResults",
            },
        },
        "links": [
            {
                "fromOpID": "Source-Scan-1",
                "fromPortID": "output-0",
                "toOpID": "View-Results-1",
                "toPortID": "input-0",
            }
        ],
    },
    "operatorInputSchemaMap": {
        "View-Results-1": [
            [
                {"attributeName": "id", "attributeType": "long"},
                {"attributeName": "name", "attributeType": "string"},
                {"attributeName": "email", "attributeType": "string"},
                {"attributeName": "age", "attributeType": "integer"},
            ]
        ]
    },
}

# Sample result tables
result_tables = {
    "View-Results-1": {
        "rows": [
            {"id": 1, "name": "John Doe", "email": "john@example.com", "age": 30},
            {"id": 2, "name": "Jane Smith", "email": "jane@example.com", "age": 25},
            {"id": 3, "name": "Bob Johnson", "email": "bob@example.com", "age": 40},
        ],
        "columnNames": ["id", "name", "email", "age"],
    }
}

# Prepare the request payload
request_data = {
    "workflow": json.dumps(workflow_json),
    "compilationState": compilation_state,
    "resultTables": result_tables,
}

# Send the request
try:
    response = requests.post(API_URL, json=request_data)

    # Print the response
    if response.status_code == 200:
        print("Suggestions received:")
        suggestions = response.json()
        for i, suggestion in enumerate(suggestions, 1):
            print(f"\nSuggestion {i}: {suggestion['description']}")
            print(f"  Operators to add: {len(suggestion['operatorsToAdd'])}")
            print(
                f"  Properties to change: {len(suggestion['operatorPropertiesToChange'])}"
            )
            print(f"  Operators to delete: {len(suggestion['operatorsToDelete'])}")
            print(f"  Links to add: {len(suggestion['linksToAdd'])}")
    else:
        print(f"Error: {response.status_code}")
        print(response.text)
except Exception as e:
    print(f"Error sending request: {e}")

"""
To run this test:
1. Start the suggestion service: python app.py
2. In another terminal, run this script: python test_request.py
"""
