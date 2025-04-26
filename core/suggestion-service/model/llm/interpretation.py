# model/serialization.py
from pydantic import BaseModel
from typing import Dict, List, Optional, Any


class AttributeInterpretation(BaseModel):
    attributeName: str
    attributeType: str


class SchemaInterpretation(BaseModel):
    attributes: List[AttributeInterpretation]


class PortInterpretation(BaseModel):
    portID: str
    inputSchema: SchemaInterpretation


class ErrorInterpretation(BaseModel):
    type: str
    message: str
    details: str


class OperatorInterpretation(BaseModel):
    operatorID: str
    operatorType: str
    customDisplayName: Optional[str]
    operatorProperties: Dict[str, Any]
    error: Optional[ErrorInterpretation]
    inputSchemas: Dict[str, PortInterpretation]


class LinkEndInterpretation(BaseModel):
    operatorID: str
    portID: str


class LinkInterpretation(BaseModel):
    source: LinkEndInterpretation
    target: LinkEndInterpretation


class WorkflowInterpretation(BaseModel):
    operators: Dict[str, OperatorInterpretation]
    links: List[LinkInterpretation]


class PathInterpretation(BaseModel):
    paths: List[WorkflowInterpretation]


class RawInterpretation(BaseModel):
    workflow: Dict[str, Any]
    inputSchema: Optional[Dict[str, Any]]
    operatorStaticErrors: Optional[Dict[str, Any]]
