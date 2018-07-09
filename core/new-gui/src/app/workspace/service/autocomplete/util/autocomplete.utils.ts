import { HttpErrorResponse } from '@angular/common/http';
import { OperatorMetadata, OperatorSchema } from '../../../types/operator-schema.interface';
import { SourceTableNamesAPIResponse } from '../../../types/autocomplete.interface';
import { SourceTableDetails, ErrorExecutionResult } from '../../../types/autocomplete.interface';

import { WorkflowGraphReadonly } from '../../workflow-graph/model/workflow-graph';
import {
  LogicalLink, LogicalPlan, LogicalOperator,
} from '../../../types/execute-workflow.interface';

import cloneDeep from 'lodash-es/cloneDeep';

export const SOURCE_OPERATORS_REQUIRING_TABLENAMES: ReadonlyArray<string> = ['KeywordSource', 'RegexSource', 'WordCountIndexSource',
                                                                            'DictionarySource', 'FuzzyTokenSource', 'ScanSource'];

export class AutocompleteUtils {

  constructor() { }

  /**
  * This function takes the response from the table-metadata API and creates a list of tableNames
  * out of it.
  * @param response The response from resourse/table-metadata API
  */
  public static processSourceTableAPIResponse(response: SourceTableNamesAPIResponse): Array<string> {
    const tableNames: Array<string> = [];
    if (response.code !== 0) {
      return tableNames;
    }
    const message = response.message;
    const tablesList: ReadonlyArray<SourceTableDetails> = JSON.parse(message);
    tablesList.forEach((table) => {
      tableNames.push(table.tableName);
    });
    return tableNames;
  }

  /**
   * This function takes the operator metadata returned by operator metadata service and modifies the
   *  schema of source operators which need a table name. Initially the operator schema had a property
   * called tableName which was just a string. After modification, the property tableName stays a string
   *  but takes enum as input. The values of the enum are the different table names which are available
   * at the server side.
   * @param operatorMetadata The metadata from operator metadata service
   */
  public static addSourceTableNamesToMetadata(operatorMetadata: OperatorMetadata,
    tablesNames: ReadonlyArray<string> | undefined): OperatorMetadata {
    // If the tableNames array is empty, just return the original operator metadata.
    if (!tablesNames || tablesNames.length === 0) {
      return operatorMetadata;
    }

    const operatorSchemaList: Array<OperatorSchema> = cloneDeep(operatorMetadata.operators.slice());
    for (let i = 0; i < operatorSchemaList.length; i++) {
      if (SOURCE_OPERATORS_REQUIRING_TABLENAMES.includes(operatorSchemaList[i].operatorType)) {
        const jsonSchemaToModify = cloneDeep(operatorSchemaList[i].jsonSchema);
        const operatorProperties = jsonSchemaToModify.properties;
        if (!operatorProperties) {
          throw new Error(`Operator ${operatorSchemaList[i].operatorType} does not have properties in its schema`);
        }

        operatorProperties['tableName'] = { type: 'string', enum: tablesNames.slice() };

        const newOperatorSchema: OperatorSchema = {
          ...operatorSchemaList[i],
          jsonSchema: jsonSchemaToModify
        };

        operatorSchemaList[i] = newOperatorSchema;
      }
    }

    const operatorMetadataModified: OperatorMetadata = {
      ...operatorMetadata,
      operators: operatorSchemaList
    };

    return operatorMetadataModified;
  }

    /**
   * Modifies the schema of the operator according to autocomplete information obtained from the backend.
   * @param operatorSchema operator schema for the operator without autocomplete info filled in
   * @param inputSchema the input schema of the operator as inferred by autocomplete API
   */
  public static addInputSchemaToOperatorSchema(operatorSchema: OperatorSchema, inputSchema: ReadonlyArray<string>): OperatorSchema {
    // If the inputSchema is empty, just return the original operator metadata.
    if (!inputSchema || inputSchema.length === 0) {
      return operatorSchema;
    }

    const jsonSchemaToModify = cloneDeep(operatorSchema.jsonSchema);
    const operatorProperties = jsonSchemaToModify.properties;
    if (!operatorProperties) {
      throw new Error(`Operator ${operatorSchema.operatorType} does not have properties in its schema`);
    }

    // There are some operators which only take one of the attributes as input (eg: Analytics group) and some
    // which take more than one (eg: Search group). Therefore, the jsonSchema for these operators are different.
    // TODO: Standardize this i.e. All operators should accept input schema in a common key of json.
    // TODO: Join operators have two inputs - inner and outer. Autocomplete API currently returns all attributes
    //       in a single array. So, we can't differentiate between inner and outer. Therefore, autocomplete isn't applicable
    //       to Join yet.
    const items_key_in_schemajson = 'items';
    const attribute_list_key_in_schemajson = 'attributes';
    const single_attribute_in_schemajson = 'attribute';

    if (single_attribute_in_schemajson in operatorProperties) {
      operatorProperties[single_attribute_in_schemajson] = { type: 'string', enum: inputSchema.slice() };
    } else if (attribute_list_key_in_schemajson in operatorProperties
                  && items_key_in_schemajson in operatorProperties[attribute_list_key_in_schemajson]) {
      operatorProperties[attribute_list_key_in_schemajson][items_key_in_schemajson] = { type: 'string', enum: inputSchema.slice() };
    }

    const newOperatorSchema: OperatorSchema = {
      ...operatorSchema,
      jsonSchema: jsonSchemaToModify
    };

    return newOperatorSchema;
  }

   /**
   * Transform a workflowGraph object to the HTTP request body according to the backend API.
   *
   * All the operators in the workflowGraph will be transformed to LogicalOperator objects,
   *  where each operator has an operatorID and operatorType along with
   *  the properties of the operator.
   *
   *
   * All the links in the workflowGraph will be tranformed to LogicalLink objects,
   *  where each link will store its source id as its origin and target id as its destination.
   *
   * @param workflowGraph
   */
  public static getLogicalPlanRequest(workflowGraph: WorkflowGraphReadonly): LogicalPlan {

    const operators: LogicalOperator[] = workflowGraph
      .getAllOperators().map(op => ({
        ...op.operatorProperties,
        operatorID: op.operatorID,
        operatorType: op.operatorType
      }));

    const links: LogicalLink[] = workflowGraph
      .getAllLinks().map(link => ({
        origin: link.source.operatorID,
        destination: link.target.operatorID
      }));

    return { operators, links };
  }

    /**
   * Handles the HTTP Error response in different failure scenarios
   *  and converts to an ErrorExecutionResult object.
   * @param errorResponse
   */
  public static processErrorResponse(errorResponse: HttpErrorResponse): ErrorExecutionResult {
    // client side error, such as no internet connection
    if (errorResponse.error instanceof ProgressEvent) {
      return {
        code: -1,
        message: 'Could not reach Texera server'
      };
    }

    // other kinds of server error
    return {
      code: -1,
      message: `Texera server autocomplete API error: ${errorResponse.error.message}`
    };
  }
}
