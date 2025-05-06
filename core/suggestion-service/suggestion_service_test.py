"""
Workflow Interpretation and Suggestion CLI Utility
"""

import json
import os
from typing import Dict, Any
from pathlib import Path

from dotenv import load_dotenv

from workflow_interpretation.interpreter import (
    WorkflowInterpreter,
    InterpretationMethod,
)
from suggestion_engine.generator import SuggestionGenerator

# Base directory for test data and results
DATA_DIR = Path("test/data")
RESULTS_DIR = Path("test/results")
load_dotenv()


def load_workflow_data(dir_name: str) -> Dict[str, Any]:
    """
    Load workflow-related JSON files from a given directory.

    Args:
        dir_name: Subdirectory name under test/data

    Returns:
        Dict containing all loaded workflow components.
    """
    path = DATA_DIR / dir_name

    return {
        "workflow_json": json.load(open(path / "workflow.json")),
        "compilation_state": json.load(open(path / "workflow_compilation_state.json")),
        "result_tables": json.load(open(path / "result_tables.json")),
        "execution_state": json.load(open(path / "execution_state.json")),
    }


def interpret_workflow(dir_name: str):
    """
    Run interpretation on a workflow and save the output.

    Args:
        dir_name: Workflow folder name
    """
    print(f"\n🧠 Interpreting workflow: {dir_name}")

    data = load_workflow_data(dir_name)
    interpreter = WorkflowInterpreter()
    schema_map = data["compilation_state"].get("operatorInputSchemaMap", {})
    errors = data["compilation_state"].get("operatorErrors", {})

    result_dir = RESULTS_DIR / dir_name
    os.makedirs(result_dir, exist_ok=True)

    # RAW
    raw_text = interpreter.interpret_workflow(
        data["workflow_json"], schema_map, errors, InterpretationMethod.RAW
    )
    with open(result_dir / "raw_interpretation.txt", "w") as f:
        f.write(raw_text)

    print("\n📄 Raw interpretation saved.")

    # BY_PATH
    by_path_text = interpreter.interpret_workflow(
        data["workflow_json"], schema_map, errors, InterpretationMethod.BY_PATH
    )
    with open(result_dir / "by_path_interpretation.txt", "w") as f:
        f.write(by_path_text)

    print("📄 By-path interpretation saved.")


def generate_suggestions(dir_name: str):
    """
    Generate suggestions from a workflow.

    Args:
        dir_name: Workflow folder name
    """
    print(f"\n💡 Generating suggestions for: {dir_name}")

    data = load_workflow_data(dir_name)
    generator = SuggestionGenerator()

    result_dir = RESULTS_DIR / dir_name
    os.makedirs(result_dir, exist_ok=True)

    suggestions = generator.generate_suggestions(
        data["workflow_json"],
        data["compilation_state"],
        data["result_tables"],
        data["execution_state"],
    )

    output_file = result_dir / "suggestions.json"
    with open(output_file, "w") as f:
        f.write(suggestions.model_dump_json(indent=2))

    print(f"✅ {len(suggestions.suggestions)} suggestions written to {output_file}")


def run_all():
    """Run interpretation and suggestion on all workflows."""
    workflows = ["workflow2"]
    os.makedirs(RESULTS_DIR, exist_ok=True)

    for dir_name in workflows:
        try:
            print(f"\n{'='*60}")
            print(f"▶ Running for workflow: {dir_name}")
            print(f"{'='*60}")
            interpret_workflow(dir_name)
            generate_suggestions(dir_name)
        except Exception as e:
            print(f"❌ Error in {dir_name}: {e}")
            import traceback

            traceback.print_exc()


if __name__ == "__main__":
    run_all()
