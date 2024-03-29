/*
 * This file is generated by jOOQ.
 */
package edu.uci.ics.texera.web.model.jooq.generated.tables.records;


import edu.uci.ics.texera.web.model.jooq.generated.tables.Environment;
import edu.uci.ics.texera.web.model.jooq.generated.tables.interfaces.IEnvironment;

import java.sql.Timestamp;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record5;
import org.jooq.Row5;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.UInteger;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class EnvironmentRecord extends UpdatableRecordImpl<EnvironmentRecord> implements Record5<UInteger, UInteger, String, String, Timestamp>, IEnvironment {

    private static final long serialVersionUID = -1437704774;

    /**
     * Setter for <code>texera_db.environment.eid</code>.
     */
    @Override
    public void setEid(UInteger value) {
        set(0, value);
    }

    /**
     * Getter for <code>texera_db.environment.eid</code>.
     */
    @Override
    public UInteger getEid() {
        return (UInteger) get(0);
    }

    /**
     * Setter for <code>texera_db.environment.owner_uid</code>.
     */
    @Override
    public void setOwnerUid(UInteger value) {
        set(1, value);
    }

    /**
     * Getter for <code>texera_db.environment.owner_uid</code>.
     */
    @Override
    public UInteger getOwnerUid() {
        return (UInteger) get(1);
    }

    /**
     * Setter for <code>texera_db.environment.name</code>.
     */
    @Override
    public void setName(String value) {
        set(2, value);
    }

    /**
     * Getter for <code>texera_db.environment.name</code>.
     */
    @Override
    public String getName() {
        return (String) get(2);
    }

    /**
     * Setter for <code>texera_db.environment.description</code>.
     */
    @Override
    public void setDescription(String value) {
        set(3, value);
    }

    /**
     * Getter for <code>texera_db.environment.description</code>.
     */
    @Override
    public String getDescription() {
        return (String) get(3);
    }

    /**
     * Setter for <code>texera_db.environment.creation_time</code>.
     */
    @Override
    public void setCreationTime(Timestamp value) {
        set(4, value);
    }

    /**
     * Getter for <code>texera_db.environment.creation_time</code>.
     */
    @Override
    public Timestamp getCreationTime() {
        return (Timestamp) get(4);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<UInteger> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record5 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row5<UInteger, UInteger, String, String, Timestamp> fieldsRow() {
        return (Row5) super.fieldsRow();
    }

    @Override
    public Row5<UInteger, UInteger, String, String, Timestamp> valuesRow() {
        return (Row5) super.valuesRow();
    }

    @Override
    public Field<UInteger> field1() {
        return Environment.ENVIRONMENT.EID;
    }

    @Override
    public Field<UInteger> field2() {
        return Environment.ENVIRONMENT.OWNER_UID;
    }

    @Override
    public Field<String> field3() {
        return Environment.ENVIRONMENT.NAME;
    }

    @Override
    public Field<String> field4() {
        return Environment.ENVIRONMENT.DESCRIPTION;
    }

    @Override
    public Field<Timestamp> field5() {
        return Environment.ENVIRONMENT.CREATION_TIME;
    }

    @Override
    public UInteger component1() {
        return getEid();
    }

    @Override
    public UInteger component2() {
        return getOwnerUid();
    }

    @Override
    public String component3() {
        return getName();
    }

    @Override
    public String component4() {
        return getDescription();
    }

    @Override
    public Timestamp component5() {
        return getCreationTime();
    }

    @Override
    public UInteger value1() {
        return getEid();
    }

    @Override
    public UInteger value2() {
        return getOwnerUid();
    }

    @Override
    public String value3() {
        return getName();
    }

    @Override
    public String value4() {
        return getDescription();
    }

    @Override
    public Timestamp value5() {
        return getCreationTime();
    }

    @Override
    public EnvironmentRecord value1(UInteger value) {
        setEid(value);
        return this;
    }

    @Override
    public EnvironmentRecord value2(UInteger value) {
        setOwnerUid(value);
        return this;
    }

    @Override
    public EnvironmentRecord value3(String value) {
        setName(value);
        return this;
    }

    @Override
    public EnvironmentRecord value4(String value) {
        setDescription(value);
        return this;
    }

    @Override
    public EnvironmentRecord value5(Timestamp value) {
        setCreationTime(value);
        return this;
    }

    @Override
    public EnvironmentRecord values(UInteger value1, UInteger value2, String value3, String value4, Timestamp value5) {
        value1(value1);
        value2(value2);
        value3(value3);
        value4(value4);
        value5(value5);
        return this;
    }

    // -------------------------------------------------------------------------
    // FROM and INTO
    // -------------------------------------------------------------------------

    @Override
    public void from(IEnvironment from) {
        setEid(from.getEid());
        setOwnerUid(from.getOwnerUid());
        setName(from.getName());
        setDescription(from.getDescription());
        setCreationTime(from.getCreationTime());
    }

    @Override
    public <E extends IEnvironment> E into(E into) {
        into.from(this);
        return into;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached EnvironmentRecord
     */
    public EnvironmentRecord() {
        super(Environment.ENVIRONMENT);
    }

    /**
     * Create a detached, initialised EnvironmentRecord
     */
    public EnvironmentRecord(UInteger eid, UInteger ownerUid, String name, String description, Timestamp creationTime) {
        super(Environment.ENVIRONMENT);

        set(0, eid);
        set(1, ownerUid);
        set(2, name);
        set(3, description);
        set(4, creationTime);
    }
}
