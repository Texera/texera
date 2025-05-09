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


import edu.uci.ics.texera.dao.jooq.generated.tables.Workflow;
import edu.uci.ics.texera.dao.jooq.generated.tables.records.WorkflowRecord;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.jooq.Configuration;
import org.jooq.impl.DAOImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class WorkflowDao extends DAOImpl<WorkflowRecord, edu.uci.ics.texera.dao.jooq.generated.tables.pojos.Workflow, Integer> {

    /**
     * Create a new WorkflowDao without any configuration
     */
    public WorkflowDao() {
        super(Workflow.WORKFLOW, edu.uci.ics.texera.dao.jooq.generated.tables.pojos.Workflow.class);
    }

    /**
     * Create a new WorkflowDao with an attached configuration
     */
    public WorkflowDao(Configuration configuration) {
        super(Workflow.WORKFLOW, edu.uci.ics.texera.dao.jooq.generated.tables.pojos.Workflow.class, configuration);
    }

    @Override
    public Integer getId(edu.uci.ics.texera.dao.jooq.generated.tables.pojos.Workflow object) {
        return object.getWid();
    }

    /**
     * Fetch records that have <code>wid BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.Workflow> fetchRangeOfWid(Integer lowerInclusive, Integer upperInclusive) {
        return fetchRange(Workflow.WORKFLOW.WID, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>wid IN (values)</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.Workflow> fetchByWid(Integer... values) {
        return fetch(Workflow.WORKFLOW.WID, values);
    }

    /**
     * Fetch a unique record that has <code>wid = value</code>
     */
    public edu.uci.ics.texera.dao.jooq.generated.tables.pojos.Workflow fetchOneByWid(Integer value) {
        return fetchOne(Workflow.WORKFLOW.WID, value);
    }

    /**
     * Fetch a unique record that has <code>wid = value</code>
     */
    public Optional<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.Workflow> fetchOptionalByWid(Integer value) {
        return fetchOptional(Workflow.WORKFLOW.WID, value);
    }

    /**
     * Fetch records that have <code>name BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.Workflow> fetchRangeOfName(String lowerInclusive, String upperInclusive) {
        return fetchRange(Workflow.WORKFLOW.NAME, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>name IN (values)</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.Workflow> fetchByName(String... values) {
        return fetch(Workflow.WORKFLOW.NAME, values);
    }

    /**
     * Fetch records that have <code>description BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.Workflow> fetchRangeOfDescription(String lowerInclusive, String upperInclusive) {
        return fetchRange(Workflow.WORKFLOW.DESCRIPTION, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>description IN (values)</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.Workflow> fetchByDescription(String... values) {
        return fetch(Workflow.WORKFLOW.DESCRIPTION, values);
    }

    /**
     * Fetch records that have <code>content BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.Workflow> fetchRangeOfContent(String lowerInclusive, String upperInclusive) {
        return fetchRange(Workflow.WORKFLOW.CONTENT, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>content IN (values)</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.Workflow> fetchByContent(String... values) {
        return fetch(Workflow.WORKFLOW.CONTENT, values);
    }

    /**
     * Fetch records that have <code>creation_time BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.Workflow> fetchRangeOfCreationTime(Timestamp lowerInclusive, Timestamp upperInclusive) {
        return fetchRange(Workflow.WORKFLOW.CREATION_TIME, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>creation_time IN (values)</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.Workflow> fetchByCreationTime(Timestamp... values) {
        return fetch(Workflow.WORKFLOW.CREATION_TIME, values);
    }

    /**
     * Fetch records that have <code>last_modified_time BETWEEN lowerInclusive
     * AND upperInclusive</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.Workflow> fetchRangeOfLastModifiedTime(Timestamp lowerInclusive, Timestamp upperInclusive) {
        return fetchRange(Workflow.WORKFLOW.LAST_MODIFIED_TIME, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>last_modified_time IN (values)</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.Workflow> fetchByLastModifiedTime(Timestamp... values) {
        return fetch(Workflow.WORKFLOW.LAST_MODIFIED_TIME, values);
    }

    /**
     * Fetch records that have <code>is_public BETWEEN lowerInclusive AND
     * upperInclusive</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.Workflow> fetchRangeOfIsPublic(Boolean lowerInclusive, Boolean upperInclusive) {
        return fetchRange(Workflow.WORKFLOW.IS_PUBLIC, lowerInclusive, upperInclusive);
    }

    /**
     * Fetch records that have <code>is_public IN (values)</code>
     */
    public List<edu.uci.ics.texera.dao.jooq.generated.tables.pojos.Workflow> fetchByIsPublic(Boolean... values) {
        return fetch(Workflow.WORKFLOW.IS_PUBLIC, values);
    }
}
