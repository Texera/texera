"""
Test script for workflow interpretation using real workflow data.
"""
import json
import os
from typing import Dict, Any, Optional
from pathlib import Path

from workflow_interpretation.interpreter import WorkflowInterpreter, InterpretationMethod
from suggestion_engine.generator import SuggestionGenerator

# Base directory for test data
TEST_DATA_DIR = Path("test/data")
TEST_RESULTS_DIR = Path("test/results")

def load_test_data(workflow_dir: str) -> Dict[str, Any]:
    """
    Load test data from the specified workflow directory.
    
    Args:
        workflow_dir: Name of the workflow directory
        
    Returns:
        Dictionary containing loaded test data
    """
    base_path = TEST_DATA_DIR / workflow_dir
    
    # Load workflow data
    with open(base_path / "workflow.json", "r") as f:
        workflow_json = json.load(f)
    
    # Load compilation state
    with open(base_path / "workflow_compilation_state.json", "r") as f:
        compilation_state = json.load(f)
    
    # Load result tables (might be empty)
    with open(base_path / "result_tables.json", "r") as f:
        result_tables = json.load(f)
    
    # Load execution state
    with open(base_path / "execution_state.json", "r") as f:
        execution_state = json.load(f)
    
    return {
        "workflow_json": workflow_json,
        "compilation_state": compilation_state,
        "result_tables": result_tables,
        "execution_state": execution_state
    }

def test_workflow_interpretation(workflow_dir: str):
    """
    Test workflow interpretation with data from the specified workflow directory.
    
    Args:
        workflow_dir: Name of the workflow directory
    """
    print(f"\n===== Testing interpretation for {workflow_dir} =====")
    
    # Load test data
    test_data = load_test_data(workflow_dir)
    
    # Create interpreter
    interpreter = WorkflowInterpreter()
    
    # Get operator input schema map and errors from compilation state
    operator_input_schema_map = test_data["compilation_state"].get("operatorInputSchemaMap", {})
    operator_errors = test_data["compilation_state"].get("operatorErrors", {})
    
    # Create results directory for this workflow
    result_dir = TEST_RESULTS_DIR / workflow_dir
    os.makedirs(result_dir, exist_ok=True)
    
    # Test raw interpretation
    raw_description = interpreter.interpret_workflow(
        test_data["workflow_json"],
        operator_input_schema_map,
        operator_errors,
        InterpretationMethod.RAW
    )
    
    # Save raw interpretation to file
    with open(result_dir / "raw_interpretation.txt", "w") as f:
        f.write(raw_description)
    
    print(f"\n--- {workflow_dir} RAW Interpretation (truncated) ---")
    # Print just the first few lines to avoid overwhelming output
    print("\n".join(raw_description.split("\n")[:20]) + "\n... (truncated)")
    
    # Test by-path interpretation
    by_path_description = interpreter.interpret_workflow(
        test_data["workflow_json"],
        operator_input_schema_map,
        operator_errors,
        InterpretationMethod.BY_PATH
    )
    
    # Save by-path interpretation to file
    with open(result_dir / "by_path_interpretation.txt", "w") as f:
        f.write(by_path_description)
    
    print(f"\n--- {workflow_dir} BY_PATH Interpretation ---")
    print(by_path_description)
    
    print(f"\nInterpretation results saved to {result_dir}/")
    
    return True

def test_suggestion_generation(workflow_dir: str):
    """
    Test suggestion generation with data from the specified workflow directory.
    
    Args:
        workflow_dir: Name of the workflow directory
    """
    print(f"\n===== Testing suggestion generation for {workflow_dir} =====")
    
    # Load test data
    test_data = load_test_data(workflow_dir)
    
    # Create suggestion generator
    suggestion_generator = SuggestionGenerator()
    
    # Create results directory for this workflow
    result_dir = TEST_RESULTS_DIR / workflow_dir
    os.makedirs(result_dir, exist_ok=True)
    
    # Generate suggestions
    suggestions = suggestion_generator.generate_suggestions(
        test_data["workflow_json"],
        test_data["compilation_state"],
        test_data["result_tables"],
        test_data["execution_state"]
    )
    
    # Save suggestions to file
    with open(result_dir / "suggestions.json", "w") as f:
        json.dump(suggestions, f, indent=2)
    
    print(f"\nGenerated {len(suggestions)} suggestions:")
    for i, suggestion in enumerate(suggestions, 1):
        print(f"\n{i}. {suggestion['description']}")
        print(f"   - Operators to add: {len(suggestion['operatorsToAdd'])}")
        print(f"   - Properties to change: {len(suggestion['operatorPropertiesToChange'])}")
        print(f"   - Operators to delete: {len(suggestion['operatorsToDelete'])}")
        print(f"   - Links to add: {len(suggestion['linksToAdd'])}")
    
    print(f"\nSuggestion results saved to {result_dir}/suggestions.json")
    
    return True

def main():
    """Run tests with all workflow data."""
    workflow_dirs = ["workflow1", "workflow2", "workflow3"]
    
    # Ensure results directory exists
    os.makedirs(TEST_RESULTS_DIR, exist_ok=True)
    
    for workflow_dir in workflow_dirs:
        try:
            print(f"\n{'='*50}")
            print(f"Testing workflow: {workflow_dir}")
            print(f"{'='*50}")
            
            # Test workflow interpretation
            test_workflow_interpretation(workflow_dir)
            
            # Test suggestion generation
            test_suggestion_generation(workflow_dir)
            
        except Exception as e:
            print(f"Error testing {workflow_dir}: {str(e)}")
            import traceback
            traceback.print_exc()

if __name__ == "__main__":
    main() 