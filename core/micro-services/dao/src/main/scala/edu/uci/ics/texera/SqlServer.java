package edu.uci.ics.texera;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

public final class SqlServer {
    public static final SQLDialect SQL_DIALECT = SQLDialect.MYSQL;
    private static final MysqlDataSource dataSource;
    public static DSLContext context;

    static {
        dataSource = new MysqlDataSource();
        dataSource.setUrl(DaoConfig.jdbcUrl());
        dataSource.setUser(DaoConfig.jdbcUsername());
        dataSource.setPassword(DaoConfig.jdbcPassword());
        context = DSL.using(dataSource, SQL_DIALECT);
    }

    public static DSLContext createDSLContext() {
        return context;
    }

    public static void replaceDSLContext(DSLContext newContext){
        context = newContext;
    }
}
