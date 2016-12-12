package org.sparqlbuilder.metadata.crawler.sparql;

import com.hp.hpl.jena.rdf.model.Resource;

public interface RDFsCrawler {

		public String[] getDeclaredRDFsClasses() throws Exception;
		
		public String[] getInferedRDFsClassesFromInstances() throws Exception;

		public String[] getRDFProperties() throws Exception;

//		public String[] getDomainRangeDeclaredRDFProperties() throws Exception;
		
}
