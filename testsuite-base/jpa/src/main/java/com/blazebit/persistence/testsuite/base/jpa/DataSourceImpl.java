/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.testsuite.base.jpa;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 *
 * @author Christian Beikov
 * @since 1.2.0
 */
public class DataSourceImpl implements DataSource {

    private final String url;
    private final String username;
    private final String password;
    private final Consumer<Connection> connectionCustomizer;

    public DataSourceImpl(String url, String username, String password, Consumer<Connection> connectionCustomizer) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.connectionCustomizer = connectionCustomizer;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return getConnection(this.username, this.password);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection conn = DriverManager.getConnection(this.url, username, password);
        if (connectionCustomizer != null) {
            connectionCustomizer.accept(conn);
        }
        return conn;
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return DriverManager.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        DriverManager.setLogWriter(out);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return DriverManager.getLoginTimeout();
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        DriverManager.setLoginTimeout(seconds);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw new SQLException();
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    @Override
    public Logger getParentLogger() {
        return null;
    }
}
