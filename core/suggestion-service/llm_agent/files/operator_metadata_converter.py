import json
import uuid
from typing import Dict, Any, List
import copy

# Load the operator metadata file
with open("operator_json_schema.json", "r") as f:
    operator_metadata = json.load(f)


def resolve_ref(ref: str, definitions: Dict[str, Any]) -> Dict[str, Any]:
    try:
        ref_path = ref.lstrip("#/").split("/")
        resolved = definitions
        for part in ref_path:
            resolved = resolved.get(part, {})
        return resolved
    except Exception:
        return {}


def fill_defaults(schema: Dict[str, Any], definitions: Dict[str, Any]) -> Dict[str, Any]:
    """
    Fills default values into a schema-defined object.
    """
    if "$ref" in schema:
        schema = resolve_ref(schema["$ref"], definitions)

    if schema.get("type") == "object":
        obj = {}
        for prop, subschema in schema.get("properties", {}).items():
            obj[prop] = fill_defaults(subschema, definitions)
        return obj
    elif schema.get("type") == "array":
        return []
    elif "default" in schema:
        return copy.deepcopy(schema["default"])
    return None


def convert_to_operator_predicate(schema: Dict[str, Any]) -> Dict[str, Any]:
    operator_type = schema["operatorType"]
    metadata = schema["additionalMetadata"]
    json_schema = schema["jsonSchema"]
    definitions = json_schema.get("definitions", {})

    operator_id = f"{operator_type}-operator-{uuid.uuid4()}"
    operator_properties = fill_defaults(json_schema, definitions)

    input_ports = []
    for i, port_info in enumerate(metadata.get("inputPorts", [])):
        input_ports.append({
            "portID": f"input-{i}",
            "displayName": port_info.get("displayName", ""),
            "allowMultiInputs": port_info.get("allowMultiLinks", False),
            "isDynamicPort": False,
            "dependencies": port_info.get("dependencies", [])
        })

    output_ports = []
    for i, port_info in enumerate(metadata.get("outputPorts", [])):
        output_ports.append({
            "portID": f"output-{i}",
            "displayName": port_info.get("displayName", ""),
            "allowMultiInputs": False,
            "isDynamicPort": False
        })

    return {
        "operatorID": operator_id,
        "operatorType": operator_type,
        "operatorVersion": schema.get("operatorVersion", "N/A"),
        "operatorProperties": operator_properties,
        "inputPorts": input_ports,
        "outputPorts": output_ports,
        "dynamicInputPorts": metadata.get("dynamicInputPorts", False),
        "dynamicOutputPorts": metadata.get("dynamicOutputPorts", False),
        "showAdvanced": False,
        "isDisabled": False,
        "customDisplayName": metadata.get("userFriendlyName", operator_type)
    }


# Convert all schemas
operator_predicates: List[Dict[str, Any]] = [
    convert_to_operator_predicate(schema)
    for schema in operator_metadata["operators"]
]

# Save result to file
output_path = "operator_format.json"
with open(output_path, "w") as f:
    json.dump(operator_predicates, f, indent=2)