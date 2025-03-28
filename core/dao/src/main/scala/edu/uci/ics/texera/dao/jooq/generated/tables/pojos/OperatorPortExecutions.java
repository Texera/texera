/*
 * This file is generated by jOOQ.
 */
package edu.uci.ics.texera.dao.jooq.generated.tables.pojos;


import edu.uci.ics.texera.dao.jooq.generated.tables.interfaces.IOperatorPortExecutions;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class OperatorPortExecutions implements IOperatorPortExecutions {

    private static final long serialVersionUID = 1L;

    private Integer workflowExecutionId;
    private String  globalPortId;
    private String  resultUri;

    public OperatorPortExecutions() {}

    public OperatorPortExecutions(IOperatorPortExecutions value) {
        this.workflowExecutionId = value.getWorkflowExecutionId();
        this.globalPortId = value.getGlobalPortId();
        this.resultUri = value.getResultUri();
    }

    public OperatorPortExecutions(
        Integer workflowExecutionId,
        String  globalPortId,
        String  resultUri
    ) {
        this.workflowExecutionId = workflowExecutionId;
        this.globalPortId = globalPortId;
        this.resultUri = resultUri;
    }

    /**
     * Getter for
     * <code>texera_db.operator_port_executions.workflow_execution_id</code>.
     */
    @Override
    public Integer getWorkflowExecutionId() {
        return this.workflowExecutionId;
    }

    /**
     * Setter for
     * <code>texera_db.operator_port_executions.workflow_execution_id</code>.
     */
    @Override
    public void setWorkflowExecutionId(Integer workflowExecutionId) {
        this.workflowExecutionId = workflowExecutionId;
    }

    /**
     * Getter for
     * <code>texera_db.operator_port_executions.global_port_id</code>.
     */
    @Override
    public String getGlobalPortId() {
        return this.globalPortId;
    }

    /**
     * Setter for
     * <code>texera_db.operator_port_executions.global_port_id</code>.
     */
    @Override
    public void setGlobalPortId(String globalPortId) {
        this.globalPortId = globalPortId;
    }

    /**
     * Getter for <code>texera_db.operator_port_executions.result_uri</code>.
     */
    @Override
    public String getResultUri() {
        return this.resultUri;
    }

    /**
     * Setter for <code>texera_db.operator_port_executions.result_uri</code>.
     */
    @Override
    public void setResultUri(String resultUri) {
        this.resultUri = resultUri;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("OperatorPortExecutions (");

        sb.append(workflowExecutionId);
        sb.append(", ").append(globalPortId);
        sb.append(", ").append(resultUri);

        sb.append(")");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // FROM and INTO
    // -------------------------------------------------------------------------

    @Override
    public void from(IOperatorPortExecutions from) {
        setWorkflowExecutionId(from.getWorkflowExecutionId());
        setGlobalPortId(from.getGlobalPortId());
        setResultUri(from.getResultUri());
    }

    @Override
    public <E extends IOperatorPortExecutions> E into(E into) {
        into.from(this);
        return into;
    }
}
