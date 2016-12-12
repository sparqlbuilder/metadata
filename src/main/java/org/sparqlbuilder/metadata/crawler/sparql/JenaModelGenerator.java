package org.sparqlbuilder.metadata.crawler.sparql;

import java.util.ArrayList;
import java.util.HashSet;

import org.apache.jena.riot.RDFDataMgr;

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.DatasetFactory;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.NodeIterator;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;

public class JenaModelGenerator {

	String endpointURI = null;
	String[] graphURIs = null;
	Model model = null;
	Dataset dataset = null;
	
	
	public static void main(String[] args) throws Exception{
		JenaModelGenerator jmg = new JenaModelGenerator("c:\\temp\\allie.ttl");
		
	}
	
	public JenaModelGenerator(String file) throws Exception{
		readCrawlerRdfFile(file);
	}
	
	public void readCrawlerRdfFile(String file) throws Exception{
		model = RDFDataMgr.loadModel(file);
		Property endpointPro = model.getProperty(URICollection.PROPERTY_SD_ENDPOINT);
		NodeIterator nit = model.listObjectsOfProperty(endpointPro);
		

		int cnt = 0;
		Resource endPointRes = null;
		while( nit.hasNext() ){
			if( cnt == 0 ){
				RDFNode endPointNode = nit.next();
				endPointRes = endPointNode.asResource();
				endpointURI = endPointRes.getURI();
			}
			cnt++;
		}
		
		// graphs
		Resource defaultDatasetBlankNode = null;
		Property defaultDatasetPro = model
				.getProperty(URICollection.PROPERTY_SD_DEFAULT_DATA_SET);
		nit= model.listObjectsOfProperty(defaultDatasetPro);
		if (nit.hasNext()) {
			defaultDatasetBlankNode = nit.next().asResource();
		}
		HashSet<String> resultSet = new HashSet<String>();
		Property namedGraphPro = model.getProperty(URICollection.PROPERTY_SD_NAMED_GRAPH);
		NodeIterator nodeit = model.listObjectsOfProperty(defaultDatasetBlankNode, namedGraphPro);
		while( nodeit.hasNext()){
			Resource blankNodeRes = nodeit.next().asResource();
			Property namePro = model.getProperty(URICollection.PROPERTY_SD_NAME);
			NodeIterator nodeit2 = model.listObjectsOfProperty(blankNodeRes, namePro);
			while( nodeit2.hasNext()){
				Resource ngRes = nodeit2.next().asResource();
				String namedGraphURI = ngRes.getURI();
				resultSet.add(namedGraphURI);
				System.out.println(namedGraphURI);
			}
		}
		graphURIs = resultSet.toArray(new String[0]);
	}

	public Model getModel(){
		return model;
	}

	public String getEndpointURI(){
		return endpointURI;
	}

	public String[] getGraphURIs(){
		return graphURIs;
	}

}
