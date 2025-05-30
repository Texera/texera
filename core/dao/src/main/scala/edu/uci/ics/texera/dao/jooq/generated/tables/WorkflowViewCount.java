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
package edu.uci.ics.texera.dao.jooq.generated.tables;


import edu.uci.ics.texera.dao.jooq.generated.Keys;
import edu.uci.ics.texera.dao.jooq.generated.TexeraDb;
import edu.uci.ics.texera.dao.jooq.generated.tables.records.WorkflowViewCountRecord;

import java.util.Arrays;
import java.util.List;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Row2;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class WorkflowViewCount extends TableImpl<WorkflowViewCountRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>texera_db.workflow_view_count</code>
     */
    public static final WorkflowViewCount WORKFLOW_VIEW_COUNT = new WorkflowViewCount();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<WorkflowViewCountRecord> getRecordType() {
        return WorkflowViewCountRecord.class;
    }

    /**
     * The column <code>texera_db.workflow_view_count.wid</code>.
     */
    public final TableField<WorkflowViewCountRecord, Integer> WID = createField(DSL.name("wid"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>texera_db.workflow_view_count.view_count</code>.
     */
    public final TableField<WorkflowViewCountRecord, Integer> VIEW_COUNT = createField(DSL.name("view_count"), SQLDataType.INTEGER.nullable(false).defaultValue(DSL.field("0", SQLDataType.INTEGER)), this, "");

    private WorkflowViewCount(Name alias, Table<WorkflowViewCountRecord> aliased) {
        this(alias, aliased, null);
    }

    private WorkflowViewCount(Name alias, Table<WorkflowViewCountRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>texera_db.workflow_view_count</code> table
     * reference
     */
    public WorkflowViewCount(String alias) {
        this(DSL.name(alias), WORKFLOW_VIEW_COUNT);
    }

    /**
     * Create an aliased <code>texera_db.workflow_view_count</code> table
     * reference
     */
    public WorkflowViewCount(Name alias) {
        this(alias, WORKFLOW_VIEW_COUNT);
    }

    /**
     * Create a <code>texera_db.workflow_view_count</code> table reference
     */
    public WorkflowViewCount() {
        this(DSL.name("workflow_view_count"), null);
    }

    public <O extends Record> WorkflowViewCount(Table<O> child, ForeignKey<O, WorkflowViewCountRecord> key) {
        super(child, key, WORKFLOW_VIEW_COUNT);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : TexeraDb.TEXERA_DB;
    }

    @Override
    public UniqueKey<WorkflowViewCountRecord> getPrimaryKey() {
        return Keys.WORKFLOW_VIEW_COUNT_PKEY;
    }

    @Override
    public List<ForeignKey<WorkflowViewCountRecord, ?>> getReferences() {
        return Arrays.asList(Keys.WORKFLOW_VIEW_COUNT__WORKFLOW_VIEW_COUNT_WID_FKEY);
    }

    private transient Workflow _workflow;

    /**
     * Get the implicit join path to the <code>texera_db.workflow</code> table.
     */
    public Workflow workflow() {
        if (_workflow == null)
            _workflow = new Workflow(this, Keys.WORKFLOW_VIEW_COUNT__WORKFLOW_VIEW_COUNT_WID_FKEY);

        return _workflow;
    }

    @Override
    public WorkflowViewCount as(String alias) {
        return new WorkflowViewCount(DSL.name(alias), this);
    }

    @Override
    public WorkflowViewCount as(Name alias) {
        return new WorkflowViewCount(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public WorkflowViewCount rename(String name) {
        return new WorkflowViewCount(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public WorkflowViewCount rename(Name name) {
        return new WorkflowViewCount(name, null);
    }

    // -------------------------------------------------------------------------
    // Row2 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row2<Integer, Integer> fieldsRow() {
        return (Row2) super.fieldsRow();
    }
}
