import json

from typing import List, Dict, Tuple, Any
import networkx as nx

from model.Operator import Operator
from model.Port import Port
from model.DataSchema import DataSchema

from model.Workflow import Workflow
from model.texera.TexeraOperator import TexeraOperator


class TexeraWorkflow(Workflow):
    def __init__(
            self,
            workflow_content: str = "",
            workflow_dict: Dict[str, Any] = None,
            operator_id_to_port_indexed_input_schemas_mapping: Dict[str, List['DataSchema']]=None,
            operator_id_to_error_mapping: Dict[str, str]=None,
            wid: int = 0,
            workflow_title: str = ""
            ):

        if operator_id_to_port_indexed_input_schemas_mapping is None:
            operator_id_to_port_indexed_input_schemas_mapping = {}
        if operator_id_to_error_mapping is None:
            operator_id_to_error_mapping = {}
            
        self.wid = wid
        self.workflow_title = workflow_title
        self.DAG = nx.DiGraph()
        self.operators = {}
        self.links = []

        # Handle both string content and direct dict input
        if workflow_dict is not None:
            self.workflow_dict = workflow_dict
            self.workflow_content = json.dumps(workflow_dict)
            self.initialize_from_dict(workflow_dict)
        elif workflow_content:
            self.workflow_content = workflow_content
            self.workflow_dict = json.loads(workflow_content)
            operators_dict = self.workflow_dict.get("operators", {})
            links_dict = self.workflow_dict.get("links", {})

            self.operators: Dict[str, 'TexeraOperator'] = {
                op_dict['operatorID']:
                    TexeraOperator(
                        operator_dict = op_dict,
                        port_indexed_input_schemas=operator_id_to_port_indexed_input_schemas_mapping.get(op_dict['operatorID'], []),
                        error=operator_id_to_error_mapping.get(op_dict['operatorID'], "")
                    ) for op_dict in operators_dict
            }
            
            # Build the DAG
            self._build_dag()

    def initialize_from_dict(self, workflow_dict: Dict[str, Any]) -> None:
        """
        Initialize the workflow from a dictionary (needed by the interpreter).
        
        Args:
            workflow_dict: Dictionary representation of the workflow
        """
        # Convert the dictionary to a string for internal storage
        self.workflow_content = json.dumps(workflow_dict)
        self.workflow_dict = workflow_dict
        
        # Extract operators and links
        content = workflow_dict.get("content", {})
        operators_dict = content.get("operators", [])
        
        # Reset internal state
        self.operators = {}
        self.links = []
        
        # Initialize operators
        for op_dict in operators_dict:
            op_id = op_dict.get("operatorID")
            if op_id:
                self.operators[op_id] = TexeraOperator(
                    operator_dict=op_dict,
                    port_indexed_input_schemas=[],
                    error=""
                )
        
        # Build the DAG structure (adds links and completes initialization)
        self._build_dag()

    def GetWorkflowContent(self) -> str:
        return self.workflow_content

    def GetWorkflowId(self) -> int:
        return self.wid

    def GetOperators(self, types: List[str] = None) -> List['Operator']:
        if types is None:
            return list(self.operators.values())
        return [op for op in self.operators.values() if op.GetType() in types]

    def TopologicalSort(self) -> List['Operator']:
        # Assuming a valid DAG and operators are sortable
        # A complete topological sort algorithm should be implemented here
        sorted_operators = list(self.operators.values())  # Placeholder for actual sorting logic
        return sorted_operators

    def GetDAG(self):
        return self.DAG

    def GetSchemaToNextOperatorDistributionMapping(self) -> Dict['DataSchema', Dict[str, int]]:
        result: Dict['DataSchema', Dict[str, int]] = {}

        # Iterate over all edges in the DAG
        for source_op_id, target_op_id, edge_data in self.DAG.edges(data=True):
            schema = edge_data['schema']
            target_op = self.operators.get(target_op_id)
            target_op_type = target_op.GetType()

            if schema not in result:
                result[schema] = {}

            if target_op_type not in result[schema]:
                result[schema][target_op_type] = 0

            result[schema][target_op_type] += 1

        return result

    def GetOperatorTypeToNextOperatorDistributionMapping(self) -> Dict[str, Dict[str, int]]:
        result: Dict[str, Dict[str, int]] = {}

        # Iterate over all edges in the DAG
        for source_op_id, target_op_id, edge_data in self.DAG.edges(data=True):
            source_op = self.operators.get(source_op_id)
            source_op_type = source_op.GetType()
            target_op = self.operators.get(target_op_id)
            target_op_type = target_op.GetType()

            if source_op_type not in result:
                result[source_op_type] = {}

            if target_op_type not in result[source_op_type]:
                result[source_op_type][target_op_type] = 0

            result[source_op_type][target_op_type] += 1

        return result

    def GetAdditionPairs(self) -> List[Tuple[Tuple[Operator, Port], Tuple[Operator, Port]]]:
        results: List[Tuple[Tuple[Operator, Port], Tuple[Operator, Port]]] = []
        # Iterate over all edges in the DAG
        for source_op_id, target_op_id, edge_data in self.DAG.edges(data=True):
            source_op = self.operators.get(source_op_id)
            target_op = self.operators.get(target_op_id)
            srcPortId = edge_data.get('srcPort')
            targetPortId = edge_data.get('targetPort')
            if source_op is None or target_op is None:
                continue
            srcPort = next((port for port in source_op.GetOutputPorts() if port.GetId() == srcPortId), None)
            targetPort = next((port for port in target_op.GetInputPorts() if port.GetId() == targetPortId), None)

            if srcPort is None or targetPort is None:
                continue
            results.append(((source_op, srcPort), (target_op, targetPort)))
        return results

    def __str__(self) -> str:
        # Create a string representation of the workflow
        operators_str = '\n'.join([str(operator) for operator in self.GetOperators()])
        edges_str = '\n'.join([f"{source} -> {target}" for source, target in self.DAG.edges()])

        return (
            f"TexeraWorkflow(\n"
            f"  WorkflowID={self.wid},\n"
            f"  Title={self.workflow_title},\n"
            f"  Operators=[\n{operators_str}\n  ],\n"
            f"  DAG Edges=[\n    {edges_str}\n  ]\n"
            f")"
        )

    def VisualizeDAG(self) -> str:
        """
        Generate a visualization of the workflow DAG.
        This is a placeholder implementation required for the abstract class.
        
        Returns:
            A string representation of the workflow DAG visualization.
        """
        # Create a simple text-based visualization of the DAG
        visualization = "Workflow DAG Visualization:\n"
        
        # Add operator nodes
        visualization += "\nOperators:\n"
        for op_id, operator in self.operators.items():
            visualization += f"  - {op_id} ({operator.GetType()})\n"
        
        # Add links (edges)
        visualization += "\nLinks:\n"
        for link in self.links:
            source_op = link.source.operator_id
            source_port = link.source.port_id
            target_op = link.target.operator_id
            target_port = link.target.port_id
            visualization += f"  - {source_op}:{source_port} → {target_op}:{target_port}\n"
        
        return visualization

    def _build_dag(self) -> None:
        """Build the DAG from the workflow dictionary's operators and links."""
        # Clear existing DAG
        self.DAG = nx.DiGraph()
        
        # Extract operators and links
        operators_dict = self.workflow_dict.get("content", {}).get("operators", [])
        links_dict = self.workflow_dict.get("content", {}).get("links", [])
        
        # Add nodes to DAG
        for operator in self.GetOperators():
            self.DAG.add_node(operator.GetId(),
                            type=operator.GetType(),
                            inputPorts=[port.GetId() for port in operator.GetInputPorts()],
                            outputPorts=[port.GetId() for port in operator.GetOutputPorts()],
                            error=operator.GetError()
                            )
        
        # Add links to DAG
        for link in links_dict:
            source_op_id = link.get('source', {}).get('operatorID')
            src_port_id = link.get('source', {}).get('portID')
            target_op_id = link.get('target', {}).get('operatorID')
            target_port_id = link.get('target', {}).get('portID')
            
            if source_op_id and target_op_id:
                # Add link to links list
                class Link:
                    def __init__(self, src_id, src_port, target_id, target_port):
                        self.source = type('obj', (object,), {'operator_id': src_id, 'port_id': src_port})
                        self.target = type('obj', (object,), {'operator_id': target_id, 'port_id': target_port})
                
                self.links.append(Link(source_op_id, src_port_id, target_op_id, target_port_id))
                
                # Add edge to DAG
                op = self.operators.get(target_op_id)
                schema = None
                if op is not None:
                    schema = op.GetInputSchemaByPortID(target_port_id)
                
                self.DAG.add_edge(source_op_id,
                                target_op_id,
                                srcPort=src_port_id,
                                targetPort=target_port_id,
                                schema=schema)