package org.sparqlbuilder.metadata.crawler.sparql;

import java.util.HashSet;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;

public class Schema {

	public Model model = null;
	public Resource datasetResource = null;
	public int propertyCategory = 0;
	public int classCategory = 0;
	public int[] countProperties;
	public int endpointCategory = 0;
	public int numTriples = 0;
	public HashSet<String> inferedClasses; // internally used
	public HashSet<String> datatypes;

	
	
	public Schema(Model model, Resource datasetResource, int propertyCategory, int[] countProperties,
			int classCategory, int endpointCategory, int numTriples,
			HashSet<String> inferedClasses, HashSet<String> datatypes) {
		this.model = model;
		this.datasetResource = datasetResource;
		this.propertyCategory = propertyCategory;
		this.countProperties = countProperties;
		this.classCategory = classCategory;
		this.endpointCategory = endpointCategory;
		this.numTriples = numTriples;
		this.inferedClasses = inferedClasses;
		this.datatypes = datatypes;
	}
}
