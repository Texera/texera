/*
 * This file is generated by jOOQ.
 */
package edu.uci.ics.texera.dao.jooq.generated.tables.interfaces;


import java.io.Serializable;
import java.sql.Timestamp;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public interface IWorkflowVersion extends Serializable {

    /**
     * Setter for <code>texera_db.workflow_version.vid</code>.
     */
    public void setVid(Integer value);

    /**
     * Getter for <code>texera_db.workflow_version.vid</code>.
     */
    public Integer getVid();

    /**
     * Setter for <code>texera_db.workflow_version.wid</code>.
     */
    public void setWid(Integer value);

    /**
     * Getter for <code>texera_db.workflow_version.wid</code>.
     */
    public Integer getWid();

    /**
     * Setter for <code>texera_db.workflow_version.content</code>.
     */
    public void setContent(String value);

    /**
     * Getter for <code>texera_db.workflow_version.content</code>.
     */
    public String getContent();

    /**
     * Setter for <code>texera_db.workflow_version.creation_time</code>.
     */
    public void setCreationTime(Timestamp value);

    /**
     * Getter for <code>texera_db.workflow_version.creation_time</code>.
     */
    public Timestamp getCreationTime();

    // -------------------------------------------------------------------------
    // FROM and INTO
    // -------------------------------------------------------------------------

    /**
     * Load data from another generated Record/POJO implementing the common
     * interface IWorkflowVersion
     */
    public void from(IWorkflowVersion from);

    /**
     * Copy data into another generated Record/POJO implementing the common
     * interface IWorkflowVersion
     */
    public <E extends IWorkflowVersion> E into(E into);
}
