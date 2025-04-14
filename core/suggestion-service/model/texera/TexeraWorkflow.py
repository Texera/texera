import json

from typing import List, Dict, Tuple
import networkx as nx

from model.Operator import Operator
from model.Port import Port
from model.DataSchema import DataSchema

from model.Workflow import Workflow
from model.texera.TexeraOperator import TexeraOperator


class TexeraWorkflow(Workflow):
    def __init__(
            self,
            workflow_content: str,
            operator_id_to_port_indexed_input_schemas_mapping: Dict[str, List['DataSchema']]=None,
            operator_id_to_error_mapping: Dict[str, str]=None,
            wid: int = 0,
            workflow_title: str = ""
            ):

        if operator_id_to_port_indexed_input_schemas_mapping is None:
            operator_id_to_port_indexed_input_schemas_mapping = {}
        if operator_id_to_error_mapping is None:
            operator_id_to_error_mapping = {}

        self.workflow_content = workflow_content
        self.workflow_dict = json.loads(workflow_content)
        operators_dict = self.workflow_dict.get("operators", {})
        links_dict = self.workflow_dict.get("links", {})

        self.DAG = nx.DiGraph()
        self.workflow_title = workflow_title
        self.operators: Dict[str, 'TexeraOperator'] = {
            op_dict['operatorID']:
                TexeraOperator(
                    operator_dict = op_dict,
                    port_indexed_input_schemas=operator_id_to_port_indexed_input_schemas_mapping.get(op_dict['operatorID'], []),
                    error=operator_id_to_error_mapping.get(op_dict['operatorID'], "")
                ) for op_dict in operators_dict
        }
        # self.links = [TexeraLink(link_dict, self.operators) for link_dict in links_dict]
        self.wid = wid

        # start to build the DAG
        for operator in self.GetOperators():
            self.DAG.add_node(operator.GetId(),
                              type=operator.GetType(),
                              inputPorts=[port.GetId() for port in operator.GetInputPorts()],
                              outputPorts=[port.GetId() for port in operator.GetOutputPorts()],
                              error=operator.GetError()
                              )

        # then start to add link
        for link in links_dict:
            source_op_id = link.get('source').get('operatorID')
            src_port_id = link.get('source').get('portID')
            target_op_id = link.get('target').get('operatorID')
            target_port_id = link.get('target').get('portID')

            op = self.operators.get(target_op_id)
            if op is not None:
                schema = op.GetInputSchemaByPortID(target_port_id)
            else:
                schema = None
            self.DAG.add_edge(source_op_id,
                              target_op_id,
                              srcPort=src_port_id,
                              targetPort=target_port_id,
                              schema=schema
                              )


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