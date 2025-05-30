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
import edu.uci.ics.texera.dao.jooq.generated.tables.records.DatasetVersionRecord;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Identity;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Row6;
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
public class DatasetVersion extends TableImpl<DatasetVersionRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>texera_db.dataset_version</code>
     */
    public static final DatasetVersion DATASET_VERSION = new DatasetVersion();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<DatasetVersionRecord> getRecordType() {
        return DatasetVersionRecord.class;
    }

    /**
     * The column <code>texera_db.dataset_version.dvid</code>.
     */
    public final TableField<DatasetVersionRecord, Integer> DVID = createField(DSL.name("dvid"), SQLDataType.INTEGER.nullable(false).identity(true), this, "");

    /**
     * The column <code>texera_db.dataset_version.did</code>.
     */
    public final TableField<DatasetVersionRecord, Integer> DID = createField(DSL.name("did"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>texera_db.dataset_version.creator_uid</code>.
     */
    public final TableField<DatasetVersionRecord, Integer> CREATOR_UID = createField(DSL.name("creator_uid"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>texera_db.dataset_version.name</code>.
     */
    public final TableField<DatasetVersionRecord, String> NAME = createField(DSL.name("name"), SQLDataType.VARCHAR(128).nullable(false), this, "");

    /**
     * The column <code>texera_db.dataset_version.version_hash</code>.
     */
    public final TableField<DatasetVersionRecord, String> VERSION_HASH = createField(DSL.name("version_hash"), SQLDataType.VARCHAR(64).nullable(false), this, "");

    /**
     * The column <code>texera_db.dataset_version.creation_time</code>.
     */
    public final TableField<DatasetVersionRecord, Timestamp> CREATION_TIME = createField(DSL.name("creation_time"), SQLDataType.TIMESTAMP(0).nullable(false).defaultValue(DSL.field("CURRENT_TIMESTAMP", SQLDataType.TIMESTAMP)), this, "");

    private DatasetVersion(Name alias, Table<DatasetVersionRecord> aliased) {
        this(alias, aliased, null);
    }

    private DatasetVersion(Name alias, Table<DatasetVersionRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>texera_db.dataset_version</code> table reference
     */
    public DatasetVersion(String alias) {
        this(DSL.name(alias), DATASET_VERSION);
    }

    /**
     * Create an aliased <code>texera_db.dataset_version</code> table reference
     */
    public DatasetVersion(Name alias) {
        this(alias, DATASET_VERSION);
    }

    /**
     * Create a <code>texera_db.dataset_version</code> table reference
     */
    public DatasetVersion() {
        this(DSL.name("dataset_version"), null);
    }

    public <O extends Record> DatasetVersion(Table<O> child, ForeignKey<O, DatasetVersionRecord> key) {
        super(child, key, DATASET_VERSION);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : TexeraDb.TEXERA_DB;
    }

    @Override
    public Identity<DatasetVersionRecord, Integer> getIdentity() {
        return (Identity<DatasetVersionRecord, Integer>) super.getIdentity();
    }

    @Override
    public UniqueKey<DatasetVersionRecord> getPrimaryKey() {
        return Keys.DATASET_VERSION_PKEY;
    }

    @Override
    public List<ForeignKey<DatasetVersionRecord, ?>> getReferences() {
        return Arrays.asList(Keys.DATASET_VERSION__DATASET_VERSION_DID_FKEY);
    }

    private transient Dataset _dataset;

    /**
     * Get the implicit join path to the <code>texera_db.dataset</code> table.
     */
    public Dataset dataset() {
        if (_dataset == null)
            _dataset = new Dataset(this, Keys.DATASET_VERSION__DATASET_VERSION_DID_FKEY);

        return _dataset;
    }

    @Override
    public DatasetVersion as(String alias) {
        return new DatasetVersion(DSL.name(alias), this);
    }

    @Override
    public DatasetVersion as(Name alias) {
        return new DatasetVersion(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public DatasetVersion rename(String name) {
        return new DatasetVersion(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public DatasetVersion rename(Name name) {
        return new DatasetVersion(name, null);
    }

    // -------------------------------------------------------------------------
    // Row6 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row6<Integer, Integer, Integer, String, String, Timestamp> fieldsRow() {
        return (Row6) super.fieldsRow();
    }
}
