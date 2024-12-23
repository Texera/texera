/*
 * This file is generated by jOOQ.
 */
package edu.uci.ics.texera.dao.jooq.generated.tables.interfaces;


import java.io.Serializable;

import org.jooq.types.UInteger;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public interface IPublicProject extends Serializable {

    /**
     * Setter for <code>texera_db.public_project.pid</code>.
     */
    public void setPid(UInteger value);

    /**
     * Getter for <code>texera_db.public_project.pid</code>.
     */
    public UInteger getPid();

    /**
     * Setter for <code>texera_db.public_project.uid</code>.
     */
    public void setUid(UInteger value);

    /**
     * Getter for <code>texera_db.public_project.uid</code>.
     */
    public UInteger getUid();

    // -------------------------------------------------------------------------
    // FROM and INTO
    // -------------------------------------------------------------------------

    /**
     * Load data from another generated Record/POJO implementing the common interface IPublicProject
     */
    public void from(edu.uci.ics.texera.dao.jooq.generated.tables.interfaces.IPublicProject from);

    /**
     * Copy data into another generated Record/POJO implementing the common interface IPublicProject
     */
    public <E extends edu.uci.ics.texera.dao.jooq.generated.tables.interfaces.IPublicProject> E into(E into);
}
