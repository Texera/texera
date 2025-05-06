# Input models
from pydantic import BaseModel, Field
from typing import List

from typing import Dict, List, Any, Optional


class SchemaAttribute(BaseModel):
    attributeName: str
    attributeType: str

    class Config:
        extra = "allow"


class PhysicalPlan(BaseModel):
    # operators IS A LIST  ➜ declare it as such
    operators: List[Dict[str, Any]] = Field(default_factory=list)
    links: List[Dict[str, Any]] = Field(default_factory=list)


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


class SuggestionRequest(BaseModel):
    workflow: str = Field(..., description="JSON string of the workflow")
    compilationState: CompilationStateInfo
    executionState: Optional[ExecutionStateInfo] = None
