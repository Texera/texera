/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 * This file is generated by jOOQ.
 */
package edu.uci.ics.texera.dao.jooq.generated.tables.daos;


import edu.uci.ics.texera.dao.jooq.generated.enums.WorkflowComputingUnitTypeEnum;
import edu.uci.ics.texera.dao.jooq.generated.tables.WorkflowComputingUnit;
import edu.uci.ics.texera.dao.jooq.generated.tables.records.WorkflowComputingUnitRecord;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.jooq.Configuration;
import org.jooq.impl.DAOImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class WorkflowComputingUnitDao extends DAOImpl<WorkflowComputingUnitRecord, edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit, Integer> {

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
    public Integer getId(edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit object) {
        return object.getCuid();
    }

    /**
     * Fetch records that have <code>uid BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit> fetchRangeOfUid(Integer lowerInclusive, Integer upperInclusive) {
        return fetchRange(WorkflowComputingUnit.WORKFLOW_COMPUTING_UNIT.UID, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>uid IN (values)</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit> fetchByUid(Integer... values) {
        return fetch(WorkflowComputingUnit.WORKFLOW_COMPUTING_UNIT.UID, values);
    }

    /**
     * Fetch records that have <code>name BETWEEN lowerInclusive AND
     * upperInclusive</code>
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
     * Fetch records that have <code>cuid BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit> fetchRangeOfCuid(Integer lowerInclusive, Integer upperInclusive) {
        return fetchRange(WorkflowComputingUnit.WORKFLOW_COMPUTING_UNIT.CUID, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>cuid IN (values)</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit> fetchByCuid(Integer... values) {
        return fetch(WorkflowComputingUnit.WORKFLOW_COMPUTING_UNIT.CUID, values);
    }

    /**
     * Fetch a unique record that has <code>cuid = value</code>
     */
    public edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit fetchOneByCuid(Integer value) {
        return fetchOne(WorkflowComputingUnit.WORKFLOW_COMPUTING_UNIT.CUID, value);
    }

    /**
     * Fetch a unique record that has <code>cuid = value</code>
     */
    public Optional<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit> fetchOptionalByCuid(Integer value) {
        return fetchOptional(WorkflowComputingUnit.WORKFLOW_COMPUTING_UNIT.CUID, value);
    }

    /**
     * Fetch records that have <code>creation_time BETWEEN lowerInclusive AND
     * upperInclusive</code>
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
     * Fetch records that have <code>terminate_time BETWEEN lowerInclusive AND
     * upperInclusive</code>
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

    /**
     * Fetch records that have <code>type BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit> fetchRangeOfType(WorkflowComputingUnitTypeEnum lowerInclusive, WorkflowComputingUnitTypeEnum upperInclusive) {
        return fetchRange(WorkflowComputingUnit.WORKFLOW_COMPUTING_UNIT.TYPE, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>type IN (values)</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit> fetchByType(WorkflowComputingUnitTypeEnum... values) {
        return fetch(WorkflowComputingUnit.WORKFLOW_COMPUTING_UNIT.TYPE, values);
    }

    /**
     * Fetch records that have <code>uri BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit> fetchRangeOfUri(String lowerInclusive, String upperInclusive) {
        return fetchRange(WorkflowComputingUnit.WORKFLOW_COMPUTING_UNIT.URI, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>uri IN (values)</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit> fetchByUri(String... values) {
        return fetch(WorkflowComputingUnit.WORKFLOW_COMPUTING_UNIT.URI, values);
    }

    /**
     * Fetch records that have <code>resource BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit> fetchRangeOfResource(String lowerInclusive, String upperInclusive) {
        return fetchRange(WorkflowComputingUnit.WORKFLOW_COMPUTING_UNIT.RESOURCE, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>resource IN (values)</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.WorkflowComputingUnit> fetchByResource(String... values) {
        return fetch(WorkflowComputingUnit.WORKFLOW_COMPUTING_UNIT.RESOURCE, values);
    }
}
