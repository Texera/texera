import json
import os
from typing import Dict, List, Any, Optional
from dotenv import load_dotenv

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from model.llm.suggestion import SuggestionList
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
suggestion_generator = SuggestionGenerator()


# Input models
class SchemaAttribute(BaseModel):
    attributeName: str
    attributeType: str


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


@app.post("/api/workflow-suggestion", response_model=SuggestionList)
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

        # Generate suggestions using the suggestion engine
        suggestions = suggestion_generator.generate_suggestions(
            workflow_json,
            compilation_state_dict,
            result_tables_dict,
            execution_state_dict,
        )

        return suggestions

    except json.JSONDecodeError:
        raise HTTPException(status_code=400, detail="Invalid workflow JSON format")
    except Exception as e:
        raise HTTPException(
            status_code=500, detail=f"Error generating suggestions: {str(e)}"
        )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=9094)
