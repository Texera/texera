import json
import os
import sys
from typing import Dict, Any

from model.texera.TexeraWorkflow import TexeraWorkflow


def load_test_workflow(workflow_name: str) -> Dict[str, Any]:
    """
    Load a test workflow from the test/data/{workflow_name} directory.

    Args:
        workflow_name: Name of the workflow folder

    Returns:
        Dictionary containing the workflow
    """
    # Construct the path to the workflow file
    workflow_file = os.path.join("test", "data", workflow_name, "workflow.json")

    # Load the workflow from the file
    with open(workflow_file, "r") as f:
        return json.load(f)


def debug_workflow(workflow_name: str) -> None:
    """
    Debug a workflow's structure.

    Args:
        workflow_name: Name of the workflow folder
    """
    print(f"Debugging workflow: {workflow_name}")

    # Load the workflow
    workflow_dict = load_test_workflow(workflow_name)

    # Create a TexeraWorkflow object
    texera_workflow = TexeraWorkflow(workflow_dict=workflow_dict)

    # Debug the workflow structure
    print("\n1. Examining workflow dictionary structure:")
    print(f"   - keys: {list(workflow_dict.keys())}")

    if "content" in workflow_dict:
        print(f"   - content keys: {list(workflow_dict['content'].keys())}")

        if "operators" in workflow_dict["content"]:
            operators = workflow_dict["content"]["operators"]
            print(f"   - number of operators: {len(operators)}")
            if operators:
                print(f"   - first operator keys: {list(operators[0].keys())}")

        if "links" in workflow_dict["content"]:
            links = workflow_dict["content"]["links"]
            print(f"   - number of links: {len(links)}")
            if links:
                print(f"   - first link keys: {list(links[0].keys())}")

    # Debug the DAG
    print("\n2. Examining DAG structure:")
    print(f"   - number of nodes: {len(texera_workflow.DAG.nodes())}")
    print(f"   - number of edges: {len(texera_workflow.DAG.edges())}")

    # Print all paths
    print("\n3. All paths through the workflow:")
    paths = texera_workflow.get_all_paths()
    print(f"   - number of paths: {len(paths)}")
    for i, path in enumerate(paths):
        print(f"   Path {i+1}: {path}")

    # Check a path workflow if paths exist
    if paths:
        print("\n4. Examining a path workflow:")
        path_workflow = texera_workflow.extract_path_workflow(paths[0])

        print(
            f"   - path workflow dict keys: {list(path_workflow.workflow_dict.keys())}"
        )
        if "content" in path_workflow.workflow_dict:
            content = path_workflow.workflow_dict["content"]
            print(f"   - content keys: {list(content.keys())}")

            if "operators" in content:
                operators = content["operators"]
                print(f"   - number of operators: {len(operators)}")
                if operators:
                    print(f"   - first operator type: {type(operators[0])}")
                    print(
                        f"   - first operator keys: {list(operators[0].keys() if isinstance(operators[0], dict) else [])}"
                    )

            if "links" in content:
                links = content["links"]
                print(f"   - number of links: {len(links)}")
                if links:
                    print(f"   - first link type: {type(links[0])}")
                    print(
                        f"   - first link keys: {list(links[0].keys() if isinstance(links[0], dict) else [])}"
                    )


if __name__ == "__main__":
    # Get the workflow name from command line arguments
    if len(sys.argv) < 2:
        print("Usage: python test_debug_workflow.py <workflow_name>")
        sys.exit(1)

    workflow_name = sys.argv[1]
    debug_workflow(workflow_name)
