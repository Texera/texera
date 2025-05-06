import json
import os
from dotenv import load_dotenv

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from model.llm.suggestion import SuggestionList
from model.web.input import SuggestionRequest
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

# @app.middleware("http")
# async def log_raw_body(request: Request, call_next):
#     body = await request.body()
#     print("🔥 RAW REQUEST BODY:", body.decode())
#     response = await call_next(request)
#     return response


@app.get("/")
async def root():
    return {"message": "Texera Workflow Suggestion Service is running"}


@app.post("/api/workflow-suggestion", response_model=SuggestionList)
async def generate_suggestions(request: SuggestionRequest):
    """
    Generate workflow suggestions based on the current workflow, compilation state, and execution state.
    """
    try:
        # Parse the workflow JSON
        workflow_json = json.loads(request.workflow)

        # Convert Pydantic models to dictionaries for the suggestion generator
        compilation_state_dict = request.compilationState.model_dump()

        # Include execution state if available
        execution_state_dict = (
            request.executionState.model_dump() if request.executionState else None
        )

        # Generate suggestions using the suggestion engine
        suggestions = suggestion_generator.generate_suggestions(
            workflow_json,
            compilation_state_dict,
            {},  # send empty resultTables
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
