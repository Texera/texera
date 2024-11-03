/*
 * This file is generated by jOOQ.
 */
package web.model.jooq.generated.tables;


import java.util.Arrays;
import java.util.List;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Row2;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.TableImpl;
import org.jooq.types.UInteger;

import web.model.jooq.generated.Indexes;
import web.model.jooq.generated.Keys;
import web.model.jooq.generated.TexeraDb;
import web.model.jooq.generated.tables.records.WorkflowOfProjectRecord;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class WorkflowOfProject extends TableImpl<WorkflowOfProjectRecord> {

    private static final long serialVersionUID = -295963659;

    /**
     * The reference instance of <code>texera_db.workflow_of_project</code>
     */
    public static final WorkflowOfProject WORKFLOW_OF_PROJECT = new WorkflowOfProject();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<WorkflowOfProjectRecord> getRecordType() {
        return WorkflowOfProjectRecord.class;
    }

    /**
     * The column <code>texera_db.workflow_of_project.wid</code>.
     */
    public final TableField<WorkflowOfProjectRecord, UInteger> WID = createField(DSL.name("wid"), org.jooq.impl.SQLDataType.INTEGERUNSIGNED.nullable(false), this, "");

    /**
     * The column <code>texera_db.workflow_of_project.pid</code>.
     */
    public final TableField<WorkflowOfProjectRecord, UInteger> PID = createField(DSL.name("pid"), org.jooq.impl.SQLDataType.INTEGERUNSIGNED.nullable(false), this, "");

    /**
     * Create a <code>texera_db.workflow_of_project</code> table reference
     */
    public WorkflowOfProject() {
        this(DSL.name("workflow_of_project"), null);
    }

    /**
     * Create an aliased <code>texera_db.workflow_of_project</code> table reference
     */
    public WorkflowOfProject(String alias) {
        this(DSL.name(alias), WORKFLOW_OF_PROJECT);
    }

    /**
     * Create an aliased <code>texera_db.workflow_of_project</code> table reference
     */
    public WorkflowOfProject(Name alias) {
        this(alias, WORKFLOW_OF_PROJECT);
    }

    private WorkflowOfProject(Name alias, Table<WorkflowOfProjectRecord> aliased) {
        this(alias, aliased, null);
    }

    private WorkflowOfProject(Name alias, Table<WorkflowOfProjectRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""));
    }

    public <O extends Record> WorkflowOfProject(Table<O> child, ForeignKey<O, WorkflowOfProjectRecord> key) {
        super(child, key, WORKFLOW_OF_PROJECT);
    }

    @Override
    public Schema getSchema() {
        return TexeraDb.TEXERA_DB;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.<Index>asList(Indexes.WORKFLOW_OF_PROJECT_PID, Indexes.WORKFLOW_OF_PROJECT_PRIMARY);
    }

    @Override
    public UniqueKey<WorkflowOfProjectRecord> getPrimaryKey() {
        return Keys.KEY_WORKFLOW_OF_PROJECT_PRIMARY;
    }

    @Override
    public List<UniqueKey<WorkflowOfProjectRecord>> getKeys() {
        return Arrays.<UniqueKey<WorkflowOfProjectRecord>>asList(Keys.KEY_WORKFLOW_OF_PROJECT_PRIMARY);
    }

    @Override
    public List<ForeignKey<WorkflowOfProjectRecord, ?>> getReferences() {
        return Arrays.<ForeignKey<WorkflowOfProjectRecord, ?>>asList(Keys.WORKFLOW_OF_PROJECT_IBFK_1, Keys.WORKFLOW_OF_PROJECT_IBFK_2);
    }

    public Workflow workflow() {
        return new Workflow(this, Keys.WORKFLOW_OF_PROJECT_IBFK_1);
    }

    public Project project() {
        return new Project(this, Keys.WORKFLOW_OF_PROJECT_IBFK_2);
    }

    @Override
    public WorkflowOfProject as(String alias) {
        return new WorkflowOfProject(DSL.name(alias), this);
    }

    @Override
    public WorkflowOfProject as(Name alias) {
        return new WorkflowOfProject(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public WorkflowOfProject rename(String name) {
        return new WorkflowOfProject(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public WorkflowOfProject rename(Name name) {
        return new WorkflowOfProject(name, null);
    }

    // -------------------------------------------------------------------------
    // Row2 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row2<UInteger, UInteger> fieldsRow() {
        return (Row2) super.fieldsRow();
    }
}