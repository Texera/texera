// import functions from lodash: import individual functions from lodash-es
//  to avoid include the entire lodash library
import cloneDeep from 'lodash-es/cloneDeep';
import { OperatorSchema, OperatorMetadata, GroupInfo } from '../../types/operator-schema.interface';

/**
 * Exports constants related to operator schema and operator metadata for testing purposes.
 *
 */

export const getMockOperatorSchemaList: () => OperatorSchema[] =
  () => cloneDeep([
    {
      'operatorType': 'ScanSource',
      'additionalMetadata': {
        'advancedOptions': [],
        'operatorDescription': 'Read records from a table one by one',
        'operatorGroupName': 'Source',
        'numInputPorts': 0,
        'numOutputPorts': 1,
        'userFriendlyName': 'Source: Scan'
      },
      'jsonSchema': {
        'id': 'urn:jsonschema:edu:uci:ics:texera:dataflow:source:scan:ScanSourcePredicate',
        'properties': {
          'tableName': {
            'type': 'string'
          }
        },
        'required': [
          'tableName'
        ],
        'type': 'object'
      }
    },
    {
      'operatorType': 'NlpSentiment',
      'additionalMetadata': {
        'advancedOptions': [],
        'operatorDescription': 'Sentiment analysis based on Stanford NLP package',
        'operatorGroupName': 'Analysis',
        'numInputPorts': 1,
        'numOutputPorts': 1,
        'userFriendlyName': 'Sentiment Analysis'
      },
      'jsonSchema': {
        'id': 'urn:jsonschema:edu:uci:ics:texera:dataflow:nlp:sentiment:NlpSentimentPredicate',
        'properties': {
          'attribute': {
            'type': 'string'
          },
          'resultAttribute': {
            'type': 'string'
          }
        },
        'required': [
          'attribute',
          'resultAttribute'
        ],
        'type': 'object'
      }
    },
    {
      'operatorType': 'ViewResults',
      'additionalMetadata': {
        'advancedOptions': [],
        'operatorDescription': 'View the results of the workflow',
        'operatorGroupName': 'View Results',
        'numInputPorts': 1,
        'numOutputPorts': 0,
        'userFriendlyName': 'View Results'
      },
      'jsonSchema': {
        'id': 'urn:jsonschema:edu:uci:ics:texera:dataflow:sink:tuple:TupleSinkPredicate',
        'properties': {
          'limit': {
            'default': 10,
            'type': 'integer'
          },
          'offset': {
            'default': 0,
            'type': 'integer'
          }
        },
        'type': 'object'
      }
    }
  ]);

export const getMockOperatorGroup: () => GroupInfo[] =
  () => cloneDeep([
    { groupName: 'Source', groupOrder: 1 },
    { groupName: 'Analysis', groupOrder: 2 },
    { groupName: 'View Results', groupOrder: 3 },
  ]);

export const getMockOperatorMetaData: () => OperatorMetadata =
  () => cloneDeep({
    operators: getMockOperatorSchemaList(),
    groups: getMockOperatorGroup()
  });
