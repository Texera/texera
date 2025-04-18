## Workflow Suggestion Generator

### Prompt Generation Layer

This layer is responsible for processing the given information regarding the workflow, compilation state, execution state and result, into the better natural language description. Once these natural language descriptions are generated, they can be passed to the LLM agent.

Prompt generation can be divided into 2 major packages, each package provides a single endpoint:
#### 1. Workflow Static Interpretation
- Endpoint name: interpretWorkflow
- Endpoint intput parameters:
    - workflow (dict) 
    - input schema for each operator (dict)
    - static error for each operator (dict)
    - interpretation method type
        1. RAW: simply use the below template
            ```
            Here is the workflow dict: 
            ${string representation of workflow dict}

            Here is the input schema for each operator:
            ${string representation of the input schema dict}
            ```
        2. BY_PATH
            - use the `TexeraWorkflow` class to parse the workflow dict and input schema dict
            - parse out all the paths from the DAG structure. A path is a sub workflow which is a single line, from source operator to the sink operator. This subworkflow should also carry the schema information extracted from the input schema dict, and the static error information, on each operator

            - use the below template to generate the description from the paths and schemas:
            ```
            Here are the existing paths in this workflow and related schemas

            Path1: ${string representation of the path's sub-workflow}

            Path2: ${string representation of the path's sub-workflow}
            ...
            ```
- Endpoint output parameters:
    - natural language description (a string)



#### 2. Workflow Interpretation with Execution information
TODO

### LLM Agent Layer

This layer is responsible for calling language models to get the response (the suggestions).

The LLM Agent layer is organized as follows:

1. `llm_agent/base.py` - Defines the abstract base class for all LLM agents and a factory for creating agents
2. `llm_agent/openai_agent.py` - Implementation for OpenAI models (GPT-4, GPT-3.5, etc.)
3. `llm_agent/anthropic_agent.py` - Implementation for Anthropic Claude models

#### Configuration

LLM agents are configured using environment variables:

```
# LLM provider configuration (openai or anthropic)
LLM_PROVIDER=openai

# OpenAI configuration
OPENAI_API_KEY=your_openai_api_key_here
OPENAI_MODEL=gpt-4-turbo-preview

# Anthropic configuration
ANTHROPIC_API_KEY=your_anthropic_api_key_here
ANTHROPIC_MODEL=claude-3-opus-20240229
```

#### Usage

The SuggestionGenerator automatically creates and uses an LLM agent based on the configuration.
You can also create and use an LLM agent directly:

```python
from llm_agent.base import LLMAgentFactory

# Create an LLM agent
agent = LLMAgentFactory.create("openai", model="gpt-4-turbo-preview")

# Generate suggestions
suggestions = agent.generate_suggestions(
    prompt="Description of the workflow",
    max_suggestions=3,
    temperature=0.7
)
```

#### Adding New LLM Providers

To add a new LLM provider:

1. Create a new file `llm_agent/your_provider_agent.py`
2. Implement the LLMAgent interface
3. Register your implementation with the LLMAgentFactory

Example:
```python
@LLMAgentFactory.register("your_provider")
class YourProviderAgent(LLMAgent):
    def __init__(self, model="default-model", api_key=None):
        # Initialize client
        pass
        
    def generate_suggestions(self, prompt, max_suggestions=3, temperature=0.7, **kwargs):
        # Generate suggestions using your provider's API
        pass
```

### Output Formatting Layer

This layer ensures that LLM outputs match the expected format for workflow suggestions.

The output formatter is responsible for:

1. Extracting JSON from raw LLM output
2. Validating suggestion structure
3. Filling in missing values (UUIDs, empty fields, etc.)
4. Converting to the correct format expected by the frontend

#### Usage

The output formatter is automatically used by the LLM agents, but you can also use it directly:

```python
from output_formatter.formatter import format_raw_suggestions

# Format raw LLM output
raw_output = """
[
  {
    "suggestion": "Add a keyword search",
    "changes": {
      "operatorsToAdd": [
        {
          "operatorType": "KeywordSearch",
          "operatorProperties": {
            "keyword": "example"
          }
        }
      ],
      "linksToAdd": []
    }
  }
]
"""

formatted_suggestions = format_raw_suggestions(raw_output)
```

### Testing

You can test the LLM agent and formatter using the test script:

```bash
# Test an LLM agent
python test_llm_agent.py agent --provider openai --prompt "Generate a workflow suggestion" --output output.json

# Test the formatter with a raw JSON file
python test_llm_agent.py formatter --input raw_output.json --output formatted_output.json
```

### API Endpoints

The service exposes the following endpoints:

- `GET /` - Health check endpoint
- `GET /api/config` - Get the current configuration
- `POST /api/workflow-suggestion` - Generate workflow suggestions

Required request format:
```json
{
  "workflow": "{JSON string of the workflow}",
  "compilationState": {
    "state": "Succeeded",
    "operatorInputSchemaMap": {},
    "operatorErrors": {}
  },
  "executionState": {
    "state": "Completed"
  },
  "resultTables": {},
  "maxSuggestions": 3
}
```
