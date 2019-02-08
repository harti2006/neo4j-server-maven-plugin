package com.github.harti2006.neo4j;

import static org.apache.maven.plugins.annotations.LifecyclePhase.PRE_INTEGRATION_TEST;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.springframework.core.io.ClassPathResource;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

import java.util.Map;
import java.util.Properties;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>8 Feb 2019</dd></dl>
 *
 */
@Mojo(name = "download", defaultPhase = PRE_INTEGRATION_TEST)
public class DownloadNeo4jServerMojo extends Neo4jServerMojoSupport
{
	/**
	 * @parameter expression="${project}"
	 * @required
	 * @readonly
	 */
	private MavenProject mavenProject;

	/**
	 * @parameter expression="${session}"
	 * @required
	 * @readonly
	 */
	private MavenSession mavenSession;

	/**
	 * @component
	 * @required
	 */
	private BuildPluginManager pluginManager;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException
	{
		this.runMyGoal ( "start" );
		this.runMyGoal ( "stop" );
	}

	private void runMyGoal ( String goal ) throws MojoExecutionException, MojoFailureException
	{
		Properties prjProps = mavenProject.getProperties ();
		Element[] forwardedPropElems = (Element[]) prjProps.entrySet ()
			.stream ()
			.map ( entry -> element ( (String) entry.getKey (), (String) entry.getValue () ) )
			.toArray ();

		executeMojo(
	    plugin(
        groupId( BUILD_PROPS.getProperty ( "plugin.groupId" ) ),
        artifactId( BUILD_PROPS.getProperty ( "plugin.artifactId" ) ),
        version( BUILD_PROPS.getProperty ( "plugin.version" ) )
	    ),
	    goal( goal ),
	    configuration( forwardedPropElems ),
	    executionEnvironment( mavenProject, mavenSession, pluginManager )
		);
	}
}
