# TODO: translate: workflow-util.service.ts
# public getNewOperatorPredicate(operatorType: string): OperatorPredicate {
#     const operatorSchema = this.operatorSchemaList.find(schema => schema.operatorType === operatorType);
#     if (operatorSchema === undefined) {
#       throw new Error(`operatorType ${operatorType} doesn't exist in operator metadata`);
#     }
#
#     const operatorId = operatorSchema.operatorType + "-" + this.getOperatorRandomUUID();
#     const operatorProperties = {};
#
#     // Remove the ID field for the schema to prevent warning messages from Ajv
#     const { ...schemaWithoutId } = operatorSchema.jsonSchema;
#
#     // value inserted in the data will be the deep clone of the default in the schema
#     const validate = this.ajv.compile(schemaWithoutId);
#     validate(operatorProperties);
#
#     const inputPorts: PortDescription[] = [];
#     const outputPorts: PortDescription[] = [];
#
#     // by default, the operator will not show advanced option in the properties to the user
#     const showAdvanced = false;
#
#     // by default, the operator is not disabled
#     const isDisabled = false;
#
#     // by default, the operator name is the user friendly name
#     const customDisplayName = operatorSchema.additionalMetadata.userFriendlyName;
#
#     const dynamicInputPorts = operatorSchema.additionalMetadata.dynamicInputPorts ?? false;
#     const dynamicOutputPorts = operatorSchema.additionalMetadata.dynamicOutputPorts ?? false;
#
#     for (let i = 0; i < operatorSchema.additionalMetadata.inputPorts.length; i++) {
#       const portID = "input-" + i.toString();
#       const portInfo = operatorSchema.additionalMetadata.inputPorts[i];
#       inputPorts.push(WorkflowUtilService.inputPortToPortDescription(portID, portInfo));
#     }
#
#     for (let i = 0; i < operatorSchema.additionalMetadata.outputPorts.length; i++) {
#       const portID = "output-" + i.toString();
#       const portInfo = operatorSchema.additionalMetadata.outputPorts[i];
#       outputPorts.push(WorkflowUtilService.outputPortToPortDescription(portID, portInfo));
#     }
#
#     const operatorVersion = operatorSchema.operatorVersion;
#
#     return {
#       operatorID: operatorId,
#       operatorType,
#       operatorVersion,
#       operatorProperties,
#       inputPorts,
#       outputPorts,
#       showAdvanced,
#       isDisabled,
#       customDisplayName,
#       dynamicInputPorts,
#       dynamicOutputPorts,
#     };
#   }
# into python