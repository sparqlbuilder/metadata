package org.sparqlbuilder.metadata.crawler.sparql;

public class URICollection {

	static public final String PREFIX_RDFS = "http://www.w3.org/2000/01/rdf-schema#";
	static public final String PREFIX_RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
	static public final String PREFIX_OWL = "http://www.w3.org/2002/07/owl#";

	static public final String PROPERTY_RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
	static public final String CLASS_RDF_PROPERTY = "http://www.w3.org/1999/02/22-rdf-syntax-ns#Property";

	static public final String PROPERTY_RDFS_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";
	static public final String RESOURCE_RDFS_CLASS = "http://www.w3.org/2000/01/rdf-schema#Class";
	static public final String PROPERTY_RDFS_SUBCLASSOF = "http://www.w3.org/2000/01/rdf-schema#subclassOf";
	static public final String PROPERTY_RDFS_DOMAIN = "http://www.w3.org/2000/01/rdf-schema#domain";
	static public final String PROPERTY_RDFS_RANGE = "http://www.w3.org/2000/01/rdf-schema#range";
	static public final String PROPERTY_RDFS_LITERAL = "http://www.w3.org/2000/01/rdf-schema#Literal";
	static public final String RESOURCE_RDFS_DATATYPE = "http://www.w3.org/2000/01/rdf-schema#Datatype";

	static public final String RESOURCE_RDF_LANGSTRING = "http://www.w3.org/1999/02/22-rdf-syntax-ns#langString";
	static public final String RESOURCE_RDF_PROPERTY = "http://www.w3.org/1999/02/22-rdf-syntax-ns#Property";

	static public final String PROPERTY_SD_ENDPOINT = "http://www.w3.org/ns/sparql-service-description#endpoint";
	static public final String RESOURCE_SD_SERVICE = "http://www.w3.org/ns/sparql-service-description#Service";
	static public final String RESOURCE_SD_NAMED_GRAPH = "http://www.w3.org/ns/sparql-service-description#NamedGraph";
	static public final String PROPERTY_SD_NAMED_GRAPH = "http://www.w3.org/ns/sparql-service-description#namedGraph";
	static public final String PROPERTY_SD_DEFAULT_DATA_SET = "http://www.w3.org/ns/sparql-service-description#defaultDataset";
	static public final String RESOURCE_SD_DATA_SET = "http://www.w3.org/ns/sparql-service-description#Dataset";
	static public final String PROPERTY_SD_NAME = "http://www.w3.org/ns/sparql-service-description#name";
	static public final String PROPERTY_SD_GRAPH = "http://www.w3.org/ns/sparql-service-description#graph";
	static public final String RESOURCE_SD_GRAPH = "http://www.w3.org/ns/sparql-service-description#Graph";



	static public final String PROPERTY_VOID_TRIPLES = "http://rdfs.org/ns/void#triples";
	static public final String PROPERTY_VOID_ENTITIES = "http://rdfs.org/ns/void#entities";
	static public final String PROPERTY_VOID_CLASSES = "http://rdfs.org/ns/void#classes";
	static public final String PROPERTY_VOID_PROPERTIES = "http://rdfs.org/ns/void#properties";
	static public final String PROPERTY_VOID_DISTINCT_SUBJECTS = "http://rdfs.org/ns/void#distinctSubjects";
	static public final String PROPERTY_VOID_DISTINCT_OBJECTS = "http://rdfs.org/ns/void#distinctObjects";
	static public final String PROPERTY_VOID_PROPERTY_PARTITION = "http://rdfs.org/ns/void#propertyPartition";
	static public final String PROPERTY_VOID_CLASS_PARTITION = "http://rdfs.org/ns/void#classPartition";
	static public final String PROPERTY_VOID_PROPERTY = "http://rdfs.org/ns/void#property";
	static public final String PROPERTY_VOID_CLASS = "http://rdfs.org/ns/void#class";
	static public final String RESOURCE_VOID_DATASET = "http://rdfs.org/ns/void#Dataset";
//	static public final String PROPERTY_VOID_SUBSET = "http://rdfs.org/ns/void#subset";


	static public final String PROPERTY_SB_ENDPOINT_CATEGORY = "http://sparqlbuilder.org/2015/09/rdf-metadata-schema#endpointCategory";
	static public final String PROPERTY_SB_CLASS_CATEGORY = "http://sparqlbuilder.org/2015/09/rdf-metadata-schema#classCategory";
	static public final String PROPERTY_SB_PROPERTY_CATEGORY = "http://sparqlbuilder.org/2015/09/rdf-metadata-schema#propertyCategory";
	static public final String PROPERTY_SB_CRAWL_START_TIME = "http://sparqlbuilder.org/2015/09/rdf-metadata-schema#crawlStartTime";
	static public final String PROPERTY_SB_CRAWL_END_TIME = "http://sparqlbuilder.org/2015/09/rdf-metadata-schema#crawlEndTime";
	static public final String RESOURCE_SB_CLASS_RELATION = "http://sparqlbuilder.org/2015/09/rdf-metadata-schema#ClassRelation";
//	static public final String PROPERTY_SB_START_CLASS_LIMITED_Q = "http://sparqlbuilder.org/2015/09/rdf-metadata-schema#startClassLimitedQ";
//	static public final String PROPERTY_SB_END_CLASS_LIMITED_Q = "http://sparqlbuilder.org/2015/09/rdf-metadata-schema#endClassLimitedQ";

	static public final String PROPERTY_SB_SEARCHABLE_TRIPLES = "http://sparqlbuilder.org/2015/09/rdf-metadata-schema#searchableTriples";
	static public final String PROPERTY_SB_CLASS_RELATION = "http://sparqlbuilder.org/2015/09/rdf-metadata-schema#classRelation";
	static public final String PROPERTY_SB_CRAWL_LOG = "http://sparqlbuilder.org/2015/09/rdf-metadata-schema#crawlLog";
	static public final String RESOURCE_SB_CRAWL_LOG = "http://sparqlbuilder.org/2015/09/rdf-metadata-schema#CrawlLog";
	static public final String PROPERTY_SB_ENDPOINT_ACCESSES = "http://sparqlbuilder.org/2015/09/rdf-metadata-schema#endpointAccesses";
	static public final String PROPERTY_SB_DATATYPES = "http://sparqlbuilder.org/2015/09/rdf-metadata-schema#datatypes";
	static public final String PROPERTY_SB_SUBJECT_CLASS = "http://sparqlbuilder.org/2015/09/rdf-metadata-schema#subjectClass";
	static public final String PROPERTY_SB_OBJECT_CLASS = "http://sparqlbuilder.org/2015/09/rdf-metadata-schema#objectClass";
	static public final String PROPERTY_SB_OBJECT_DATATYPE = "http://sparqlbuilder.org/2015/09/rdf-metadata-schema#objectDatatype";
	static public final String PROPERTY_SB_SUBJECT_CLASSES = "http://sparqlbuilder.org/2015/09/rdf-metadata-schema#subjectClasses";
	static public final String PROPERTY_SB_OBJECT_CLASSES = "http://sparqlbuilder.org/2015/09/rdf-metadata-schema#objectClasses";
	static public final String PROPERTY_SB_OBJECT_DATATYPES = "http://sparqlbuilder.org/2015/09/rdf-metadata-schema#objectDatatypes";
	static public final String PROPERTY_SB_PROPERTY_CATEGORY_SUBSET = "http://sparqlbuilder.org/2015/09/rdf-metadata-schema#propertyCategorySubset";


	static public final String[] FILTER_GRAPH = { "http://www.openlinksw.com/", "http://localhost", PREFIX_RDF, PREFIX_RDFS, PREFIX_OWL };
	static public final String[] FILTER_CLASS = { PREFIX_RDF, PREFIX_RDFS, PREFIX_OWL, "http://www.w3.org/2001/XMLSchema", "http://www.openlinksw", "http://www.w3.org/ns/sparql-service-description"};
	static public final String[] UNFILTER_CLASS = { "http://www.w3.org/2001/XMLSchema" };
	static public final String[] FILTER_PROPERTY = { PREFIX_RDF, PREFIX_RDFS, PREFIX_OWL, "http://www.w3.org/ns/sparql-service-description", "http://www.openlinksw", "http://www.w3.org/1999/02/22-rdf-syntax-ns", "http://www.w3.org/2000/01/rdf-schema", "http://www.w3.org/2002/07/owl", "http://www.w3.org/ns/sparql-service-description" };


}
