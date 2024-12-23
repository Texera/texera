/*
 * This file is generated by jOOQ.
 */
package edu.uci.ics.texera.dao.jooq.generated.tables.interfaces;


import java.io.Serializable;
import java.sql.Timestamp;

import org.jooq.types.UInteger;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public interface IDatasetVersion extends Serializable {

    /**
     * Setter for <code>texera_db.dataset_version.dvid</code>.
     */
    public void setDvid(UInteger value);

    /**
     * Getter for <code>texera_db.dataset_version.dvid</code>.
     */
    public UInteger getDvid();

    /**
     * Setter for <code>texera_db.dataset_version.did</code>.
     */
    public void setDid(UInteger value);

    /**
     * Getter for <code>texera_db.dataset_version.did</code>.
     */
    public UInteger getDid();

    /**
     * Setter for <code>texera_db.dataset_version.creator_uid</code>.
     */
    public void setCreatorUid(UInteger value);

    /**
     * Getter for <code>texera_db.dataset_version.creator_uid</code>.
     */
    public UInteger getCreatorUid();

    /**
     * Setter for <code>texera_db.dataset_version.name</code>.
     */
    public void setName(String value);

    /**
     * Getter for <code>texera_db.dataset_version.name</code>.
     */
    public String getName();

    /**
     * Setter for <code>texera_db.dataset_version.version_hash</code>.
     */
    public void setVersionHash(String value);

    /**
     * Getter for <code>texera_db.dataset_version.version_hash</code>.
     */
    public String getVersionHash();

    /**
     * Setter for <code>texera_db.dataset_version.creation_time</code>.
     */
    public void setCreationTime(Timestamp value);

    /**
     * Getter for <code>texera_db.dataset_version.creation_time</code>.
     */
    public Timestamp getCreationTime();

    // -------------------------------------------------------------------------
    // FROM and INTO
    // -------------------------------------------------------------------------

    /**
     * Load data from another generated Record/POJO implementing the common interface IDatasetVersion
     */
    public void from(edu.uci.ics.texera.dao.jooq.generated.tables.interfaces.IDatasetVersion from);

    /**
     * Copy data into another generated Record/POJO implementing the common interface IDatasetVersion
     */
    public <E extends edu.uci.ics.texera.dao.jooq.generated.tables.interfaces.IDatasetVersion> E into(E into);
}
