/*
 * This file is generated by jOOQ.
 */
package edu.uci.ics.texera.dao.jooq.generated.tables.records;


import edu.uci.ics.texera.dao.jooq.generated.enums.ProjectUserAccessPrivilege;
import edu.uci.ics.texera.dao.jooq.generated.tables.ProjectUserAccess;
import edu.uci.ics.texera.dao.jooq.generated.tables.interfaces.IProjectUserAccess;

import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.Record3;
import org.jooq.Row3;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.UInteger;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class ProjectUserAccessRecord extends UpdatableRecordImpl<ProjectUserAccessRecord> implements Record3<UInteger, UInteger, ProjectUserAccessPrivilege>, IProjectUserAccess {

    private static final long serialVersionUID = 193545410;

    /**
     * Setter for <code>texera_db.project_user_access.uid</code>.
     */
    @Override
    public void setUid(UInteger value) {
        set(0, value);
    }

    /**
     * Getter for <code>texera_db.project_user_access.uid</code>.
     */
    @Override
    public UInteger getUid() {
        return (UInteger) get(0);
    }

    /**
     * Setter for <code>texera_db.project_user_access.pid</code>.
     */
    @Override
    public void setPid(UInteger value) {
        set(1, value);
    }

    /**
     * Getter for <code>texera_db.project_user_access.pid</code>.
     */
    @Override
    public UInteger getPid() {
        return (UInteger) get(1);
    }

    /**
     * Setter for <code>texera_db.project_user_access.privilege</code>.
     */
    @Override
    public void setPrivilege(ProjectUserAccessPrivilege value) {
        set(2, value);
    }

    /**
     * Getter for <code>texera_db.project_user_access.privilege</code>.
     */
    @Override
    public ProjectUserAccessPrivilege getPrivilege() {
        return (ProjectUserAccessPrivilege) get(2);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record2<UInteger, UInteger> key() {
        return (Record2) super.key();
    }

    // -------------------------------------------------------------------------
    // Record3 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row3<UInteger, UInteger, ProjectUserAccessPrivilege> fieldsRow() {
        return (Row3) super.fieldsRow();
    }

    @Override
    public Row3<UInteger, UInteger, ProjectUserAccessPrivilege> valuesRow() {
        return (Row3) super.valuesRow();
    }

    @Override
    public Field<UInteger> field1() {
        return ProjectUserAccess.PROJECT_USER_ACCESS.UID;
    }

    @Override
    public Field<UInteger> field2() {
        return ProjectUserAccess.PROJECT_USER_ACCESS.PID;
    }

    @Override
    public Field<ProjectUserAccessPrivilege> field3() {
        return ProjectUserAccess.PROJECT_USER_ACCESS.PRIVILEGE;
    }

    @Override
    public UInteger component1() {
        return getUid();
    }

    @Override
    public UInteger component2() {
        return getPid();
    }

    @Override
    public ProjectUserAccessPrivilege component3() {
        return getPrivilege();
    }

    @Override
    public UInteger value1() {
        return getUid();
    }

    @Override
    public UInteger value2() {
        return getPid();
    }

    @Override
    public ProjectUserAccessPrivilege value3() {
        return getPrivilege();
    }

    @Override
    public ProjectUserAccessRecord value1(UInteger value) {
        setUid(value);
        return this;
    }

    @Override
    public ProjectUserAccessRecord value2(UInteger value) {
        setPid(value);
        return this;
    }

    @Override
    public ProjectUserAccessRecord value3(ProjectUserAccessPrivilege value) {
        setPrivilege(value);
        return this;
    }

    @Override
    public ProjectUserAccessRecord values(UInteger value1, UInteger value2, ProjectUserAccessPrivilege value3) {
        value1(value1);
        value2(value2);
        value3(value3);
        return this;
    }

    // -------------------------------------------------------------------------
    // FROM and INTO
    // -------------------------------------------------------------------------

    @Override
    public void from(IProjectUserAccess from) {
        setUid(from.getUid());
        setPid(from.getPid());
        setPrivilege(from.getPrivilege());
    }

    @Override
    public <E extends IProjectUserAccess> E into(E into) {
        into.from(this);
        return into;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached ProjectUserAccessRecord
     */
    public ProjectUserAccessRecord() {
        super(ProjectUserAccess.PROJECT_USER_ACCESS);
    }

    /**
     * Create a detached, initialised ProjectUserAccessRecord
     */
    public ProjectUserAccessRecord(UInteger uid, UInteger pid, ProjectUserAccessPrivilege privilege) {
        super(ProjectUserAccess.PROJECT_USER_ACCESS);

        set(0, uid);
        set(1, pid);
        set(2, privilege);
    }
}
