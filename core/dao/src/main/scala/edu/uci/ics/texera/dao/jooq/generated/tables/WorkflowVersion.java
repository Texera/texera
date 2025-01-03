/*
 * This file is generated by jOOQ.
 */
package edu.uci.ics.texera.dao.jooq.generated.tables;


import edu.uci.ics.texera.dao.jooq.generated.Indexes;
import edu.uci.ics.texera.dao.jooq.generated.Keys;
import edu.uci.ics.texera.dao.jooq.generated.TexeraDb;
import edu.uci.ics.texera.dao.jooq.generated.tables.records.WorkflowVersionRecord;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Identity;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Row4;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.TableImpl;
import org.jooq.types.UInteger;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class WorkflowVersion extends TableImpl<WorkflowVersionRecord> {

    private static final long serialVersionUID = -1813902570;

    /**
     * The reference instance of <code>texera_db.workflow_version</code>
     */
    public static final WorkflowVersion WORKFLOW_VERSION = new WorkflowVersion();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<WorkflowVersionRecord> getRecordType() {
        return WorkflowVersionRecord.class;
    }

    /**
     * The column <code>texera_db.workflow_version.vid</code>.
     */
    public final TableField<WorkflowVersionRecord, UInteger> VID = createField(DSL.name("vid"), org.jooq.impl.SQLDataType.INTEGERUNSIGNED.nullable(false).identity(true), this, "");

    /**
     * The column <code>texera_db.workflow_version.wid</code>.
     */
    public final TableField<WorkflowVersionRecord, UInteger> WID = createField(DSL.name("wid"), org.jooq.impl.SQLDataType.INTEGERUNSIGNED.nullable(false), this, "");

    /**
     * The column <code>texera_db.workflow_version.content</code>.
     */
    public final TableField<WorkflowVersionRecord, String> CONTENT = createField(DSL.name("content"), org.jooq.impl.SQLDataType.CLOB.nullable(false), this, "");

    /**
     * The column <code>texera_db.workflow_version.creation_time</code>.
     */
    public final TableField<WorkflowVersionRecord, Timestamp> CREATION_TIME = createField(DSL.name("creation_time"), org.jooq.impl.SQLDataType.TIMESTAMP.nullable(false).defaultValue(org.jooq.impl.DSL.field("CURRENT_TIMESTAMP", org.jooq.impl.SQLDataType.TIMESTAMP)), this, "");

    /**
     * Create a <code>texera_db.workflow_version</code> table reference
     */
    public WorkflowVersion() {
        this(DSL.name("workflow_version"), null);
    }

    /**
     * Create an aliased <code>texera_db.workflow_version</code> table reference
     */
    public WorkflowVersion(String alias) {
        this(DSL.name(alias), WORKFLOW_VERSION);
    }

    /**
     * Create an aliased <code>texera_db.workflow_version</code> table reference
     */
    public WorkflowVersion(Name alias) {
        this(alias, WORKFLOW_VERSION);
    }

    private WorkflowVersion(Name alias, Table<WorkflowVersionRecord> aliased) {
        this(alias, aliased, null);
    }

    private WorkflowVersion(Name alias, Table<WorkflowVersionRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""));
    }

    public <O extends Record> WorkflowVersion(Table<O> child, ForeignKey<O, WorkflowVersionRecord> key) {
        super(child, key, WORKFLOW_VERSION);
    }

    @Override
    public Schema getSchema() {
        return TexeraDb.TEXERA_DB;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.<Index>asList(Indexes.WORKFLOW_VERSION_PRIMARY, Indexes.WORKFLOW_VERSION_WID);
    }

    @Override
    public Identity<WorkflowVersionRecord, UInteger> getIdentity() {
        return Keys.IDENTITY_WORKFLOW_VERSION;
    }

    @Override
    public UniqueKey<WorkflowVersionRecord> getPrimaryKey() {
        return Keys.KEY_WORKFLOW_VERSION_PRIMARY;
    }

    @Override
    public List<UniqueKey<WorkflowVersionRecord>> getKeys() {
        return Arrays.<UniqueKey<WorkflowVersionRecord>>asList(Keys.KEY_WORKFLOW_VERSION_PRIMARY);
    }

    @Override
    public List<ForeignKey<WorkflowVersionRecord, ?>> getReferences() {
        return Arrays.<ForeignKey<WorkflowVersionRecord, ?>>asList(Keys.WORKFLOW_VERSION_IBFK_1);
    }

    public Workflow workflow() {
        return new Workflow(this, Keys.WORKFLOW_VERSION_IBFK_1);
    }

    @Override
    public WorkflowVersion as(String alias) {
        return new WorkflowVersion(DSL.name(alias), this);
    }

    @Override
    public WorkflowVersion as(Name alias) {
        return new WorkflowVersion(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public WorkflowVersion rename(String name) {
        return new WorkflowVersion(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public WorkflowVersion rename(Name name) {
        return new WorkflowVersion(name, null);
    }

    // -------------------------------------------------------------------------
    // Row4 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row4<UInteger, UInteger, String, Timestamp> fieldsRow() {
        return (Row4) super.fieldsRow();
    }
}
