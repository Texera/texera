/*
 * This file is generated by jOOQ.
 */
package edu.uci.ics.texera.web.model.jooq.generated.tables.pojos;


import edu.uci.ics.texera.web.model.jooq.generated.tables.interfaces.IEnvironment;

import java.sql.Timestamp;

import org.jooq.types.UInteger;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Environment implements IEnvironment {

    private static final long serialVersionUID = -372026697;

    private UInteger  eid;
    private UInteger  ownerUid;
    private String    name;
    private String    description;
    private Timestamp creationTime;

    public Environment() {}

    public Environment(IEnvironment value) {
        this.eid = value.getEid();
        this.ownerUid = value.getOwnerUid();
        this.name = value.getName();
        this.description = value.getDescription();
        this.creationTime = value.getCreationTime();
    }

    public Environment(
        UInteger  eid,
        UInteger  ownerUid,
        String    name,
        String    description,
        Timestamp creationTime
    ) {
        this.eid = eid;
        this.ownerUid = ownerUid;
        this.name = name;
        this.description = description;
        this.creationTime = creationTime;
    }

    @Override
    public UInteger getEid() {
        return this.eid;
    }

    @Override
    public void setEid(UInteger eid) {
        this.eid = eid;
    }

    @Override
    public UInteger getOwnerUid() {
        return this.ownerUid;
    }

    @Override
    public void setOwnerUid(UInteger ownerUid) {
        this.ownerUid = ownerUid;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public Timestamp getCreationTime() {
        return this.creationTime;
    }

    @Override
    public void setCreationTime(Timestamp creationTime) {
        this.creationTime = creationTime;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Environment (");

        sb.append(eid);
        sb.append(", ").append(ownerUid);
        sb.append(", ").append(name);
        sb.append(", ").append(description);
        sb.append(", ").append(creationTime);

        sb.append(")");
        return sb.toString();
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
}
