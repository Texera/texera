"""Formatter for LLM outputs to ensure they match the expected format."""
import json
import re
import uuid
from typing import List, Dict, Any, Optional, Tuple


def format_raw_suggestions(raw_content: str) -> List[Dict[str, Any]]:
    """
    Format raw LLM output into structured suggestions.
    
    Args:
        raw_content: Raw string output from an LLM
        
    Returns:
        List of formatted suggestion dictionaries
    """
    try:
        # Try to extract JSON from the response
        suggestions = extract_json_from_llm_response(raw_content)
        
        # Validate and format each suggestion
        formatted_suggestions = []
        for suggestion in suggestions:
            formatted = validate_and_format_suggestion(suggestion)
            if formatted:
                formatted_suggestions.append(formatted)
        
        return formatted_suggestions
    
    except Exception as e:
        print(f"Error formatting suggestions: {str(e)}")
        return []


def extract_json_from_llm_response(response: str) -> List[Dict[str, Any]]:
    """
    Extract JSON from an LLM response.
    
    Args:
        response: Raw string output from an LLM
        
    Returns:
        List of suggestion dictionaries
        
    Raises:
        ValueError: If no valid JSON can be extracted
    """
    # Try to find JSON in the response using regex
    json_matches = re.findall(r'```json\n([\s\S]*?)\n```|(?<!\`)([\[{][\s\S]*?[\]}])(?!\`)', response)
    
    for match in json_matches:
        # Check which capture group has content (either inside code block or standalone)
        content = match[0] if match[0] else match[1]
        try:
            # Try to parse as array first
            parsed = json.loads(content)
            if isinstance(parsed, list):
                return parsed
            elif isinstance(parsed, dict):
                # Single suggestion as a dict
                return [parsed]
        except json.JSONDecodeError:
            continue
    
    # If we didn't find valid JSON, try to parse the entire response
    try:
        parsed = json.loads(response)
        if isinstance(parsed, list):
            return parsed
        elif isinstance(parsed, dict):
            return [parsed]
    except json.JSONDecodeError:
        # One more attempt with a more aggressive pattern
        try:
            # Find anything that looks like JSON, even without delimiters
            pattern = r'[\[{][\s\S]*?[\]}]'
            matches = re.findall(pattern, response)
            for match in matches:
                try:
                    parsed = json.loads(match)
                    if isinstance(parsed, list):
                        return parsed
                    elif isinstance(parsed, dict):
                        return [parsed]
                except json.JSONDecodeError:
                    continue
        except Exception:
            pass
    
    # If all attempts fail, raise an error
    raise ValueError("Could not extract valid JSON from the response")


def validate_and_format_suggestion(suggestion: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    """
    Validate and format a single suggestion.
    
    Args:
        suggestion: Dictionary containing a suggestion
        
    Returns:
        Formatted suggestion or None if validation fails
    """
    # Check required fields
    if "suggestion" not in suggestion:
        suggestion["suggestion"] = "Unnamed suggestion"
    
    # Ensure changes field exists
    if "changes" not in suggestion:
        suggestion["changes"] = {}
    
    # Initialize changes fields if missing
    changes = suggestion["changes"]
    for field in ["operatorsToAdd", "linksToAdd", "operatorsToDelete", "operatorPropertiesToChange"]:
        if field not in changes:
            changes[field] = []
    
    # Generate UUIDs for operators and links if missing
    for operator in changes["operatorsToAdd"]:
        if "operatorID" not in operator or not operator["operatorID"]:
            op_type = operator.get("operatorType", "Unknown")
            operator["operatorID"] = f"{op_type}-operator-{str(uuid.uuid4())}"
        
        # Ensure operator properties exists
        if "operatorProperties" not in operator:
            operator["operatorProperties"] = {}
    
    # Generate UUIDs for links if missing
    for link in changes["linksToAdd"]:
        if "linkID" not in link or not link["linkID"]:
            link["linkID"] = f"link-{str(uuid.uuid4())}"
    
    # Validate operatorsToDelete is a list of strings
    if not isinstance(changes["operatorsToDelete"], list):
        changes["operatorsToDelete"] = []
    
    return suggestion


def create_placeholder_suggestion() -> Dict[str, Any]:
    """
    Create a placeholder suggestion when LLM generation fails.
    
    Returns:
        A placeholder suggestion
    """
    return {
        "suggestion": "Could not generate a valid suggestion",
        "changes": {
            "operatorsToAdd": [],
            "linksToAdd": [],
            "operatorsToDelete": [],
            "operatorPropertiesToChange": []
        }
    } 