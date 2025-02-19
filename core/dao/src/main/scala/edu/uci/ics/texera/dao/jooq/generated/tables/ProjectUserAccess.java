/*
 * This file is generated by jOOQ.
 */
package edu.uci.ics.texera.dao.jooq.generated.tables;


import edu.uci.ics.texera.dao.jooq.generated.Indexes;
import edu.uci.ics.texera.dao.jooq.generated.Keys;
import edu.uci.ics.texera.dao.jooq.generated.TexeraDb;
import edu.uci.ics.texera.dao.jooq.generated.enums.ProjectUserAccessPrivilege;
import edu.uci.ics.texera.dao.jooq.generated.tables.records.ProjectUserAccessRecord;

import java.util.Arrays;
import java.util.List;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Row3;
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
public class ProjectUserAccess extends TableImpl<ProjectUserAccessRecord> {

    private static final long serialVersionUID = 1043932682;

    /**
     * The reference instance of <code>texera_db.project_user_access</code>
     */
    public static final ProjectUserAccess PROJECT_USER_ACCESS = new ProjectUserAccess();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<ProjectUserAccessRecord> getRecordType() {
        return ProjectUserAccessRecord.class;
    }

    /**
     * The column <code>texera_db.project_user_access.uid</code>.
     */
    public final TableField<ProjectUserAccessRecord, UInteger> UID = createField(DSL.name("uid"), org.jooq.impl.SQLDataType.INTEGERUNSIGNED.nullable(false), this, "");

    /**
     * The column <code>texera_db.project_user_access.pid</code>.
     */
    public final TableField<ProjectUserAccessRecord, UInteger> PID = createField(DSL.name("pid"), org.jooq.impl.SQLDataType.INTEGERUNSIGNED.nullable(false), this, "");

    /**
     * The column <code>texera_db.project_user_access.privilege</code>.
     */
    public final TableField<ProjectUserAccessRecord, ProjectUserAccessPrivilege> PRIVILEGE = createField(DSL.name("privilege"), org.jooq.impl.SQLDataType.VARCHAR(5).nullable(false).defaultValue(org.jooq.impl.DSL.inline("NONE", org.jooq.impl.SQLDataType.VARCHAR)).asEnumDataType(edu.uci.ics.texera.dao.jooq.generated.enums.ProjectUserAccessPrivilege.class), this, "");

    /**
     * Create a <code>texera_db.project_user_access</code> table reference
     */
    public ProjectUserAccess() {
        this(DSL.name("project_user_access"), null);
    }

    /**
     * Create an aliased <code>texera_db.project_user_access</code> table reference
     */
    public ProjectUserAccess(String alias) {
        this(DSL.name(alias), PROJECT_USER_ACCESS);
    }

    /**
     * Create an aliased <code>texera_db.project_user_access</code> table reference
     */
    public ProjectUserAccess(Name alias) {
        this(alias, PROJECT_USER_ACCESS);
    }

    private ProjectUserAccess(Name alias, Table<ProjectUserAccessRecord> aliased) {
        this(alias, aliased, null);
    }

    private ProjectUserAccess(Name alias, Table<ProjectUserAccessRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""));
    }

    public <O extends Record> ProjectUserAccess(Table<O> child, ForeignKey<O, ProjectUserAccessRecord> key) {
        super(child, key, PROJECT_USER_ACCESS);
    }

    @Override
    public Schema getSchema() {
        return TexeraDb.TEXERA_DB;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.<Index>asList(Indexes.PROJECT_USER_ACCESS_PID, Indexes.PROJECT_USER_ACCESS_PRIMARY);
    }

    @Override
    public UniqueKey<ProjectUserAccessRecord> getPrimaryKey() {
        return Keys.KEY_PROJECT_USER_ACCESS_PRIMARY;
    }

    @Override
    public List<UniqueKey<ProjectUserAccessRecord>> getKeys() {
        return Arrays.<UniqueKey<ProjectUserAccessRecord>>asList(Keys.KEY_PROJECT_USER_ACCESS_PRIMARY);
    }

    @Override
    public List<ForeignKey<ProjectUserAccessRecord, ?>> getReferences() {
        return Arrays.<ForeignKey<ProjectUserAccessRecord, ?>>asList(Keys.PROJECT_USER_ACCESS_IBFK_1, Keys.PROJECT_USER_ACCESS_IBFK_2);
    }

    public User user() {
        return new User(this, Keys.PROJECT_USER_ACCESS_IBFK_1);
    }

    public Project project() {
        return new Project(this, Keys.PROJECT_USER_ACCESS_IBFK_2);
    }

    @Override
    public ProjectUserAccess as(String alias) {
        return new ProjectUserAccess(DSL.name(alias), this);
    }

    @Override
    public ProjectUserAccess as(Name alias) {
        return new ProjectUserAccess(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public ProjectUserAccess rename(String name) {
        return new ProjectUserAccess(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public ProjectUserAccess rename(Name name) {
        return new ProjectUserAccess(name, null);
    }

    // -------------------------------------------------------------------------
    // Row3 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row3<UInteger, UInteger, ProjectUserAccessPrivilege> fieldsRow() {
        return (Row3) super.fieldsRow();
    }
}
