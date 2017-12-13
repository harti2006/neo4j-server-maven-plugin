/*
 * Copyright 2015 André Hartmann (github.com/harti2006)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.harti2006.neo4j;

import static java.lang.String.format;
import static java.net.URI.create;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.newBufferedWriter;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.io.FileUtils.copyURLToFile;
import static org.apache.maven.plugins.annotations.LifecyclePhase.PRE_INTEGRATION_TEST;
import static org.rauschig.jarchivelib.ArchiverFactory.createArchiver;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.plexus.util.PropertyUtils;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.exceptions.ServiceUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Mojo(name = "start", defaultPhase = PRE_INTEGRATION_TEST)
public class StartNeo4jServerMojo extends Neo4jServerMojoSupport {

    @SuppressWarnings("unchecked")
    public void execute() throws MojoExecutionException {
        installNeo4jServer();
        configureNeo4jServer();
        startNeo4jServer();
    }

    private void installNeo4jServer() throws MojoExecutionException {
        final Path serverLocation = getServerLocation();
        if (!exists(serverLocation)) {
            final Path downloadDestination = Paths.get(System.getProperty("java.io.tmpdir"),
                                                       "neo4j-server-maven-plugin", "downloads", "server",
                                                       version, "neo4j-server" + urlSuffix);

            if (!exists(downloadDestination)) {
                try {
                    final URL source = new URL(BASE_URL + version + urlSuffix);
                    createDirectories(downloadDestination.getParent());

                    getLog().info(format("Downloading Neo4j Server from %s", source));
                    getLog().debug(format("...and saving it to '%s'", downloadDestination));

                    copyURLToFile(source, downloadDestination.toFile());
                } catch (IOException e) {
                    throw new MojoExecutionException("Error downloading server artifact", e);
                }
            }

            try {
                getLog().info(format("Extracting %s", downloadDestination));
                createArchiver("tar", "gz").extract(downloadDestination.toFile(),
                                                    serverLocation.getParent().toFile());
            } catch (IOException e) {
                throw new MojoExecutionException("Error extracting server archive", e);
            }
        }
    }

    private void configureNeo4jServer() throws MojoExecutionException {
        final Path serverLocation = getServerLocation();
        final Path serverPropertiesPath = serverLocation.resolve(
                Paths.get("conf", "neo4j.conf"));

        Properties serverProperties = PropertyUtils.loadProperties(serverPropertiesPath.toFile());

        serverProperties.setProperty("dbms.connector.http.listen_address", "localhost:" + port);
        serverProperties.setProperty("dbms.connector.bolt.listen_address", "localhost:" + boltPort);
        serverProperties.setProperty("dbms.connector.https.enabled", "false");

        try {
            serverProperties.store(newBufferedWriter(serverPropertiesPath, TRUNCATE_EXISTING, WRITE),
                                   "Generated by Neo4j Server Maven Plugin");
        } catch (IOException e) {
            throw new MojoExecutionException("Could not configure Neo4j server", e);
        }
    }

    private void startNeo4jServer() throws MojoExecutionException {
        final Log log = getLog();
        try {
            final Path serverLocation = getServerLocation();
            
            // Delete existing DB if required
            if ( deleteDb )
            {
            		Path dbdir = serverLocation.resolve ( "data/databases/graph.db" );
            		log.info ( "Deleting Database directory: '" + dbdir.toAbsolutePath ().toString () + "'" );
	            FileUtils.deleteQuietly ( 
	            		serverLocation.resolve ( "data" ).toFile ()
	            	);
            }
            
            final String[] neo4jCommand = new String[]{
                    serverLocation.resolve(Paths.get("bin", "neo4j")).toString(), "start"};
            final File workingDir = serverLocation.toFile();

            final Process neo4jStartProcess = Runtime.getRuntime().exec(neo4jCommand, null, workingDir);
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(neo4jStartProcess.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    log.info("NEO4J SERVER > " + line);
                }
            }

            if (neo4jStartProcess.waitFor(5, SECONDS) && neo4jStartProcess.exitValue() == 0) {
                log.info("Started Neo4j server");
            } else {
                throw new MojoExecutionException("Neo4j server did not start up properly");
            }
            
            // Now we need to wait it replies
            checkServerReady ();
            
            // Went up! If it's a new DB, let's change the password.
            setNewPassword ();
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException( "Could not start neo4j server", e);
        }
    }
    
    private void checkServerReady () throws MojoExecutionException, InterruptedException
    {
    		String pwd = deleteDb ? "neo4j" : password;
    		
      for ( int attempts = 3; attempts > 0; attempts-- )
      {
      		try 
      		{
      			getLog().info ( "Trying to connect DB with pass: " + pwd ); 
	        Driver driver = GraphDatabase.driver( "bolt://127.0.0.1:" + boltPort, AuthTokens.basic ( "neo4j", pwd ) );
	      		driver.close ();
	      		return;
      		}
      		catch ( ServiceUnavailableException ex ) {
      		}
      		Thread.sleep ( 3000 );
      }
      throw new MojoExecutionException ( 
      		"Server doesn't result started after waiting for its boot" 
      	);
    }
    
    private void setNewPassword ()
    {
      if ( !deleteDb ) return;
      
      Driver driver = GraphDatabase.driver( "bolt://127.0.0.1:" + boltPort, AuthTokens.basic ( "neo4j", "neo4j" ) );
      Session session = driver.session ();
      session.run ( "CALL dbms.changePassword( '" + password + "' )" );
      session.close ();
      driver.close ();
    }
}
