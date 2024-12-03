package edu.uci.ics.amber.operator.udf.r;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.google.common.base.Preconditions;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle;
import edu.uci.ics.amber.core.executor.OpExecInitInfo;
import edu.uci.ics.amber.core.tuple.Attribute;
import edu.uci.ics.amber.core.tuple.Schema;
import edu.uci.ics.amber.core.workflow.PhysicalOp;
import edu.uci.ics.amber.core.workflow.SchemaPropagationFunc;
import edu.uci.ics.amber.operator.metadata.OperatorGroupConstants;
import edu.uci.ics.amber.operator.metadata.OperatorInfo;
import edu.uci.ics.amber.operator.source.SourceOperatorDescriptor;
import edu.uci.ics.amber.operator.util.OperatorDescriptorUtils;
import edu.uci.ics.amber.virtualidentity.ExecutionIdentity;
import edu.uci.ics.amber.virtualidentity.WorkflowIdentity;
import edu.uci.ics.amber.workflow.InputPort;
import edu.uci.ics.amber.workflow.OutputPort;
import edu.uci.ics.amber.workflow.PortIdentity;
import scala.Option;
import scala.collection.immutable.Map;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static scala.jdk.javaapi.CollectionConverters.asScala;


public class RUDFSourceOpDesc extends SourceOperatorDescriptor {
    @JsonProperty(
            required = true,
            defaultValue =
                    "# If using Table API:\n" +
                            "# function() { \n" +
                            "#   return (data.frame(Column_Here = \"Value_Here\")) \n" +
                            "# }\n" +
                            "\n" +
                            "# If using Tuple API:\n" +
                            "# library(coro)\n" +
                            "# coro::generator(function() {\n" +
                            "#   yield (list(text= \"hello world!\"))\n" +
                            "# })"
    )
    @JsonSchemaTitle("R Source UDF Script")
    @JsonPropertyDescription("Input your code here")
    public String code;

    @JsonProperty(required = true, defaultValue = "1")
    @JsonSchemaTitle("Worker count")
    @JsonPropertyDescription("Specify how many parallel workers to lunch")
    public Integer workers = 1;

    @JsonProperty(required = true, defaultValue = "false")
    @JsonSchemaTitle("Use Tuple API?")
    @JsonPropertyDescription("Check this box to use Tuple API, leave unchecked to use Table API")
    public Boolean useTupleAPI = false;

    @JsonProperty()
    @JsonSchemaTitle("Columns")
    @JsonPropertyDescription("The columns of the source")
    public List<Attribute> columns;

    @Override
    public PhysicalOp getPhysicalOp(WorkflowIdentity workflowId, ExecutionIdentity executionId) {
        String r_operator_type = useTupleAPI ? "r-tuple" : "r-table";
        OpExecInitInfo exec = OpExecInitInfo.apply(code, r_operator_type);
        Preconditions.checkArgument(workers >= 1, "Need at least 1 worker.");
        SchemaPropagationFunc func = SchemaPropagationFunc.apply((Function<Map<PortIdentity, Schema>, Map<PortIdentity, Schema>> & Serializable) inputSchemas -> {
            // Initialize a Java HashMap
            java.util.Map<PortIdentity, Schema> javaMap = new java.util.HashMap<>();

            javaMap.put(operatorInfo().outputPorts().head().id(), sourceSchema());

            // Convert the Java Map to a Scala immutable Map
            return OperatorDescriptorUtils.toImmutableMap(javaMap);
        });
        PhysicalOp physicalOp = PhysicalOp.sourcePhysicalOp(
                        workflowId,
                        executionId,
                        operatorIdentifier(),
                        exec
                )
                .withInputPorts(operatorInfo().inputPorts())
                .withOutputPorts(operatorInfo().outputPorts())
                .withIsOneToManyOp(true)
                .withPropagateSchema(func)
                .withLocationPreference(Option.empty());


        if (workers > 1) {
            return physicalOp
                    .withParallelizable(true)
                    .withSuggestedWorkerNum(workers);
        } else {
            return physicalOp.withParallelizable(false);
        }

    }

    @Override
    public OperatorInfo operatorInfo() {
        return new OperatorInfo(
                "1-out R UDF",
                "User-defined function operator in R script",
                OperatorGroupConstants.R_GROUP(),
                asScala(new ArrayList<InputPort>()).toList(),
                asScala(singletonList(new OutputPort(new PortIdentity(0, false), "", false))).toList(),
                false,
                false,
                false,
                false
        );
    }

    @Override
    public Schema sourceSchema() {
        Schema.Builder outputSchemaBuilder = Schema.builder();

        // for any UDFType, it can add custom output columns (attributes).
        if (columns != null) {
            outputSchemaBuilder.add(asScala(columns)).build();
        }
        return outputSchemaBuilder.build();
    }
}