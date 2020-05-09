package net.technolords.tool.testcontainer;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.JdbcDatabaseContainerProvider;
import org.testcontainers.delegate.DatabaseDelegate;
import org.testcontainers.ext.ScriptUtils;
import org.testcontainers.jdbc.ConnectionUrl;
import org.testcontainers.jdbc.JdbcDatabaseDelegate;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;

public class SharedContainerDatabaseDriver implements Driver  {
    private static Logger LOGGER = LoggerFactory.getLogger(SharedContainerDatabaseDriver.class);
    private static final Map<String, Set<Connection>> containerConnections = new ConcurrentHashMap<>();
    private static final Map<String, JdbcDatabaseContainer> jdbcUrlContainerCache = new ConcurrentHashMap<>();
    private static final Set<String> initializedScripts = ConcurrentHashMap.newKeySet();
    private static final String JDBC_WITH_TC_PREFIX = "jdbc:tc:";
    private static final String FILE_PATH_PREFIX = "file:";
    private static final String SQL_DROP_FLYWAY_TABLE_IF_EXISTS = "DROP TABLE IF EXISTS flyway_schema_history";
    private Driver delegate;

    static {
        try {
            LOGGER.info("About to register shared container driver (supporting jdbc:tc) ...");
            DriverManager.registerDriver(new SharedContainerDatabaseDriver());
            LOGGER.info("... success");
        } catch (SQLException e) {
            LOGGER.warn("Failed to register driver", e);
        }
    }

    static JdbcDatabaseContainer getContainer(String jdbcUrl) {
        LOGGER.info("About to fetch container with URL: {}", jdbcUrl);
        return jdbcUrlContainerCache.get(jdbcUrl);
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return url.startsWith(JDBC_WITH_TC_PREFIX);
    }

    @Override
    public Connection connect(String url, Properties properties) throws SQLException {
        LOGGER.debug("Current cache size: {}", jdbcUrlContainerCache.size());
        if (!acceptsURL(url)) {
            return null;
        }
        ConnectionUrl connectionUrl = ConnectionUrl.newInstance(url);
        String queryString = connectionUrl.getQueryString().orElse("");
        LOGGER.debug("Checking cache for key: {} -> with query: {}", connectionUrl.getDatabaseType(), queryString);
        JdbcDatabaseContainer container = jdbcUrlContainerCache.get(connectionUrl.getDatabaseType());
        if (container == null) {
            LOGGER.debug("... not found -> creating new instance");
            Map<String, String> parameters = connectionUrl.getContainerParameters();
            ServiceLoader<JdbcDatabaseContainerProvider> databaseContainers = ServiceLoader.load(JdbcDatabaseContainerProvider.class);
            for (JdbcDatabaseContainerProvider candidateContainerType : databaseContainers) {
                LOGGER.debug("Probing candidate ({}) for database type: {}", candidateContainerType, connectionUrl.getDatabaseType());
                if (candidateContainerType.supports(connectionUrl.getDatabaseType())) {
                    LOGGER.info("Creating new instance with connection url: {} -> URL: {}", connectionUrl, connectionUrl.getUrl());
                    container = candidateContainerType.newInstance(connectionUrl);
                    container.withTmpFs(connectionUrl.getTmpfsOptions());
                    delegate = container.getJdbcDriverInstance();
                }
            }
            if (container == null) {
                throw new UnsupportedOperationException("Database name " + connectionUrl.getDatabaseType() + " not supported");
            }
            jdbcUrlContainerCache.put(connectionUrl.getDatabaseType(), container);
            container.setParameters(parameters);
            container.start();
            LOGGER.debug("... container started...");
        }
        Connection connection = container.createConnection(queryString);
        LOGGER.debug("Connection to container: {} -> URL {}", connection, connection.getMetaData().getURL());
        DatabaseDelegate databaseDelegate = new JdbcDatabaseDelegate(container, queryString);
        runInitScriptIfRequired(connectionUrl, databaseDelegate);
        runClearFlywayIfRequired(connection);
        runInitFunctionIfRequired(connectionUrl, connection);
        return wrapConnection(connection, container, connectionUrl);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties properties) throws SQLException {
        return this.delegate.getPropertyInfo(url, properties);
    }

    @Override
    public int getMajorVersion() {
        return this.delegate.getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        return this.delegate.getMinorVersion();
    }

    @Override
    public boolean jdbcCompliant() {
        return this.delegate.jdbcCompliant();
    }

    @Override
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return this.delegate.getParentLogger();
    }

    public static void killContainers() {
        jdbcUrlContainerCache.values().forEach(JdbcDatabaseContainer::stop);
        jdbcUrlContainerCache.clear();
        containerConnections.clear();
    }

    public static void killContainer(String jdbcUrl) {
        JdbcDatabaseContainer container = jdbcUrlContainerCache.get(jdbcUrl);
        if (container != null) {
            container.stop();
            jdbcUrlContainerCache.remove(jdbcUrl);
            containerConnections.remove(container.getContainerId());
        }
    }

    private void runInitScriptIfRequired(final ConnectionUrl connectionUrl, DatabaseDelegate databaseDelegate) throws SQLException {
        if (connectionUrl.getInitScriptPath().isPresent()) {
            String initScriptPath = connectionUrl.getInitScriptPath().get();
            LOGGER.debug("About to execute script: {}", initScriptPath);
            if (initializedScripts.contains(initScriptPath)) {
                LOGGER.debug("... aborted, already executed");
                return;
            }
            LOGGER.debug("... script execution added to history");
            initializedScripts.add(initScriptPath);
            try {
                URL resource;
                if (initScriptPath.startsWith(FILE_PATH_PREFIX)) {
                    //relative workdir path
                    resource = new URL(initScriptPath);
                } else {
                    //classpath resource
                    resource = Thread.currentThread().getContextClassLoader().getResource(initScriptPath);
                }
                if (resource == null) {
                    LOGGER.warn("Could not load classpath init script: {}", initScriptPath);
                    throw new SQLException("Could not load classpath init script: " + initScriptPath + ". Resource not found.");
                }

                String sql = IOUtils.toString(resource, StandardCharsets.UTF_8);
                LOGGER.debug("Running script {}", sql);
                ScriptUtils.executeDatabaseScript(databaseDelegate, initScriptPath, sql);
            } catch (IOException e) {
                LOGGER.warn("Could not load classpath init script: {}", initScriptPath);
                throw new SQLException("Could not load classpath init script: " + initScriptPath, e);
            } catch (ScriptException e) {
                LOGGER.error("Error while executing init script: {}", initScriptPath, e);
                throw new SQLException("Error while executing init script: " + initScriptPath, e);
            }
        }
    }

    private void runClearFlywayIfRequired(final Connection connection) throws SQLException {
        PreparedStatement preparedStatement = connection.prepareStatement(SQL_DROP_FLYWAY_TABLE_IF_EXISTS);
        preparedStatement.execute();
    }

    private void runInitFunctionIfRequired(final ConnectionUrl connectionUrl, Connection connection) throws SQLException {
        if (connectionUrl.getInitFunction().isPresent()) {
            String className = connectionUrl.getInitFunction().get().getClassName();
            String methodName = connectionUrl.getInitFunction().get().getMethodName();

            try {
                Class<?> initFunctionClazz = Class.forName(className);
                Method method = initFunctionClazz.getMethod(methodName, Connection.class);

                method.invoke(null, connection);
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                LOGGER.error("Error while executing init function: {}::{}", className, methodName, e);
                throw new SQLException("Error while executing init function: " + className + "::" + methodName, e);
            }
        }
    }

    private Connection wrapConnection(final Connection connection, final JdbcDatabaseContainer container, final ConnectionUrl connectionUrl) {
        final boolean isDaemon = connectionUrl.isInDaemonMode();
        Set<Connection> connections = containerConnections.computeIfAbsent(container.getContainerId(), k -> new HashSet<>());
        connections.add(connection);
        final Set<Connection> finalConnections = connections;
        return new SharedConnectionWrapper(connection, () -> {
            finalConnections.remove(connection);
            if (!isDaemon && finalConnections.isEmpty()) {
                container.stop();
                jdbcUrlContainerCache.remove(connectionUrl.getUrl());
            }
        });
    }
}
