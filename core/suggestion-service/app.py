import json
from typing import Dict, List, Any, Optional

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from suggestion_engine.generator import SuggestionGenerator

app = FastAPI(title="Texera Workflow Suggestion Service")

# Initialize the suggestion generator
suggestion_generator = SuggestionGenerator()

# Enable CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # In production, restrict this to your frontend domain
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
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
    operatorInputSchemaMap: Optional[Dict[str, List[Optional[List[SchemaAttribute]]]]] = None
    operatorErrors: Optional[Dict[str, Any]] = None

class ResultTable(BaseModel):
    rows: List[Dict[str, Any]]
    columnNames: List[str]

class SuggestionRequest(BaseModel):
    workflow: str = Field(..., description="JSON string of the workflow")
    compilationState: CompilationStateInfo
    resultTables: Dict[str, ResultTable]

@app.get("/")
async def root():
    return {"message": "Texera Workflow Suggestion Service is running"}

@app.post("/api/suggest", response_model=List[WorkflowSuggestion])
async def generate_suggestions(request: SuggestionRequest):
    """
    Generate workflow suggestions based on the current workflow, compilation state, and result tables.
    
    Args:
        request: Contains workflow as JSON string, compilation state info, and result tables by operator ID
        
    Returns:
        A list of workflow suggestions
    """
    try:
        # Parse the workflow JSON
        workflow_json = json.loads(request.workflow)
        
        # Convert Pydantic models to dictionaries for the suggestion generator
        compilation_state_dict = request.compilationState.dict()
        result_tables_dict = {op_id: table.dict() for op_id, table in request.resultTables.items()}
        
        # Generate suggestions using the suggestion engine
        suggestions = suggestion_generator.generate_suggestions(
            workflow_json,
            compilation_state_dict,
            result_tables_dict
        )
        
        # Convert the dictionaries to WorkflowSuggestion objects
        workflow_suggestions = []
        for suggestion in suggestions:
            workflow_suggestions.append(WorkflowSuggestion(**suggestion))
        
        return workflow_suggestions
        
    except json.JSONDecodeError:
        raise HTTPException(status_code=400, detail="Invalid workflow JSON format")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error generating suggestions: {str(e)}")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000) 