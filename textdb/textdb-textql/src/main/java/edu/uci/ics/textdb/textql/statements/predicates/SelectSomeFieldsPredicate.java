package edu.uci.ics.textdb.textql.statements.predicates;

import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;

import edu.uci.ics.textdb.web.request.beans.OperatorBean;
import edu.uci.ics.textdb.web.request.beans.ProjectionBean;

/**
 * Object representation of a "SELECT a, b, c, ..." predicate inside a { @code SelectExtractStatement },
 * were "a, b, c, ..." is a list of field names
 * 
 * @author Flavio Bayer
 *
 */
public class SelectSomeFieldsPredicate implements SelectPredicate {

    /**
     * The { @link List } of fields to be projected if it is specified as
     * in "SELECT a, b, c".
     */
    private List<String> projectedFields;

    /**
     * Create a { @code Statement } with the given list of field names to be projected.
     * @param projectedFields The list of field names to be projected.
     */
    public SelectSomeFieldsPredicate(List<String> projectedFields){
        this.projectedFields = projectedFields;
    }
    
    /**
     * Get the list of field names to be projected.
     * @return A list of field names to be projected
     */
    public List<String> getProjectedFields() {
        return projectedFields;
    }
    
    /**
     * Set the list of field names to be projected.
     * @param projectedFields The list of field names to be projected.
     */
    public void setProjectedFields(List<String> projectedFields) {
        this.projectedFields = projectedFields;
    }

    
    /**
     * Return this operator converted to an { @code OperatorBean }.
     * @param selectOperatorId The ID of the OperatorBean to be created.
     */
    public OperatorBean generateOperatorBean(String selectOperatorId) {
        ProjectionBean projectionBean = new ProjectionBean();
        projectionBean.setOperatorID(selectOperatorId);
        projectionBean.setOperatorType("Projection");
        projectionBean.setAttributes(String.join(",", this.getProjectedFields()));
        return projectionBean;
    }

    
    @Override
    public boolean equals(Object other) {
        if (other == null) { return false; }
        if (other.getClass() != getClass()) { return false; }
        SelectSomeFieldsPredicate selectFieldsPredicate = (SelectSomeFieldsPredicate) other;
        return new EqualsBuilder()
                .append(projectedFields, selectFieldsPredicate.projectedFields)
                .isEquals();
    }
}
