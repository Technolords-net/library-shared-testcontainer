package net.technolords.tool.testcontainer;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SharedConnectionWrapper implements Connection {
    private static Logger LOGGER = LoggerFactory.getLogger(SharedConnectionWrapper.class);
    private final Connection delegate;
    private final Runnable closeCallBack;

    public SharedConnectionWrapper(Connection delegate, Runnable closeCallBack) {
        this.delegate = delegate;
        this.closeCallBack = closeCallBack;
    }

    @Override
    public Statement createStatement() throws SQLException {
        return this.delegate.createStatement();
    }

    @Override
    public PreparedStatement prepareStatement(String s) throws SQLException {
        return this.delegate.prepareStatement(s);
    }

    @Override
    public CallableStatement prepareCall(String s) throws SQLException {
        return this.delegate.prepareCall(s);
    }

    @Override
    public String nativeSQL(String s) throws SQLException {
        return this.delegate.nativeSQL(s);
    }

    @Override
    public void setAutoCommit(boolean b) throws SQLException {
        this.delegate.setAutoCommit(b);
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        return this.delegate.getAutoCommit();
    }

    @Override
    public void commit() throws SQLException {
        this.delegate.commit();
    }

    @Override
    public void rollback() throws SQLException {
        this.delegate.rollback();
    }

    @Override
    public void close() throws SQLException {
        this.delegate.commit();
        try {
            this.closeCallBack.run();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Override
    public boolean isClosed() throws SQLException {
        return this.delegate.isClosed();
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return this.delegate.getMetaData();
    }

    @Override
    public void setReadOnly(boolean b) throws SQLException {
        this.delegate.setReadOnly(b);
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return this.delegate.isReadOnly();
    }

    @Override
    public void setCatalog(String s) throws SQLException {
        this.delegate.setCatalog(s);
    }

    @Override
    public String getCatalog() throws SQLException {
        return this.delegate.getCatalog();
    }

    @Override
    public void setTransactionIsolation(int i) throws SQLException {
        this.delegate.setTransactionIsolation(i);
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        return this.delegate.getTransactionIsolation();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return this.delegate.getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        this.delegate.clearWarnings();
    }

    @Override
    public Statement createStatement(int i, int i1) throws SQLException {
        return this.delegate.createStatement(i, i1);
    }

    @Override
    public PreparedStatement prepareStatement(String s, int i, int i1) throws SQLException {
        return this.delegate.prepareStatement(s, i, i1);
    }

    @Override
    public CallableStatement prepareCall(String s, int i, int i1) throws SQLException {
        return this.delegate.prepareCall(s, i, i1);
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return this.delegate.getTypeMap();
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        this.delegate.setTypeMap(map);
    }

    @Override
    public void setHoldability(int i) throws SQLException {
        this.delegate.setHoldability(i);
    }

    @Override
    public int getHoldability() throws SQLException {
        return this.delegate.getHoldability();
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        return this.delegate.setSavepoint();
    }

    @Override
    public Savepoint setSavepoint(String s) throws SQLException {
        return this.delegate.setSavepoint(s);
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        this.delegate.rollback(savepoint);
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        this.delegate.releaseSavepoint(savepoint);
    }

    @Override
    public Statement createStatement(int i, int i1, int i2) throws SQLException {
        return this.delegate.createStatement(i, i1, i2);
    }

    @Override
    public PreparedStatement prepareStatement(String s, int i, int i1, int i2) throws SQLException {
        return this.delegate.prepareStatement(s, i, i1, i2);
    }

    @Override
    public CallableStatement prepareCall(String s, int i, int i1, int i2) throws SQLException {
        return this.delegate.prepareCall(s, i, i1, i2);
    }

    @Override
    public PreparedStatement prepareStatement(String s, int i) throws SQLException {
        return this.delegate.prepareStatement(s, i);
    }

    @Override
    public PreparedStatement prepareStatement(String s, int[] ints) throws SQLException {
        return this.delegate.prepareStatement(s, ints);
    }

    @Override
    public PreparedStatement prepareStatement(String s, String[] strings) throws SQLException {
        return this.delegate.prepareStatement(s, strings);
    }

    @Override
    public Clob createClob() throws SQLException {
        return this.delegate.createClob();
    }

    @Override
    public Blob createBlob() throws SQLException {
        return this.delegate.createBlob();
    }

    @Override
    public NClob createNClob() throws SQLException {
        return this.delegate.createNClob();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        return this.delegate.createSQLXML();
    }

    @Override
    public boolean isValid(int i) throws SQLException {
        return this.delegate.isValid(i);
    }

    @Override
    public void setClientInfo(String s, String s1) throws SQLClientInfoException {
        this.delegate.setClientInfo(s, s1);
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        this.delegate.setClientInfo(properties);
    }

    @Override
    public String getClientInfo(String s) throws SQLException {
        return this.delegate.getClientInfo(s);
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        return this.delegate.getClientInfo();
    }

    @Override
    public Array createArrayOf(String s, Object[] objects) throws SQLException {
        return this.delegate.createArrayOf(s, objects);
    }

    @Override
    public Struct createStruct(String s, Object[] objects) throws SQLException {
        return this.delegate.createStruct(s, objects);
    }

    @Override
    public void setSchema(String s) throws SQLException {
        this.delegate.setSchema(s);
    }

    @Override
    public String getSchema() throws SQLException {
        return this.delegate.getSchema();
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        this.delegate.abort(executor);
    }

    @Override
    public void setNetworkTimeout(Executor executor, int i) throws SQLException {
        this.delegate.setNetworkTimeout(executor, i);
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return this.delegate.getNetworkTimeout();
    }

    @Override
    public <T> T unwrap(Class<T> aClass) throws SQLException {
        return this.delegate.unwrap(aClass);
    }

    @Override
    public boolean isWrapperFor(Class<?> aClass) throws SQLException {
        return this.delegate.isWrapperFor(aClass);
    }
}
