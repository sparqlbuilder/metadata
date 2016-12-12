package org.sparqlbuilder.metadata.crawler.sparql;

public interface RDFsCrawler {

		public String[] getDeclaredRDFsClasses() throws Exception;

		public String[] getInferedRDFsClassesFromInstances() throws Exception;

		public String[] getRDFProperties() throws Exception;

//		public String[] getDomainRangeDeclaredRDFProperties() throws Exception;

}
