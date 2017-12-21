# metadata

A Java library that manages SPARQL Builder Metadata (SBM; http://www.sparqlbuilder.org/doc/) that describes a profile of SPARQL Endpoint for SPARQL Builder (http://www.sparqlbuilder.org).
This library includes a crawler that obtains the SBM by querying a set of SPARQL queries.

#Usage of the SPARQL Builder metadata crawler

% java org.sparqlbuilder.metadata.crawler.sparql.RDFsCrawlerImpl \[options\]

\[options\] <br>
  1. to print a list of graphURIs <br>
     -g endpointURL <br>
  2. to crawl whole data in the endpoint <br>
     -ac endpointURL crawlName outputFileName <br>
  3. to crawl the specified graph in the endpoint <br>
     -gc endpointURL crawlName graphURI outputFileName <br>  
	4. to crawl the default named graph in the endpoint <br>
     -d endpointURL crawlName outputFileName <br>  
