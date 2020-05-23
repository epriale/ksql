---
layout: page
title: Java Client for ksqlDB
tagline: Java client for ksqlDB
description: Send requests to ksqlDB from your Java app
---

Use the Java client to:

- [Receive query results one row at a time (streamQuery())](#stream-query)
- [Receive query results in a single batch (executeQuery())](#execute-query)
- [Terminate a push query (terminatePushQuery())](#terminate-push-query)
- [Insert a new row into a stream (insertInto())](#insert-into)

Getting Started
---------------

Start by creating a `pom.xml` for your Java application:

```xml
<?xml version="1.0" encoding="UTF-8"?>

<project xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>my.ksqldb.app</groupId>
    <artifactId>my-ksqldb-app</artifactId>
    <version>0.0.1</version>

    <properties>
        <!-- Keep versions as properties to allow easy modification -->
        <java.version>8</java.version>
        <ksqldb.version>{{ site.release }}</ksqldb.version>
        <!-- Maven properties for compilation -->
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>io.confluent.ksql</groupId>
            <artifactId>ksqldb-api-client</artifactId>
            <version>${ksqldb.version}</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
              <groupId>org.apache.maven.plugins</groupId>
              <artifactId>maven-compiler-plugin</artifactId>
              <version>3.8.1</version>
              <configuration>
                <source>${java.version}</source>
                <target>${java.version}</target>
                <compilerArgs>
                  <arg>-Xlint:all</arg>
                </compilerArgs>
              </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Create your example app at `src/main/java/my/ksqldb/app/ExampleApp.java`:

```java
package my.ksqldb.app;

import io.confluent.ksql.api.client.Client;
import io.confluent.ksql.api.client.ClientOptions;

public class ExampleApp {

  public static final String KSQLDB_SERVER_HOST = "localhost";
  public static final int KSQLDB_SERVER_HOST_PORT = 8088;
  
  public static void main(final String[] args) {
    final ClientOptions options = ClientOptions.create()
        .setHost(KSQLDB_SERVER_HOST)
        .setPort(KSQLDB_SERVER_HOST_PORT);
    final Client client = Client.create(options);
    
    // Send requests with the client by following the other examples
    
    client.close();
  }
}
```

For additional client options, see [the API reference](TODO).

Receive query results one row at a time (streamQuery())<a name="stream-query"></a>
----------------------------------------------------------------------------------

The `streamQuery()` method allows client apps to receive query results one row at a time,
either asynchronously via a Reactive Streams subscriber or synchronously in a polling fashion.

```java
public interface Client {

  /**
   * Executes a query (push or pull) and returns the results one row at a time.
   *
   * <p>If a non-200 response is received from the server, the {@code CompletableFuture} will be
   * failed.
   *
   * <p>By default, push queries issued via this method return results starting from the beginning
   * of the stream or table. To override this behavior, use the method
   * {@link #streamQuery(String, Map)} to pass in the query property {@code auto.offset.reset}
   * with value set to {@code latest}.
   *
   * @param sql statement of query to execute
   * @return a future that completes once the server response is received, and contains the query
   *         result if successful
   */
  CompletableFuture<StreamedQueryResult> streamQuery(String sql);
  
  ...
  
}
```

This method may be used to issue both push and pull queries, though the usage pattern is most suited for push queries.
For pull queries, consider [the `executeQuery()` method](./execute-query.md) instead. 

Query properties can be passed as an optional second argument. See the [client API reference](TODO) for more.

By default, push queries return results starting from the beginning of the stream or table.
To instead start from the end and only receive newly arriving rows, set the property `auto.offset.reset` to `latest`.

### Asynchronous Usage ###

To consume records in an asynchronous fashion, first create a Reactive Streams subscriber to receive query result rows:

```java
import io.confluent.ksql.api.client.Row;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class RowSubscriber implements Subscriber<Row> {

  private Subscription subscription;

  public RowSubscriber() {
  }

  @Override
  public synchronized void onSubscribe(final Subscription subscription) {
    System.out.println("Subscriber is subscribed.");
    this.subscription = subscription;
  }

  @Override
  public synchronized void onNext(final Row row) {
    System.out.println("Received a row!");
    System.out.println("Row: " + row.values());
  }

  @Override
  public synchronized void onError(final Throwable t) {
    System.out.println("Received an error: " + t);
  }

  @Override
  public synchronized void onComplete() {
    System.out.println("Query has ended.");
  }

  public Subscription getSubscription() {
    return subscription;
  }
}
```

Then, use the client to send the query result to the server and stream results to the subscriber:

```java
client.streamQuery("SELECT * FROM MY_STREAM EMIT CHANGES;")
    .thenAccept(streamedQueryResult -> {
      System.out.println("Query has started. Query ID: " + streamedQueryResult.queryID());
      
      final RowSubscriber subscriber = new RowSubscriber();
      streamedQueryResult.subscribe(subscriber);
      subscriber.getSubscription().request(10);
    }).exceptionally(e -> {
      System.out.println("Request failed: " + e);
      return null;
    });
```

### Synchronous Usage ###

To consume records one-at-a-time in a synchronous fashion, use the `poll()` method on the query result object.
If `poll()` is called with no arguments, `poll()` will block until a new row becomes available or the query is terminated.
You can also pass a `Duration` argument to `poll()` which will cause `poll()` to return null if no new rows are received by the time the duration has elapsed.
See [the API reference](TODO) for more.

```java
final StreamedQueryResult streamedQueryResult;
try {
  streamedQueryResult = client.streamQuery("SELECT * FROM MY_STREAM EMIT CHANGES;").get();
} catch (Exception e) {
  System.out.println("Request failed: " + e);
  return;
}

for (int i = 0; i < 10; i++) {
  // Block until a new row is available
  final Row row = streamedQueryResult.poll();
  if (row != null) {
    System.out.println("Received a row!");
    System.out.println("Row: " + row.values());
  } else {
    System.out.println("Query has ended.");
  }
}
```

Receive query results in a single batch (executeQuery())<a name="execute-query"></a>
------------------------------------------------------------------------------------

The `executeQuery()` method allows client apps to receive query results as a single batch,
returned once the query has completed.

```java
public interface Client {

  /**
   * Executes a query (push or pull) and returns all result rows in a single batch, once the query
   * has completed.
   *
   * <p>By default, push queries issued via this method return results starting from the beginning
   * of the stream or table. To override this behavior, use the method
   * {@link #executeQuery(String, Map)} to pass in the query property {@code auto.offset.reset}
   * with value set to {@code latest}.
   *
   * @param sql statement of query to execute
   * @return query result
   */
  BatchedQueryResult executeQuery(String sql);
  
  ...
  
}
```

This method is suitable for both pull queries as well as terminating push queries (e.g., those with a `LIMIT` clause).
For non-temrinating push queries, use [the `streamQuery()` method](./stream-query.md) instead. 

Query properties can be passed as an optional second argument. See the [client API reference](TODO) for more.

By default, push queries return results starting from the beginning of the stream or table.
To instead start from the end and only receive newly arriving rows, set the property `auto.offset.reset` to `latest`.

### Example Usage ###

```java
final String pullQuery = "SELECT * FROM MY_MATERIALIZED_TABLE WHERE KEY_FIELD='some_key';";
final BatchedQueryResult batchedQueryResult = client.executeQuery(pullQuery);

final List<Row> resultRows;
try {
  resultRows = batchedQueryResult.get();
} catch (Exception e) {
  System.out.println("Request failed: " + e);
  return;
}

System.out.println("Received results. Num rows: " + resultRows.size());
for (final Row row : resultRows) {
  System.out.println("Row: " + row.values());
}
```

Terminate a push query (terminatePushQuery())<a name="terminate-push-query"></a>
--------------------------------------------------------------------------------

The `terminatePushQuery()` method allows client apps to terminate push queries.

```java
public interface Client {

  /**
   * Terminates a push query with the specified query ID.
   *
   * <p>If a non-200 response is received from the server, the {@code CompletableFuture} will be
   * failed.
   *
   * @param queryId ID of the query to terminate
   * @return a future that completes once the server response is received
   */
  CompletableFuture<Void> terminatePushQuery(String queryId);
  
  ...
  
}
```

The query ID is obtained from the query result response object when push queries are issued via the client,
via either [`streamQuery()`](./stream-query.md) or [`executeQuery()`](./execute-query.md).

### Example Usage ###

Here's an example of terminating a push query issued via `streamQuery()`:

```java
final StreamedQueryResult streamedQueryResult;
try {
  streamedQueryResult = client.streamQuery("SELECT * FROM MY_STREAM EMIT CHANGES;").get();
} catch (Exception e) {
  System.out.println("Query request failed: " + e);
  return;
}

final String queryId = streamedQueryResult.queryID();
System.out.println("Terminating query with ID: " + queryId);
try {
  client.terminatePushQuery(queryId).get();
  System.out.println("Sucessfully terminated query.");
} catch (Exception e) {
  System.out.println("Terminate request failed: " + e);
}
```

And here's an analogous example for terminating a push query issued via `executeQuery()`:

```java
final String pullQuery = "SELECT * FROM MY_STREAM EMIT CHANGES LIMIT 10;";
final BatchedQueryResult batchedQueryResult = client.executeQuery(pullQuery);

final String queryId;
try {
  queryId = batchedQueryResult.queryID().get();
} catch (Exception e) {
  System.out.println("Query request failed: " + e);
  return;
}

try {
  client.terminatePushQuery(queryId).get();
} catch (Exception e) {
  System.out.println("Terminate request failed: " + e);
}
```

Insert a new row into a stream (insertInto())<a name="insert-into"></a>
-----------------------------------------------------------------------

Client apps can insert new rows of data into existing ksqlDB streams via the `insertInto()` method.

```java
public interface Client {

  /**
   * Inserts a row into a ksqlDB stream.
   *
   * <p>The {@code CompletableFuture} will be failed if a non-200 response is received from the
   * server, or if the server encounters an error while processing the insertion.
   *
   * @param streamName name of the target stream
   * @param row the row to insert. Keys are column names and values are column values.
   * @return a future that completes once the server response is received
   */
  CompletableFuture<Void> insertInto(String streamName, KsqlObject row);
  
  ...
  
}
```

Rows for insertion are represented as `KsqlObject` instances. A `KsqlObject` represents a map of strings
(in this case, column names) to values (column values).

### Example Usage ###

Here's an example of using the client to insert a new row into an existing stream `ORDERS`
with schema (ORDER_ID BIGINT, PRODUCT_ID VARCHAR, USER_ID VARCHAR).

```java
final Row row = new KsqlObject()
    .put("ROWKEY", "k1")
    .put("ORDER_ID", 12345678L)
    .put("PRODUCT_ID", "UAC-222-19234")
    .put("USER_ID", "User_321"));

try {
  client.insertInto("ORDERS", row).get();
  System.out.println("Successfully inserted a row.");
} catch (Exception e) {
  System.out.println("Insert request failed: " + e);
}
```
