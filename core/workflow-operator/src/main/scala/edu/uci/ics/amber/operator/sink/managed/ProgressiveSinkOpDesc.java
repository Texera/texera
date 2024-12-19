package edu.uci.ics.amber.operator.sink.managed;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;
import edu.uci.ics.amber.core.executor.OpExecInitInfo;
import edu.uci.ics.amber.core.executor.OperatorExecutor;
import edu.uci.ics.amber.core.tuple.Schema;
import edu.uci.ics.amber.core.workflow.PhysicalOp;
import edu.uci.ics.amber.core.workflow.SchemaPropagationFunc;
import edu.uci.ics.amber.operator.metadata.OperatorGroupConstants;
import edu.uci.ics.amber.operator.metadata.OperatorInfo;
import edu.uci.ics.amber.operator.sink.ProgressiveUtils;
import edu.uci.ics.amber.operator.sink.SinkOpDesc;
import edu.uci.ics.amber.operator.util.OperatorDescriptorUtils;
import edu.uci.ics.amber.virtualidentity.ExecutionIdentity;
import edu.uci.ics.amber.virtualidentity.OperatorIdentity;
import edu.uci.ics.amber.virtualidentity.WorkflowIdentity;
import edu.uci.ics.amber.workflow.InputPort;
import edu.uci.ics.amber.workflow.OutputPort;
import edu.uci.ics.amber.workflow.PortIdentity;
import scala.Option;
import scala.Tuple2;
import scala.collection.immutable.Map;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.function.Function;


import static java.util.Collections.singletonList;
import static scala.jdk.javaapi.CollectionConverters.asScala;

public class ProgressiveSinkOpDesc extends SinkOpDesc {

    // use SET_SNAPSHOT as the default output mode
    // this will be set internally by the workflow compiler
    @JsonIgnore
    private OutputPort.OutputMode outputMode = OutputPort.OutputMode$.MODULE$.fromValue(0);


    // corresponding upstream operator ID and output port, will be set by workflow compiler
    @JsonIgnore
    private Option<OperatorIdentity> upstreamId = Option.empty();

    @JsonIgnore
    private Option<Integer> upstreamPort = Option.empty();

    @Override
    public PhysicalOp getPhysicalOp(WorkflowIdentity workflowId, ExecutionIdentity executionId) {

        return PhysicalOp.localPhysicalOp(
                        workflowId,
                        executionId,
                        operatorIdentifier(),
                        OpExecInitInfo.apply(
                                (Function<Tuple2<Object, Object>, OperatorExecutor> & java.io.Serializable)
                                        worker -> new edu.uci.ics.amber.operator.sink.managed.ProgressiveSinkOpExec(outputMode, this.getUpstreamId().get().id(), workflowId)
                        )
                )
                .withInputPorts(this.operatorInfo().inputPorts())
                .withOutputPorts(this.operatorInfo().outputPorts())
                .withPropagateSchema(
                        SchemaPropagationFunc.apply((Function<Map<PortIdentity, Schema>, Map<PortIdentity, Schema>> & Serializable) inputSchemas -> {
                            // Initialize a Java HashMap
                            java.util.Map<PortIdentity, Schema> javaMap = new java.util.HashMap<>();

                            Schema inputSchema = inputSchemas.values().head();

                            // SET_SNAPSHOT:
                            Schema outputSchema;
                            if (this.outputMode.equals(OutputPort.OutputMode$.MODULE$.fromValue(0))) {
                                if (inputSchema.containsAttribute(ProgressiveUtils.insertRetractFlagAttr().getName())) {
                                    // input is insert/retract delta: the flag column is removed in output
                                    outputSchema = Schema.builder().add(inputSchema)
                                            .remove(ProgressiveUtils.insertRetractFlagAttr().getName()).build();
                                } else {
                                    // input is insert-only delta: output schema is the same as input schema
                                    outputSchema = inputSchema;
                                }
                            } else {
                                // SET_DELTA: output schema is always the same as input schema
                                outputSchema = inputSchema;
                            }

                            javaMap.put(operatorInfo().outputPorts().head().id(), outputSchema);
                            // Convert the Java Map to a Scala immutable Map
                            return OperatorDescriptorUtils.toImmutableMap(javaMap);
                        })
                );
    }

    @Override
    public OperatorInfo operatorInfo() {
        return new OperatorInfo(
                "View Results",
                "View the results",
                OperatorGroupConstants.UTILITY_GROUP(),
                asScala(singletonList(new InputPort(new PortIdentity(0, false), "", false, asScala(new ArrayList<PortIdentity>()).toSeq()))).toList(),
                asScala(singletonList(new OutputPort(new PortIdentity(0, false), "", false, OutputPort.OutputMode$.MODULE$.fromValue(0)))).toList(),
                false,
                false,
                false,
                false);
    }

    @Override
    public Schema getOutputSchema(Schema[] schemas) {
        Preconditions.checkArgument(schemas.length == 1);
        Schema inputSchema = schemas[0];

        // SET_SNAPSHOT:
        if (this.outputMode.equals(OutputPort.OutputMode$.MODULE$.fromValue(0))) {
            if (inputSchema.containsAttribute(ProgressiveUtils.insertRetractFlagAttr().getName())) {
                // input is insert/retract delta: the flag column is removed in output
                return Schema.builder().add(inputSchema)
                        .remove(ProgressiveUtils.insertRetractFlagAttr().getName()).build();
            } else {
                // input is insert-only delta: output schema is the same as input schema
                return inputSchema;
            }
        } else {
            // SET_DELTA: output schema is always the same as input schema
            return inputSchema;
        }
    }

    @JsonIgnore
    public OutputPort.OutputMode getOutputMode() {
        return outputMode;
    }

    @JsonIgnore
    public void setOutputMode(OutputPort.OutputMode outputMode) {
        this.outputMode = outputMode;
    }

    @JsonIgnore
    public Option<OperatorIdentity> getUpstreamId() {
        return upstreamId;
    }

    @JsonIgnore
    public void setUpstreamId(OperatorIdentity upstreamId) {
        this.upstreamId = Option.apply(upstreamId);
    }

    @JsonIgnore
    public Option<Integer> getUpstreamPort() {
        return upstreamPort;
    }

    @JsonIgnore
    public void setUpstreamPort(Integer upstreamPort) {
        this.upstreamPort = Option.apply(upstreamPort);
    }


}
