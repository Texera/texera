/*
 * This file is generated by jOOQ.
 */
package edu.uci.ics.texera.dao.jooq.generated.tables.daos;


import edu.uci.ics.texera.dao.jooq.generated.tables.WorkflowComputingUnit;
import edu.uci.ics.texera.dao.jooq.generated.tables.records.WorkflowComputingUnitRecord;

import java.sql.Timestamp;
import java.util.List;

import org.jooq.Configuration;
import org.jooq.impl.DAOImpl;
import org.jooq.types.UInteger;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class WorkflowComputingUnitDao extends DAOImpl<WorkflowComputingUnitRecord, edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit, UInteger> {

    /**
     * Create a new WorkflowComputingUnitDao without any configuration
     */
    public WorkflowComputingUnitDao() {
        super(WorkflowComputingUnit.WORKFLOW_COMPUTING_UNIT, edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit.class);
    }

    /**
     * Create a new WorkflowComputingUnitDao with an attached configuration
     */
    public WorkflowComputingUnitDao(Configuration configuration) {
        super(WorkflowComputingUnit.WORKFLOW_COMPUTING_UNIT, edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit.class, configuration);
    }

    @Override
    public UInteger getId(edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit object) {
        return object.getCuid();
    }

    /**
     * Fetch records that have <code>uid BETWEEN lowerInclusive AND upperInclusive</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit> fetchRangeOfUid(UInteger lowerInclusive, UInteger upperInclusive) {
        return fetchRange(WorkflowComputingUnit.WORKFLOW_COMPUTING_UNIT.UID, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>uid IN (values)</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit> fetchByUid(UInteger... values) {
        return fetch(WorkflowComputingUnit.WORKFLOW_COMPUTING_UNIT.UID, values);
    }

    /**
     * Fetch records that have <code>name BETWEEN lowerInclusive AND upperInclusive</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit> fetchRangeOfName(String lowerInclusive, String upperInclusive) {
        return fetchRange(WorkflowComputingUnit.WORKFLOW_COMPUTING_UNIT.NAME, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>name IN (values)</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit> fetchByName(String... values) {
        return fetch(WorkflowComputingUnit.WORKFLOW_COMPUTING_UNIT.NAME, values);
    }

    /**
     * Fetch records that have <code>cuid BETWEEN lowerInclusive AND upperInclusive</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit> fetchRangeOfCuid(UInteger lowerInclusive, UInteger upperInclusive) {
        return fetchRange(WorkflowComputingUnit.WORKFLOW_COMPUTING_UNIT.CUID, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>cuid IN (values)</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit> fetchByCuid(UInteger... values) {
        return fetch(WorkflowComputingUnit.WORKFLOW_COMPUTING_UNIT.CUID, values);
    }

    /**
     * Fetch a unique record that has <code>cuid = value</code>
     */
    public edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit fetchOneByCuid(UInteger value) {
        return fetchOne(WorkflowComputingUnit.WORKFLOW_COMPUTING_UNIT.CUID, value);
    }

    /**
     * Fetch records that have <code>creation_time BETWEEN lowerInclusive AND upperInclusive</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit> fetchRangeOfCreationTime(Timestamp lowerInclusive, Timestamp upperInclusive) {
        return fetchRange(WorkflowComputingUnit.WORKFLOW_COMPUTING_UNIT.CREATION_TIME, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>creation_time IN (values)</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit> fetchByCreationTime(Timestamp... values) {
        return fetch(WorkflowComputingUnit.WORKFLOW_COMPUTING_UNIT.CREATION_TIME, values);
    }

    /**
     * Fetch records that have <code>terminate_time BETWEEN lowerInclusive AND upperInclusive</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit> fetchRangeOfTerminateTime(Timestamp lowerInclusive, Timestamp upperInclusive) {
        return fetchRange(WorkflowComputingUnit.WORKFLOW_COMPUTING_UNIT.TERMINATE_TIME, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>terminate_time IN (values)</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit> fetchByTerminateTime(Timestamp... values) {
        return fetch(WorkflowComputingUnit.WORKFLOW_COMPUTING_UNIT.TERMINATE_TIME, values);
    }
}