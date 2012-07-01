xuml-tools
==========

Executable UML tools (xml schema, java model compiler, javascript model viewer) based on miUML metamodels.

Getting started
---------------
Until the project has matured enough to release artifacts to Maven Central repository this is how to locally install the artfacts from source:

    git clone https://github.com/davidmoten/xuml-tools.git
    cd xuml-tools
    mvn clean install

To generate your own JPA classes from xml compliant with the miUML schema add the following plugin to your pom.xml:
```
	<build>
		<plugins>
			<plugin>
				<groupId>${project.groupId}</groupId>
				<artifactId>xuml-tools-maven-plugin</artifactId>
				<version>0.0.1-SNAPSHOT</version>
				<executions>
					<execution>
						<id>generate-jpa</id>
						<goals>
							<goal>generate-jpa</goal>
						</goals>
						<configuration>
							<domainsXml>/samples.xml</domainsXml>
							<domain>Nested composite id example</domain>
							<schema>abc</schema>
							<packageName>abc</packageName>
						</configuration>
					</execution>
				</executions>
			</plugin>
		</plugins>
	</build>
```