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
import web.model.jooq.generated.tables.records.PublicProjectRecord;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class PublicProject extends TableImpl<PublicProjectRecord> {

    private static final long serialVersionUID = 823947565;

    /**
     * The reference instance of <code>texera_db.public_project</code>
     */
    public static final PublicProject PUBLIC_PROJECT = new PublicProject();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<PublicProjectRecord> getRecordType() {
        return PublicProjectRecord.class;
    }

    /**
     * The column <code>texera_db.public_project.pid</code>.
     */
    public final TableField<PublicProjectRecord, UInteger> PID = createField(DSL.name("pid"), org.jooq.impl.SQLDataType.INTEGERUNSIGNED.nullable(false), this, "");

    /**
     * The column <code>texera_db.public_project.uid</code>.
     */
    public final TableField<PublicProjectRecord, UInteger> UID = createField(DSL.name("uid"), org.jooq.impl.SQLDataType.INTEGERUNSIGNED, this, "");

    /**
     * Create a <code>texera_db.public_project</code> table reference
     */
    public PublicProject() {
        this(DSL.name("public_project"), null);
    }

    /**
     * Create an aliased <code>texera_db.public_project</code> table reference
     */
    public PublicProject(String alias) {
        this(DSL.name(alias), PUBLIC_PROJECT);
    }

    /**
     * Create an aliased <code>texera_db.public_project</code> table reference
     */
    public PublicProject(Name alias) {
        this(alias, PUBLIC_PROJECT);
    }

    private PublicProject(Name alias, Table<PublicProjectRecord> aliased) {
        this(alias, aliased, null);
    }

    private PublicProject(Name alias, Table<PublicProjectRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""));
    }

    public <O extends Record> PublicProject(Table<O> child, ForeignKey<O, PublicProjectRecord> key) {
        super(child, key, PUBLIC_PROJECT);
    }

    @Override
    public Schema getSchema() {
        return TexeraDb.TEXERA_DB;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.<Index>asList(Indexes.PUBLIC_PROJECT_PRIMARY);
    }

    @Override
    public UniqueKey<PublicProjectRecord> getPrimaryKey() {
        return Keys.KEY_PUBLIC_PROJECT_PRIMARY;
    }

    @Override
    public List<UniqueKey<PublicProjectRecord>> getKeys() {
        return Arrays.<UniqueKey<PublicProjectRecord>>asList(Keys.KEY_PUBLIC_PROJECT_PRIMARY);
    }

    @Override
    public List<ForeignKey<PublicProjectRecord, ?>> getReferences() {
        return Arrays.<ForeignKey<PublicProjectRecord, ?>>asList(Keys.PUBLIC_PROJECT_IBFK_1);
    }

    public Project project() {
        return new Project(this, Keys.PUBLIC_PROJECT_IBFK_1);
    }

    @Override
    public PublicProject as(String alias) {
        return new PublicProject(DSL.name(alias), this);
    }

    @Override
    public PublicProject as(Name alias) {
        return new PublicProject(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public PublicProject rename(String name) {
        return new PublicProject(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public PublicProject rename(Name name) {
        return new PublicProject(name, null);
    }

    // -------------------------------------------------------------------------
    // Row2 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row2<UInteger, UInteger> fieldsRow() {
        return (Row2) super.fieldsRow();
    }
}