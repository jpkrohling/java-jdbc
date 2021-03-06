package io.opentracing.contrib.jdbc;


import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

public class TracingDriver implements Driver {

  private static final Driver INSTANCE = new TracingDriver();

  static {
    try {
      DriverManager.registerDriver(INSTANCE);
    } catch (SQLException e) {
      throw new IllegalStateException("Could not register TracingDriver with DriverManager", e);
    }
  }

  @Override
  public Connection connect(String url, Properties info) throws SQLException {
    // if there is no url, we have problems
    if (url == null) {
      throw new SQLException("url is required");
    }

    if (!acceptsURL(url)) {
      return null;
    }

    String realUrl = extractRealUrl(url);
    String dbType = extractDbType(realUrl);
    String dbUser = info.getProperty("user");

    // find the real driver for the URL
    Driver wrappedDriver = findDriver(realUrl);
    Connection connection = wrappedDriver.connect(realUrl, info);

    return new TracingConnection(connection, dbType, dbUser);
  }

  @Override
  public boolean acceptsURL(String url) throws SQLException {
    return url != null && url.startsWith("jdbc:tracing:");
  }

  @Override
  public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
    return findDriver(url).getPropertyInfo(url, info);
  }

  @Override
  public int getMajorVersion() {
    // There is no way to get it from wrapped driver
    return 1;
  }

  @Override
  public int getMinorVersion() {
    // There is no way to get it from wrapped driver
    return 0;
  }

  @Override
  public boolean jdbcCompliant() {
    return true;
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    // There is no way to get it from wrapped driver
    return null;
  }

  private Driver findDriver(String realUrl) throws SQLException {

    Driver wrappedDriver = null;
    for (Driver driver : registeredDrivers()) {
      try {
        if (driver.acceptsURL(realUrl)) {
          wrappedDriver = driver;
          break;
        }
      } catch (SQLException e) {
        // intentionally ignore exception
      }
    }
    if (wrappedDriver == null) {
      throw new SQLException("Unable to find a driver that accepts " + realUrl);
    }
    return wrappedDriver;
  }

  private List<Driver> registeredDrivers() {
    List<Driver> result = new ArrayList<>();
    for (Enumeration<Driver> driverEnumeration = DriverManager.getDrivers();
        driverEnumeration.hasMoreElements(); ) {
      result.add(driverEnumeration.nextElement());
    }
    return result;
  }

  private String extractRealUrl(String url) {
    return url.startsWith("jdbc:tracing:") ? url.replace("tracing:", "") : url;
  }

  private String extractDbType(String realUrl) {
    return realUrl.split(":")[1];
  }
}
