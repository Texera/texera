package edu.uci.ics.texera.dataflow.sink.piechart;

import edu.uci.ics.texera.api.constants.ErrorMessages;
import edu.uci.ics.texera.api.dataflow.IOperator;
import edu.uci.ics.texera.api.dataflow.ISink;
import edu.uci.ics.texera.api.exception.DataflowException;
import edu.uci.ics.texera.api.exception.TexeraException;
import edu.uci.ics.texera.api.field.DoubleField;
import edu.uci.ics.texera.api.field.IField;
import edu.uci.ics.texera.api.field.IntegerField;
import edu.uci.ics.texera.api.field.StringField;
import edu.uci.ics.texera.api.field.TextField;
import edu.uci.ics.texera.api.schema.Attribute;
import edu.uci.ics.texera.api.schema.AttributeType;
import edu.uci.ics.texera.api.schema.Schema;
import edu.uci.ics.texera.api.tuple.Tuple;
import edu.uci.ics.texera.dataflow.sink.IVisualization;
import edu.uci.ics.texera.dataflow.sink.VisualizationConstants;
import edu.uci.ics.texera.dataflow.sink.VisualizationOperator;
import edu.uci.ics.texera.dataflow.sink.barchart.BarChartSinkPredicate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PieChartSink extends VisualizationOperator {
    private PieChartSinkPredicate predicate;


    public PieChartSink(PieChartSinkPredicate predicate) {
        super(VisualizationConstants.PIE);
        this.predicate = predicate;
    }
    @Override
    public void open() throws TexeraException {
        if (cursor != CLOSED) {
            return;
        }
        if (inputOperator == null) {
            throw new TexeraException(ErrorMessages.INPUT_OPERATOR_NOT_SPECIFIED);
        }
        inputOperator.open();

        Schema schema = inputOperator.getOutputSchema();

        Attribute nameColumn =  schema.getAttribute(predicate.getNameColumn());
        AttributeType nameColumnType = nameColumn.getType();
        if (!nameColumnType.equals(AttributeType.STRING) && !nameColumnType.equals(AttributeType.TEXT)) {
            throw new DataflowException("Type of name column should be string or text.");
        }

        Attribute dataColumn =  schema.getAttribute(predicate.getDataColumn());
        AttributeType dataColumnType = dataColumn.getType();
        if (!dataColumnType.equals(AttributeType.DOUBLE) && !dataColumnType.equals(AttributeType.INTEGER)) {
            throw new DataflowException(("Type of data column should be integer or double."));
        }

        Double ratio = predicate.getPruneRatio();
        if ( ratio < 0 || ratio > 1) {
            throw new DataflowException("Ratio should be in (0, 1).");
        }

        outputSchema = new Schema.Builder().add(nameColumn, dataColumn).build();
        cursor = OPENED;
    }

    @Override
    public void processTuples() throws TexeraException {
        List<Tuple> list = new ArrayList<>();

        Tuple tuple;

        while ( (tuple = inputOperator.getNextTuple()) != null) {
            list.add(tuple);
        }
        list = list.stream()
            .map(e -> new Tuple(outputSchema, e.getField(predicate.getNameColumn()), e.getField(predicate.getDataColumn())))
            .collect(Collectors.toList());

        list.sort((left, right) -> {
            double leftValue =  extractNumber(left.getField(predicate.getDataColumn()));
            double rightValue = extractNumber(right.getField(predicate.getDataColumn())) ;
            if ( leftValue < rightValue ) {
                return 1;
            } else if (leftValue == rightValue) {
                return 0;
            } else {
                return -1;
            }
        });

        double sum = 0.0;
        for (Tuple t: list) {
            sum += extractNumber(t.getField(predicate.getDataColumn()));
        }
        double total = 0.0;
        for (Tuple t: list) {
            total += extractNumber(t.getField(predicate.getDataColumn()));
            result.add(t);
            if (total / sum > predicate.getPruneRatio()) {

                IField nameField =  buildOtherNameField();
                IField dataField =  buildOtherDataField(sum - total);
                result.add(new Tuple(outputSchema, nameField, dataField));
                return;
            }
        }



    }
    private IField buildOtherNameField() {


        Attribute nameColumn =   inputOperator.getOutputSchema().getAttribute(predicate.getNameColumn());
        AttributeType nameColumnType = nameColumn.getType();
        if (nameColumnType.equals(AttributeType.STRING)) {
            return new StringField("Other");
        }
        return new TextField("Other");
    }

    private IField buildOtherDataField(double value) {
        Attribute dataColumn =   inputOperator.getOutputSchema().getAttribute(predicate.getDataColumn());
        AttributeType  dataColumnType = dataColumn.getType();

        if (dataColumnType.equals(AttributeType.INTEGER)) {
            return new IntegerField((int)value);
        }
        return new DoubleField(value);

    }

    private double extractNumber(IField field) {
        if (field instanceof DoubleField) {
            DoubleField doubleField = (DoubleField)field;
            return doubleField.getValue();
        }
        else if (field instanceof IntegerField) {
            IntegerField integerField = (IntegerField)field;
            return integerField.getValue().doubleValue();
        }
        return 0.0;
    }

}