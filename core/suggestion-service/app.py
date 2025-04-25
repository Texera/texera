import json
import os
from typing import Dict, List, Any, Optional
from dotenv import load_dotenv

from fastapi import FastAPI, HTTPException, Depends
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from suggestion_engine.generator import SuggestionGenerator

# Load environment variables
load_dotenv()

app = FastAPI(title="Texera Workflow Suggestion Service")

# Enable CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # In production, restrict this to your frontend domain
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Get LLM configuration from environment variables
LLM_PROVIDER = os.environ.get("LLM_PROVIDER", "openai")
LLM_MODEL = os.environ.get(
    "OPENAI_MODEL" if LLM_PROVIDER == "openai" else "ANTHROPIC_MODEL", None
)
MAX_SUGGESTIONS = int(os.environ.get("MAX_SUGGESTIONS", "3"))

# Initialize the suggestion generator with LLM support
suggestion_generator = SuggestionGenerator(
    llm_provider=LLM_PROVIDER, llm_model=LLM_MODEL
)


# Input models
class SchemaAttribute(BaseModel):
    attributeName: str
    attributeType: str


class Position(BaseModel):
    x: float
    y: float


class OperatorToAdd(BaseModel):
    operatorType: str
    position: Position
    properties: Optional[Dict[str, Any]] = None
    customDisplayName: Optional[str] = None


class OperatorPropertyChange(BaseModel):
    operatorId: str
    properties: Dict[str, Any]


class Link(BaseModel):
    source: Dict[str, str]
    target: Dict[str, str]


class WorkflowSuggestion(BaseModel):
    id: str
    description: str
    operatorsToAdd: List[OperatorToAdd]
    operatorPropertiesToChange: List[OperatorPropertyChange]
    operatorsToDelete: List[str]
    linksToAdd: List[Link]
    isPreviewActive: bool = False


class PhysicalPlan(BaseModel):
    # Simplified physical plan model
    operators: Dict[str, Any] = {}
    links: List[Dict[str, Any]] = []


class CompilationStateInfo(BaseModel):
    state: str
    physicalPlan: Optional[PhysicalPlan] = None
    operatorInputSchemaMap: Optional[
        Dict[str, List[Optional[List[SchemaAttribute]]]]
    ] = None
    operatorErrors: Optional[Dict[str, Any]] = None


class ExecutionStateInfo(BaseModel):
    state: str
    currentTuples: Optional[Dict[str, Any]] = None
    errorMessages: Optional[List[Dict[str, Any]]] = None


class ResultTable(BaseModel):
    rows: List[Dict[str, Any]]
    columnNames: List[str]


class SuggestionRequest(BaseModel):
    workflow: str = Field(..., description="JSON string of the workflow")
    compilationState: CompilationStateInfo
    executionState: Optional[ExecutionStateInfo] = None
    resultTables: Dict[str, ResultTable]
    maxSuggestions: Optional[int] = MAX_SUGGESTIONS


class LLMConfig(BaseModel):
    provider: str = LLM_PROVIDER
    model: Optional[str] = LLM_MODEL


@app.get("/")
async def root():
    return {"message": "Texera Workflow Suggestion Service is running"}


@app.get("/api/config")
async def get_config():
    """Get the current configuration."""
    return {
        "llm": {"provider": LLM_PROVIDER, "model": LLM_MODEL},
        "maxSuggestions": MAX_SUGGESTIONS,
    }


@app.post("/api/workflow-suggestion", response_model=List[WorkflowSuggestion])
async def generate_suggestions(request: SuggestionRequest):
    """
    Generate workflow suggestions based on the current workflow, compilation state, and result tables.

    Args:
        request: Contains workflow as JSON string, compilation state info, execution state info, and result tables by operator ID

    Returns:
        A list of workflow suggestions
    """
    try:
        # Parse the workflow JSON
        print("Received suggestion request")
        workflow_json = json.loads(request.workflow)

        # Convert Pydantic models to dictionaries for the suggestion generator
        compilation_state_dict = request.compilationState.model_dump()
        result_tables_dict = {
            op_id: table.model_dump() for op_id, table in request.resultTables.items()
        }

        # Include execution state if available
        execution_state_dict = (
            request.executionState.model_dump() if request.executionState else None
        )

        # Create a data directory if it doesn't exist
        os.makedirs("test/data", exist_ok=True)

        # Save the workflow data as JSON files
        with open("test/data/workflow.json", "w") as f:
            json.dump(workflow_json, f, indent=2)

        with open("test/data/workflow_compilation_state.json", "w") as f:
            json.dump(compilation_state_dict, f, indent=2)

        with open("test/data/result_tables.json", "w") as f:
            json.dump(result_tables_dict, f, indent=2)

        if execution_state_dict:
            with open("test/data/execution_state.json", "w") as f:
                json.dump(execution_state_dict, f, indent=2)

        # Generate suggestions using the suggestion engine
        suggestions = suggestion_generator.generate_suggestions(
            workflow_json,
            compilation_state_dict,
            result_tables_dict,
            execution_state_dict,
            max_suggestions=request.maxSuggestions or MAX_SUGGESTIONS,
        )

        # Convert the dictionaries to WorkflowSuggestion objects
        workflow_suggestions = []
        for suggestion in suggestions:
            workflow_suggestions.append(WorkflowSuggestion(**suggestion))

        return workflow_suggestions

    except json.JSONDecodeError:
        raise HTTPException(status_code=400, detail="Invalid workflow JSON format")
    except Exception as e:
        raise HTTPException(
            status_code=500, detail=f"Error generating suggestions: {str(e)}"
        )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=9094)
