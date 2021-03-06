# Neo4j Server Maven Plugin

It may get hard to update the existing [neo4j-maven-plugin](https://github.com/rherschke/neo4j-maven-plugin)
and make it use the latest versions of Neo4j, because it depends on deprecated classes, which are very
likely to be moved to some internal packages in the future.

So I decided to write another small Maven plugin, that simply downloads the complete Neo4j server artifact,
and runs it, as a user would do it, using `./neo4j start`

Furthermore it provides an idiomatic way to configure the server using the plugin `<configuration>` section.


## Building the project

    mvn clean install

## Usage

### start

    mvn neo4j-server:start

### stop

    mvn neo4j-server:stop

### Integration Testing

The start/stop goals bind by default to lifecycle phases pre- and post-integration-test:

    <plugin>
        <groupId>com.github.harti2006</groupId>
        <artifactId>neo4j-server-maven-plugin</artifactId>
        <version>1.0-SNAPSHOT</version>
        <configuration>
            <port>${neo4j-server.port}</port>
            <version>${neo4j-server.version}</version>
        </configuration>
        <executions>
            <execution>
                <id>start-neo4j-server</id>
                <goals>
                    <goal>start</goal>
                </goals>
            </execution>
            <execution>
                <id>stop-neo4j-server</id>
                <goals>
                    <goal>stop</goal>
                </goals>
            </execution>
        </executions>
    </plugin>

For an example, run

    cd integration-tests
    mvn clean verify

### Parameters

Have a look at the [`Neo4jServerMojoSupport`](src/main/java/com/github/harti2006/neo4j/Neo4jServerMojoSupport.java) class for details.
 
### Code Formatting

File formatting is verified by [EditorConfig](http://editorconfig.org/) 
during `mvn verify` step. Most errors can be fixed with
`mvn editorconfig:format` task.
 
### Releases

The release process is copied frome this [blog post](https://dracoblue.net/dev/uploading-snapshots-and-releases-to-maven-central-with-travis/):

* Snapshot releases to Maven Central are performed automatically by Travis CI on every push to `master`.
* Final releases to Maven Central are performed automatically by Travis CI after creating and pushing a git tag.
