import json
import os
from dotenv import load_dotenv

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from model.llm.suggestion import SuggestionList
from model.web.input import SuggestionRequest
from model.llm.interpretation import InterpretationMethod
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
        # Generate suggestions using the suggestion engine
        suggestions = suggestion_generator.generate_suggestions(
            request.workflow,
            request.compilationState,
            request.executionState,
            request.intention,
            request.focusingOperatorIDs,
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
