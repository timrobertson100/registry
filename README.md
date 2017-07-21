# GBIF Registry

The GBIF Registry is a core component of the architecture responsible for providing the authoritative source of information on GBIF participants (Nodes), institutions (e.g. data publishers), datasets, networks their interrelationships and the means to identify and access them.

As a distributed network, the registry serves a central coordination mechanism, used for example to allow publishers to declare their existence and for data integrating components to discover how to access published datasets and interoperate with the publisher.

See also: http://www.gbif.org/infrastructure/registry

## To build the project

1. Create an empty PostgreSQL database "registry_it".  You may need to run

    `CREATE EXTENSION unaccent;`
    `CREATE EXTENSION hstore;`

  (installed extensions can be listed with `\dx` from `psql`.)

2. This database will automatically be populated by Liquibase when the integration tests are run.


3. Set up a solr collection hosted either in a simple [http solr server](http://lucene.apache.org/solr/quickstart.html) or in a Solr cloud. Follow the [registry-index-builder](registry-index-builder/README.md) to create and populate such a collection. See the [maven POM](pom.xml) for the minimum solr version required by the current registry code.

4. Create a Maven profile similar to:

````xml
  <profile>
    <id>registry-local-it</id>
    <properties>
      <registry-it.db.host>localhost</registry-it.db.host>
      <registry-it.db.name>registry_it</registry-it.db.name>
      <registry-it.db.username>registry</registry-it.db.username>
      <registry-it.db.password/>
      <appkeys.testfile>/home/mblissett/Workspace/appkeys-it.properties</appkeys.testfile>
   </properties>
  </profile>
````

6. Run `mvn clean install`.
