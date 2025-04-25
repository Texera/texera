"""Test script for the LLM agent and formatter."""

import os
import json
import argparse
from dotenv import load_dotenv

from llm_agent.base import LLMAgentFactory
from output_formatter.formatter import format_raw_suggestions


# Load environment variables
load_dotenv()


def test_llm_agent(provider, prompt, output_file=None):
    """
    Test generating suggestions with an LLM agent.

    Args:
        provider: LLM provider (openai or anthropic)
        prompt: Natural language prompt to send to the LLM
        output_file: Optional file to save the raw output to
    """
    try:
        # Create LLM agent
        agent = LLMAgentFactory.create(provider)

        print(f"Testing {provider} LLM agent...")
        print(f"Prompt: {prompt}")
        print("-" * 50)

        # Generate suggestions
        suggestions = agent.generate_suggestions(
            prompt=prompt, max_suggestions=3, temperature=0.7
        )

        # Print suggestions
        print(f"Generated {len(suggestions)} suggestions:")
        for i, suggestion in enumerate(suggestions):
            print(f"\nSuggestion {i+1}: {suggestion['suggestion']}")
            print("Changes:")
            changes = suggestion["changes"]
            print(f"  - Operators to add: {len(changes['operatorsToAdd'])}")
            print(f"  - Links to add: {len(changes['linksToAdd'])}")
            print(f"  - Operators to delete: {len(changes['operatorsToDelete'])}")
            print(
                f"  - Properties to change: {len(changes.get('operatorPropertiesToChange', []))}"
            )

        # Save output to file if requested
        if output_file:
            with open(output_file, "w") as f:
                json.dump(suggestions, f, indent=2)
            print(f"\nSaved suggestions to {output_file}")

        return suggestions

    except Exception as e:
        print(f"Error testing LLM agent: {str(e)}")
        return []


def test_formatter(raw_json_file, output_file=None):
    """
    Test the formatter with a raw JSON file.

    Args:
        raw_json_file: Path to a file containing raw JSON from an LLM
        output_file: Optional file to save the formatted output to
    """
    try:
        # Load raw JSON
        with open(raw_json_file, "r") as f:
            raw_content = f.read()

        print(f"Testing formatter with {raw_json_file}...")
        print("-" * 50)

        # Format suggestions
        suggestions = format_raw_suggestions(raw_content)

        # Print suggestions
        print(f"Formatted {len(suggestions)} suggestions:")
        for i, suggestion in enumerate(suggestions):
            print(f"\nSuggestion {i+1}: {suggestion['suggestion']}")
            print("Changes:")
            changes = suggestion["changes"]
            print(f"  - Operators to add: {len(changes['operatorsToAdd'])}")
            print(f"  - Links to add: {len(changes['linksToAdd'])}")
            print(f"  - Operators to delete: {len(changes['operatorsToDelete'])}")
            print(
                f"  - Properties to change: {len(changes.get('operatorPropertiesToChange', []))}"
            )

        # Save output to file if requested
        if output_file:
            with open(output_file, "w") as f:
                json.dump(suggestions, f, indent=2)
            print(f"\nSaved formatted suggestions to {output_file}")

        return suggestions

    except Exception as e:
        print(f"Error testing formatter: {str(e)}")
        return []


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Test the LLM agent and formatter")

    # Create subparsers for different commands
    subparsers = parser.add_subparsers(dest="command", help="Command to run")

    # Agent testing subcommand
    agent_parser = subparsers.add_parser("agent", help="Test the LLM agent")
    agent_parser.add_argument(
        "--provider",
        choices=["openai", "anthropic"],
        default=os.environ.get("LLM_PROVIDER", "openai"),
        help="LLM provider to use",
    )
    agent_parser.add_argument(
        "--prompt", type=str, required=True, help="Prompt to send to the LLM"
    )
    agent_parser.add_argument("--output", type=str, help="File to save the output to")

    # Formatter testing subcommand
    formatter_parser = subparsers.add_parser("formatter", help="Test the formatter")
    formatter_parser.add_argument(
        "--input", type=str, required=True, help="Raw JSON file to format"
    )
    formatter_parser.add_argument(
        "--output", type=str, help="File to save the formatted output to"
    )

    # Parse arguments
    args = parser.parse_args()

    # Execute command
    if args.command == "agent":
        test_llm_agent(args.provider, args.prompt, args.output)
    elif args.command == "formatter":
        test_formatter(args.input, args.output)
    else:
        parser.print_help()
