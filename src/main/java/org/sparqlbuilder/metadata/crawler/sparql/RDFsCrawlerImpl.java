package org.sparqlbuilder.metadata.crawler.sparql;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import org.sparqlbuilder.metadata.crawler.datastructure.SchemaCategory;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.AnonId;
//import com.hp.hpl.jena.rdf.model.AnonId;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
//import com.hp.hpl.jena.rdf.model.RDFWriter;
import com.hp.hpl.jena.rdf.model.Resource;

// import com.hp.hpl.jena.sparql.engine.http.QueryExceptionHTTP;

public class RDFsCrawlerImpl implements RDFsCrawler {

	long INTERVAL = 500L;
	long INTERVAL_ERROR = 5000L;

	static String version = "20170630-1";

	String endpointURI = null;
	String crawlName = null;
	File outDir = null;
	String[] graphURIs = null;
	String[] graphURIFilter = URICollection.FILTER_GRAPH;

	static LogFileManager logFileManager = null;
	static LogFileManager logFileManagerForRecovery = null;
	static TodoFileManager todoFileManager = null;

	public static void main(String[] args) throws Exception {

		System.out.println("  Version: " + version);

		if (args.length == 2 && args[0].equals("-g")) {
			String endPURI = args[1];
			// graphlist
			RDFsCrawlerImpl impl = new RDFsCrawlerImpl(endPURI);
			String[] graphURIs = null;
			graphURIs = impl.getGraphURIs();
			if (graphURIs != null) {
				Arrays.sort(graphURIs);
				for (String graphURI : graphURIs) {
					System.out.println("Graph: " + graphURI);
				}
			}
		} else {
			if( args.length == 4 && args[0].equals("-d") ){
				String endPURI = args[1];
				String crawlName = args[2];
				String outDir = args[3];
				// graphlist
				RDFsCrawlerImpl impl = new RDFsCrawlerImpl(endPURI, crawlName, outDir);
				String[] graphURIs = new String[0];
				crawl(impl, graphURIs);
			}else{
				if (args.length == 4 && args[0].equals("-ac")) {
					String endPURI = args[1];
					String crawlName = args[2];
					String outDir = args[3];
					RDFsCrawlerImpl impl = new RDFsCrawlerImpl(endPURI, crawlName, outDir);
					String[] graphURIs = null;
					graphURIs = impl.getGraphURIs();
					if (graphURIs == null || graphURIs.length == 0) {
						graphURIs = new String[0];
					} else {
						Arrays.sort(graphURIs);
						if (!Arrays.asList(graphURIs).contains((String) null)) {
							String[] extGraphURIs = new String[graphURIs.length + 1];
							extGraphURIs[graphURIs.length] = null;
							for (int i = 0; i < graphURIs.length; i++) {
								extGraphURIs[i] = graphURIs[i];
							}
							graphURIs = extGraphURIs;
						}
					}
					crawl(impl, graphURIs);
				} else {
					if (args.length == 5 && args[0].equals("-gc")) {
						// boolean errorState = false;
						String endPURI = args[1];
						String crawlName = args[2];
						String targetGraphURI = args[3];
						String outDir = args[4];
						RDFsCrawlerImpl impl = new RDFsCrawlerImpl(endPURI, crawlName, outDir);

						String[] graphURIs = null;
						graphURIs = impl.getGraphURIs();
						if (graphURIs != null) {
							for (String graphURI : graphURIs) {
								if (graphURI.equals(targetGraphURI)) {
									graphURIs = new String[1];
									graphURIs[0] = targetGraphURI;
									crawl(impl, graphURIs);
								}
							}
						}
					} else {
						// usage
						printUsage();
					}
				}
			}
		}
	}

	private static void printUsage() throws Exception {
		System.out.println("Usage: java org.sparqlbuilder.metadata.crawler.sparql.RDFsCrawlerImpl [options]");
		System.out.println("   [options]");
		System.out.println("       1. to print a list of graphURIs");
		System.out.println("            -g endpointURL");
		System.out.println("       2. to crawl whole data in the endpoint");
		System.out.println("            -ac endpointURL crawlName outputFileName");
		System.out.println("       3. to crawl the specified graph in the endpoint");
		System.out.println("            -gc endpointURL crawlName graphURI outputFileName");
		System.out.println("       4. to crawl the default named graph in the endpoint");
		System.out.println("            -d endpointURL crawlName outputFileName");
		
	}

	public static void crawl(RDFsCrawlerImpl impl, String[] graphURIs) throws Exception {

		// global model
		Model wholeModel = null;
		wholeModel = ModelFactory.createDefaultModel();
		Property endpointPro = wholeModel.createProperty(URICollection.PROPERTY_SD_ENDPOINT);
		Resource serviceRes = wholeModel.createResource(URICollection.RESOURCE_SD_SERVICE);
		Resource endpointRes = wholeModel.createResource(impl.endpointURI);
		Property typePro = wholeModel.createProperty(URICollection.PROPERTY_RDF_TYPE);

		Resource thisRes = wholeModel.createResource("");
		thisRes.addProperty(typePro, serviceRes);
		thisRes.addProperty(endpointPro, endpointRes);

		// dataset
		Property defaultDatasetPro = wholeModel.createProperty(URICollection.PROPERTY_SD_DEFAULT_DATA_SET);
		Resource datasetBlankRes = wholeModel.createResource(AnonId.create());
		thisRes.addProperty(defaultDatasetPro, datasetBlankRes);
		Resource datasetRes = wholeModel.createResource(URICollection.RESOURCE_SD_DATA_SET);
		datasetBlankRes.addProperty(typePro, datasetRes);

		HashMap<String, Resource> datasetResourceTable = new HashMap<String, Resource>();
		datasetResourceTable.put("", datasetBlankRes);

		if (graphURIs != null && graphURIs.length != 0) {
			Property namedGraphPro = wholeModel.createProperty(URICollection.PROPERTY_SD_NAMED_GRAPH);
			Resource namedGraphRes = wholeModel.createResource(URICollection.RESOURCE_SD_NAMED_GRAPH);
			Property namePro = wholeModel.createProperty(URICollection.PROPERTY_SD_NAME);
			Property graphPro = wholeModel.createProperty(URICollection.PROPERTY_SD_GRAPH);
			Resource graphRes = wholeModel.createResource(URICollection.RESOURCE_SD_GRAPH);
			Resource voidDatasetRes = wholeModel.createResource(URICollection.RESOURCE_VOID_DATASET);
			for (String graphURI : graphURIs) {
				Resource gRes = wholeModel.createResource(AnonId.create());
				datasetBlankRes.addProperty(namedGraphPro, gRes);
				gRes.addProperty(typePro, namedGraphRes);
				Resource gIRI = wholeModel.createResource(graphURI);
				gRes.addProperty(namePro, gIRI);
				Resource sdgRes = wholeModel.createResource(AnonId.create());
				gRes.addProperty(graphPro, sdgRes);
				sdgRes.addProperty(typePro, voidDatasetRes);
				sdgRes.addProperty(typePro, graphRes);
				datasetResourceTable.put(graphURI, sdgRes);
			}
		}

		SchemaCategory schema = null;
		if (graphURIs.length == 0) {
			Date startDate = new Date();
			long start = startDate.getTime();
			boolean errorState = false;
			String logFileName = new File(impl.outDir, "log_" + impl.crawlName + ".txt").getCanonicalPath();
			String recoveryLogFileName = new File(impl.outDir, "log_recovery_" + impl.crawlName + ".txt")
					.getCanonicalPath();
			String todoFileName = new File(impl.outDir, "todo_" + impl.crawlName + ".txt").getCanonicalPath();
			String resultTurtleFileName = new File(impl.outDir, "turtle_" + impl.crawlName + ".ttl").getCanonicalPath();

			logFileManager = new LogFileManager(logFileName);
			logFileManager.writeEndpointURL(impl.endpointURI);
			logFileManagerForRecovery = new LogFileManager(recoveryLogFileName, true);
			// todoFile
			todoFileManager = new TodoFileManager(todoFileName);
			todoFileManager.writeEndpointURL(impl.endpointURI);

			try {
				schema = impl.determineSchemaCategory(wholeModel, datasetResourceTable.get(""));
				schema.write2File(resultTurtleFileName, "Turtle");
				System.out.println("Category:" + schema.category);

			} catch (Exception ex) {
				ex.printStackTrace();
				errorState = true;
			}
			Date endDate = new Date();
			long end = endDate.getTime();
			System.out.println((end - start) + " msec.");
			todoFileManager.close();
			logFileManager.writeStartEndTime(startDate, endDate);
			logFileManager.close();
			if (errorState) {
				System.err.println("Error occured.");
			}
		} else {
			boolean errorState = false;

			// String[] extGraphURIs = new String[graphURIs.length + 1];
			// extGraphURIs[0] = null;
			// for (int i = 0; i < graphURIs.length; i++) {
			// extGraphURIs[i + 1] = graphURIs[i];
			// }
			// graphURIs = extGraphURIs;
			int gCount = 1;
			for (String graphURI : graphURIs) {
				errorState = false;
				System.out.println("-----------------------------------------------------------");
				System.out.println("  Graph: " + graphURI + "        " + gCount + " / " + graphURIs.length);
				System.out.println("-----------------------------------------------------------");
				Date startDate = new Date();
				long start = startDate.getTime();

				String dbURI = null;
				if (graphURI != null) {
					dbURI = graphURI.substring(graphURI.lastIndexOf("/") + 1);
					dbURI = dbURI.replaceAll("\\.", "_");
					dbURI = dbURI.replaceAll("\\:", "_");
					if (dbURI.length() > 32) {
						dbURI = dbURI.substring(0, 32);
					}
				} else {
					dbURI = "";
				}
				dbURI = dbURI + "_" + gCount;
				gCount++;
				String logFileName = new File(impl.outDir, "log_" + impl.crawlName + "_" + dbURI + ".txt")
						.getCanonicalPath();
				String recoveryLogFileName = new File(impl.outDir,
						"log_recovery_" + impl.crawlName + "_" + dbURI + ".txt").getCanonicalPath();
				String todoFileName = new File(impl.outDir, "todo_" + impl.crawlName + "_" + dbURI + ".txt")
						.getCanonicalPath();
				String resultTurtleFileName = new File(impl.outDir, "turtle_" + impl.crawlName + "_" + dbURI + ".ttl")
						.getCanonicalPath();

				System.out.println(resultTurtleFileName);

				logFileManager = new LogFileManager(logFileName);
				logFileManager.writeEndpointURL(impl.endpointURI);
				logFileManagerForRecovery = new LogFileManager(recoveryLogFileName, true);
				// todoFile
				todoFileManager = new TodoFileManager(todoFileName);
				todoFileManager.writeEndpointURL(impl.endpointURI);

				// graphURIs = new String[0];
				if (graphURI != null) {
					impl.setGraphURIs(new String[] { graphURI });
				} else {
					impl.setGraphURIs(null);
				}
				try {
					if (graphURI != null) {
						schema = impl.determineSchemaCategory(wholeModel, datasetResourceTable.get(graphURI));
					} else {
						schema = impl.determineSchemaCategory(wholeModel, datasetResourceTable.get(""));
					}
					schema.write2File(resultTurtleFileName, "Turtle");
					System.out.println("OutputFile: " + resultTurtleFileName);
					System.out.println("Category:" + schema.category);

				} catch (Exception ex) {
					ex.printStackTrace();
					errorState = true;
				}
				Date endDate = new Date();
				long end = endDate.getTime();
				System.out.println((end - start) + " msec.");
				todoFileManager.close();
				logFileManager.writeStartEndTime(startDate, endDate);
				logFileManager.close();
				if (errorState) {
					System.err.println("Error occured.");
					throw new Exception("Error occured s(341)");
				}
			} // end of for
		}
	}

	private synchronized void interval() throws Exception {
		try {
			wait(INTERVAL);
		} catch (InterruptedException e) {
		}
	}

	private synchronized void interval_error() throws Exception {
		try {
			wait(INTERVAL_ERROR);
		} catch (InterruptedException e) {
		}
	}

	public void setGraphURIs(String[] graphURIs) {
		this.graphURIs = graphURIs;
	}

	public String[] getGraphURIs() throws Exception {
		// step: getGraphURIs
		String stepName = "getGraphURIs";

		// QUERY
		// ---------------------------------------------------------------------------------
		// obtains all graphs on the SPARQL endpoint.
		// ---------------------------------------------------------------------------------------
		StringBuffer queryBuffer = new StringBuffer();
		queryBuffer.append("PREFIX owl: <" + URICollection.PREFIX_OWL + ">\n");
		queryBuffer.append("PREFIX rdfs: <" + URICollection.PREFIX_RDFS + ">\n");
		queryBuffer.append("PREFIX rdf: <" + URICollection.PREFIX_RDF + ">\n");
		queryBuffer.append("SELECT DISTINCT ?g\n");
		queryBuffer.append("WHERE{\n");
		queryBuffer.append(" GRAPH ?g{ ?s ?p ?o.}\n");
		queryBuffer.append("}");

		String queryString = queryBuffer.toString();

		// System.out.println(queryString);

		Query query = QueryFactory.create(queryString);

		QueryExecution qexec = null;
		ResultSet results = null;
		String[] recoveryStrings = null;
		try {
			// long start = System.currentTimeMillis();
			qexec = QueryExecutionFactory.sparqlService(endpointURI, query);
			interval();
			results = qexec.execSelect();
			// long end = System.currentTimeMillis();
			// System.out.println("EXEC TIME: " + (end - start));
		} catch (Exception ex) {
			ex.printStackTrace();
			if (logFileManagerForRecovery != null) {
				recoveryStrings = logFileManagerForRecovery.recoverStrings(stepName, null);
			}
			if (recoveryStrings == null) {
				// write to todoFile
				if (todoFileManager != null) {
					todoFileManager.writeQuery(stepName, null, queryString);
				}
				throw ex;
			}
		}

		// results
		ArrayList<String> graphList = new ArrayList<String>();
		if (recoveryStrings != null) {
			for (String uri : recoveryStrings) {
				if (uriFilter(uri, graphURIFilter) != null) {
					graphList.add(uri);
				}
			}
		} else {
			ArrayList<String> forLog = new ArrayList<String>();
			for (; results.hasNext();) {
				QuerySolution sol = results.next();
				// label
				Resource graph = sol.getResource("g");
				forLog.add(graph.getURI());
				if (uriFilter(graph.getURI(), graphURIFilter) != null) {
					graphList.add(graph.getURI());
				}
			}

			// write to Log
			if (logFileManager != null) {
				logFileManager.writeResult(stepName, null, forLog.toArray(new String[0]));
			}
		}
		qexec.close();

		String[] resultList = graphList.toArray(new String[0]);
		return resultList;
	}

	int endpointAccessCount = 0;

	public SchemaCategory determineSchemaCategory(Model wholeModel, Resource datasetResource) throws Exception {
		Calendar start = GregorianCalendar.getInstance();
		endpointAccessCount = 0;
		String[] res1 = null;
		// try {
		res1 = getRDFProperties();
		// } catch (Exception ex) {
		// HashSet<String> res1Set = new HashSet<String>();
		// for (String gURI : graphURIs) {
		// String[] ss = getRDFProperties(gURI);
		// if (ss != null) {
		// for (String s : ss) {
		// res1Set.add(s);
		// }
		// }
		// }
		// res1 = res1Set.toArray(new String[0]);
		// }

		System.out.println("\nproperties");
		for (String uri : res1) {
			System.out.println(uri);
		}
		System.out.println();

		String[] datatypes = new String[0];

		try {
			datatypes = getDatatypes(res1);
		} catch (Exception ex) {
			ex.printStackTrace();
			// TODO
			// throw ex;
		}

		System.out.println("datatypes");
		for (String uri : datatypes) {
			System.out.println(uri);
		}
		System.out.println();

		String[] res3 = null;

		try {
			res3 = getDeclaredRDFsClasses();
		} catch (Exception ex) {
			// TODO
			// throw ex;
		}

		HashSet<String> datatypeSet = new HashSet<String>();
		for (String datatype : datatypes) {
			datatypeSet.add(datatype);
		}
		ArrayList<String> tmpStrList = new ArrayList<String>();
		for (String uri : res3) {
			if (!datatypeSet.contains(uri)) {
				tmpStrList.add(uri);
			}
		}
		res3 = tmpStrList.toArray(new String[0]);

		System.out.println("classes");
		for (String uri : res3) {
			System.out.println(uri);
		}
		System.out.println();

		System.out.println("\n#Decl Datatype(total): " + datatypes.length);
		System.out.println("#Decl Class(total): " + res3.length);
		System.out.println("#Decl Property(total): " + res1.length);

		//
		// Further schema analysis
		//

		// Model model = null;
		// model = ModelFactory.createDefaultModel();
		// Property endpointPro = model
		// .createProperty(URICollection.PROPERTY_SD_ENDPOINT);
		// Resource serviceRes = model
		// .createResource(URICollection.RESOURCE_SD_SERVICE);
		// Resource endpointRes = model.createResource(endpointURI);
		// Property typePro = model
		// .createProperty(URICollection.PROPERTY_RDF_TYPE);
		//
		// Resource thisRes = model.createResource("");
		// thisRes.addProperty(typePro, serviceRes);
		// thisRes.addProperty(endpointPro, endpointRes);
		//
		// // dataset
		// Property defaultDatasetPro = model
		// .createProperty(URICollection.PROPERTY_SD_DEFAULT_DATA_SET);
		// Resource datasetBlankRes = model.createResource(AnonId.create());
		// thisRes.addProperty(defaultDatasetPro, datasetBlankRes);
		// Resource datasetRes = model
		// .createResource(URICollection.RESOURCE_SD_DATA_SET);
		// datasetBlankRes.addProperty(typePro, datasetRes);
		// // graphs
		// if (graphURIs != null && graphURIs.length != 0) {
		// Property namedGraphPro = model
		// .createProperty(URICollection.PROPERTY_SD_NAMED_GRAPH);
		// Resource namedGraphRes = model
		// .createResource(URICollection.RESOURCE_SD_NAMED_GRAPH);
		// Property namePro = model
		// .createProperty(URICollection.PROPERTY_SD_NAME);
		// for (String graphURI : graphURIs) {
		// Resource gRes = model.createResource(AnonId.create());
		// datasetBlankRes.addProperty(namedGraphPro, gRes);
		// gRes.addProperty(typePro, namedGraphRes);
		// Resource gIRI = model.createResource(graphURI);
		// gRes.addProperty(namePro, gIRI);
		// }
		// }

		Schema schema = null;
		if (res1.length != 0 && res3.length != 0) {
			schema = getPropertySchema(wholeModel, datasetResource, res1, datatypeSet);
			if (schema.propertyCategory == 4) {
				schema.classCategory = 4;
			} else {
				schema = getClassSchema(schema, res3);
			}
		} else {
			schema = new Schema(wholeModel, datasetResource, 4, null, 3, 4, 0, null, null);
		}
		// model = schema.model;
		int category = 0;
		// if (schema.propertyCategory == 1 && schema.classCategory == 1) {
		// category = 1;
		// } else {
		// if (schema.propertyCategory == 2 && schema.classCategory == 1) {
		// category = 2;
		// } else {
		// if (schema.propertyCategory == 4 || schema.classCategory == 3) {
		// category = 4;
		// } else {
		// category = 3;
		// }
		// }
		// }

		System.out.println("PropertyCategory: " + schema.propertyCategory + "  ClassCategory: " + schema.classCategory);

		if (schema.propertyCategory <= 2 || schema.classCategory == 1) {
			category = 1;
		} else {
			if (schema.classCategory == 3) {
				category = 3;
			} else {
				category = 2;
			}
		}

		// triples
		// int numTriples = schema.numTriples;
		// Property triplesPro =
		// model.createProperty(URICollection.PROPERTY_VOID_TRIPLES);
		// schema.datasetResource.addLiteral(triplesPro, numTriples);

		// classes
		// int numClasses = schema.inferedClasses.size();
		// Property classesPro =
		// model.createProperty(URICollection.PROPERTY_VOID_CLASSES);
		// schema.datasetResource.addLiteral(classesPro, numClasses);

		// datatypes
		// Property datatypesPro =
		// model.createProperty(URICollection.PROPERTY_SB_DATATYPES);
		// int numDatatypes = schema.datatypes.size();
		// schema.datasetResource.addLiteral(datatypesPro, numDatatypes);

		Property typePro = wholeModel.createProperty(URICollection.PROPERTY_RDF_TYPE);

		Resource crawlLogRes = wholeModel.createResource(URICollection.RESOURCE_SB_CRAWL_LOG);
		Property crawlLogPro = wholeModel.createProperty(URICollection.PROPERTY_SB_CRAWL_LOG);
		Resource clBlankRes = wholeModel.createResource(AnonId.create());
		schema.datasetResource.addProperty(crawlLogPro, clBlankRes);
		clBlankRes.addProperty(typePro, crawlLogRes);
		Property crawlStartTimePro = wholeModel.createProperty(URICollection.PROPERTY_SB_CRAWL_START_TIME);
		clBlankRes.addLiteral(crawlStartTimePro, wholeModel.createTypedLiteral(start));
		Property crawlEndTimePro = wholeModel.createProperty(URICollection.PROPERTY_SB_CRAWL_END_TIME);
		Calendar end = GregorianCalendar.getInstance();
		clBlankRes.addLiteral(crawlEndTimePro, wholeModel.createTypedLiteral(end));
		Property endpointAccessesPro = wholeModel.createProperty(URICollection.PROPERTY_SB_ENDPOINT_ACCESSES);
		clBlankRes.addLiteral(endpointAccessesPro, endpointAccessCount);

		Property catPro = wholeModel.createProperty(URICollection.PROPERTY_SB_ENDPOINT_CATEGORY);
		// schema.datasetResource.addLiteral(catPro, schema.endpointCategory);
		schema.datasetResource.addLiteral(catPro, schema.classCategory);
		Property clsCatPro = wholeModel.createProperty(URICollection.PROPERTY_SB_CLASS_CATEGORY);
		schema.datasetResource.addLiteral(clsCatPro, schema.classCategory);
		Property proCatPro = wholeModel.createProperty(URICollection.PROPERTY_SB_PROPERTY_CATEGORY);
		schema.datasetResource.addLiteral(proCatPro, schema.propertyCategory);

		// Property numTriplesPro = model
		// .createProperty(URICollection.PROPERTY_SB_NUMBER_OF_TRIPLES);
		// datasetRes.addLiteral(numTriplesPro, schema.numTriples);

		System.out.println("#EndpointAccess: " + endpointAccessCount);

		return new SchemaCategory(category, wholeModel);
	}

	public Schema getClassSchema(Schema schema, String[] classURIs) throws Exception {
		// String[] filterStrs = URICollection.FILTER_CLASS;
		// String[] unfilterStrs = URICollection.UNFILTER_CLASS;

		HashSet<String> inferedClasses = schema.inferedClasses;
		int classCategory = 1;
		int cnt = 0;
		for (String classURI : classURIs) {
			if (inferedClasses.contains(classURI)) {
				cnt++;
			}
		}
		if (cnt == inferedClasses.size()) {
			classCategory = 1;
		} else {
			if (cnt == 0) {
				classCategory = 3;
			} else {
				classCategory = 2;
			}
		}

		Model model = schema.model;
		// create model
		Property labelPro = model.createProperty(URICollection.PROPERTY_RDFS_LABEL);
		Property entitiesPro = model.createProperty(URICollection.PROPERTY_VOID_ENTITIES);
		Property classPartitionPro = model.createProperty(URICollection.PROPERTY_VOID_CLASS_PARTITION);
		Property classPro = model.createProperty(URICollection.PROPERTY_VOID_CLASS);

		ArrayList<Resource> classList = new ArrayList<Resource>();

		// for each class, obtains its labels
		for (String clsURI : inferedClasses) {
			Resource cls = model.createResource(clsURI);
			Resource cpBlankRes = model.createResource(AnonId.create());
			schema.datasetResource.addProperty(classPartitionPro, cpBlankRes);
			cpBlankRes.addProperty(classPro, cls);

			classList.add(cls);

			// QUERY
			// ---------------------------------------------------------------------------------
			// obtains all labels associated with the class
			// ---------------------------------------------------------------------------------------
			StringBuffer queryBuffer = new StringBuffer();
			queryBuffer.append("PREFIX owl: <" + URICollection.PREFIX_OWL + ">\n");
			queryBuffer.append("PREFIX rdfs: <" + URICollection.PREFIX_RDFS + ">\n");
			queryBuffer.append("PREFIX rdf: <" + URICollection.PREFIX_RDF + ">\n");
			queryBuffer.append("SELECT DISTINCT ?label\n");
			if (graphURIs != null && graphURIs.length != 0) {
				for (String graphURI : graphURIs) {
					queryBuffer.append("FROM <");
					queryBuffer.append(graphURI);
					queryBuffer.append(">\n");
				}
			}
			queryBuffer.append("WHERE{\n");
			queryBuffer.append("  <" + clsURI + "> rdfs:label ?label.\n");
			queryBuffer.append("}");

			String queryString = queryBuffer.toString();

			// System.out.println(queryString);

			Query query = QueryFactory.create(queryString);

			QueryExecution qexec = null;
			ResultSet results = null;
			try {
				// long start = System.currentTimeMillis();
				qexec = QueryExecutionFactory.sparqlService(endpointURI, query);
				endpointAccessCount++;
				interval();
				results = qexec.execSelect();
				// long end = System.currentTimeMillis();
				// System.out.println("EXEC TIME: " + (end - start));
				System.out.print("L");
			} catch (Exception ex) {
				ex.printStackTrace();
				System.out.println(queryString);
				throw ex;
			}

			for (; results.hasNext();) {
				QuerySolution sol = results.next();
				// label
				Literal labelLiteral = sol.getLiteral("label");
				if (labelLiteral != null) {
					String label = labelLiteral.getString();
					cls.addLiteral(labelPro, label);
				}
			}
			qexec.close();

			// QUERY
			// ---------------------------------------------------------------------------------
			// obtains the number of instances of the class by checking triples
			// <?i,rdf:type,cls>,
			// (<[],?p,?i>,<?p,rdfs:range,cls>), and
			// (<?i,?p,[]>,<?p,rdfs:domain,cls>).
			// ---------------------------------------------------------------------------------------
			queryBuffer = new StringBuffer();
			queryBuffer.append("PREFIX owl: <" + URICollection.PREFIX_OWL + ">\n");
			queryBuffer.append("PREFIX rdfs: <" + URICollection.PREFIX_RDFS + ">\n");
			queryBuffer.append("PREFIX rdf: <" + URICollection.PREFIX_RDF + ">\n");
			queryBuffer.append("SELECT (count(DISTINCT ?i)  AS ?num) \n");
			if (graphURIs != null && graphURIs.length != 0) {
				for (String graphURI : graphURIs) {
					queryBuffer.append("FROM <");
					queryBuffer.append(graphURI);
					queryBuffer.append(">\n");
				}
			}
			queryBuffer.append("WHERE{\n");
			queryBuffer.append("  {?i rdf:type <" + cls.getURI() + ">.}\n");
			queryBuffer.append(" UNION  {[] ?p ?i.  ?p rdfs:range <" + cls.getURI() + ">.}\n");
			queryBuffer.append(" UNION  {?i ?p [].  ?p rdfs:domain <" + cls.getURI() + ">.}\n");
			queryBuffer.append("}");

			queryString = queryBuffer.toString();

			// System.out.println(queryString);

			query = QueryFactory.create(queryString);

			qexec = null;
			results = null;

			int sCount = 3;
			while (sCount > 0) {
				try {
					// long start = System.currentTimeMillis();
					qexec = QueryExecutionFactory.sparqlService(endpointURI, query);
					endpointAccessCount++;
					interval();
					results = qexec.execSelect();
					// long end = System.currentTimeMillis();
					// System.out.println("EXEC TIME: " + (end - start));
					break;
				} catch (Exception ex) {
					sCount--;
					if (sCount == 0) {
						System.out.println(queryString);
						ex.printStackTrace();
						throw ex;
					} else {
						interval_error();
						interval_error();
					}

				}
			}

			// create model
			for (; results.hasNext();) {
				QuerySolution sol = results.next();
				Literal lit = sol.getLiteral("num");
				if (lit != null) {
					int num = lit.getInt();
					cpBlankRes.addLiteral(entitiesPro, num);
				}
			}
			qexec.close();
		}

		// subclassOf
		StringBuffer queryBuffer = new StringBuffer();
		queryBuffer.append("PREFIX owl: <" + URICollection.PREFIX_OWL + ">\n");
		queryBuffer.append("PREFIX rdfs: <" + URICollection.PREFIX_RDFS + ">\n");
		queryBuffer.append("PREFIX rdf: <" + URICollection.PREFIX_RDF + ">\n");
		queryBuffer.append("SELECT DISTINCT ?c ?d \n");
		if (graphURIs != null && graphURIs.length != 0) {
			for (String graphURI : graphURIs) {
				queryBuffer.append("FROM <");
				queryBuffer.append(graphURI);
				queryBuffer.append(">\n");
			}
		}
		queryBuffer.append("WHERE{\n");
		queryBuffer.append(" ?c rdfs:subclassOf ?d.\n");
		// queryBuffer.append("FILTER(! contains(str(?c),\"openlinksw\"))\n");
		queryBuffer.append("}");

		String queryString = queryBuffer.toString();

		// System.out.println(queryString);

		Query query = QueryFactory.create(queryString);
		QueryExecution qexec = null;
		ResultSet results = null;
		try {
			// long start = System.currentTimeMillis();
			qexec = QueryExecutionFactory.sparqlService(endpointURI, query);
			endpointAccessCount++;
			results = qexec.execSelect();
			// // long end = System.currentTimeMillis();
			// // System.out.println("EXEC TIME: " + (end - start));
			System.out.println("sb");
		} catch (Exception ex) {
			ex.printStackTrace();
			throw ex;
		}

		Property subClsOf = model.createProperty(URICollection.PROPERTY_RDFS_SUBCLASSOF);
		String[] filterStrs = URICollection.FILTER_CLASS;
		for (; results.hasNext();) {
			QuerySolution sol = results.next();
			Resource cCls = sol.getResource("c");
			Resource pCls = sol.getResource("d");
			if (cCls != null && pCls != null && uriFilter(cCls.getURI(), filterStrs) != null) {
				cCls = model.createResource(cCls.getURI());
				pCls = model.createResource(pCls.getURI());
				cCls.addProperty(subClsOf, pCls);
			}
		}
		qexec.close();

		schema.model = model;
		schema.classCategory = classCategory;
		return schema;
	}

	class ResourceNumberPair {
		public Resource resource = null;
		public int number = 0;

		ResourceNumberPair(Resource resource, int number) {
			this.resource = resource;
			this.number = number;
		}
	}

	public Schema getPropertySchema(Model model, Resource datasetRes, String[] propertyURIs,
			HashSet<String> datatypeSet) throws Exception {
		// String[] filterStrs = { "http://www.openlinksw" };

		// create model
		Property domainPro = model.createProperty(URICollection.PROPERTY_RDFS_DOMAIN);
		Property rangePro = model.createProperty(URICollection.PROPERTY_RDFS_RANGE);

		// Property inferedDomainPro = model
		// .createProperty("http://sparqlbuilder.org/inferedDomain");
		// Property inferedRangePro = model
		// .createProperty("http://sparqlbuilder.org/inferedRange");

		Property typePro = model.createProperty(URICollection.PROPERTY_RDF_TYPE);
		Property labelPro = model.createProperty(URICollection.PROPERTY_RDFS_LABEL);
		Resource clsCls = model.createResource(URICollection.RESOURCE_RDFS_CLASS);
		Resource propRes = model.createResource(URICollection.CLASS_RDF_PROPERTY);

		// Property numTriplePro = model
		// .createProperty(URICollection.PROPERTY_SB_NUMBER_OF_TRIPLES);
		Property distinctSubjectsPro = model.createProperty(URICollection.PROPERTY_VOID_DISTINCT_SUBJECTS);
		Property distinctObjectsPro = model.createProperty(URICollection.PROPERTY_VOID_DISTINCT_OBJECTS);

		Property subjectClassesPro = model.createProperty(URICollection.PROPERTY_SB_SUBJECT_CLASSES);
		Property objectClassesPro = model.createProperty(URICollection.PROPERTY_SB_OBJECT_CLASSES);
		Property objectDatatypesPro = model.createProperty(URICollection.PROPERTY_SB_OBJECT_DATATYPES);

		Property classesPro = model.createProperty(URICollection.PROPERTY_VOID_CLASSES);
		Property datatypesPro = model.createProperty(URICollection.PROPERTY_SB_DATATYPES);

		// Property numDomClsNumInsPro = model
		// .createProperty(URICollection.PROPERTY_SB_NUMBER_OF_INSTANCES_OF_DOMAIN_CLASS);
		// Property numRanClsNumInsPro = model
		// .createProperty(URICollection.PROPERTY_SB_NUMBER_OF_INSTANCES_OF_RANGE_CLASS);
		// Property indCatPro = model
		// .createProperty(URICollection.PROPERTY_SB_INDIVIDUAL_PROPERTY_CATEGORY);

		Property propCatPro = model.createProperty(URICollection.PROPERTY_SB_PROPERTY_CATEGORY);

		Resource classRelationCls = model.createResource(URICollection.RESOURCE_SB_CLASS_RELATION);
		Property subjectClsPro = model.createProperty(URICollection.PROPERTY_SB_SUBJECT_CLASS);
		Property objectClsPro = model.createProperty(URICollection.PROPERTY_SB_OBJECT_CLASS);
		Property objectDatatypePro = model.createProperty(URICollection.PROPERTY_SB_OBJECT_DATATYPE);
		Property propPro = model.createProperty(URICollection.PROPERTY_VOID_PROPERTY);
		// Resource propProfileCls = model
		// .createResource(URICollection.RESOURCE_SB_PROPERTY_PROFILE);
		// Property isDomClsLimPro = model
		// .createProperty(URICollection.PROPERTY_SB_START_CLASS_LIMITED_Q);
		// Property isRanClsLimPro = model
		// .createProperty(URICollection.PROPERTY_SB_END_CLASS_LIMITED_Q);

		Property propertyPartitionPro = model.createProperty(URICollection.PROPERTY_VOID_PROPERTY_PARTITION);
		Property propertyPro = model.createProperty(URICollection.PROPERTY_VOID_PROPERTY);
		Property triplesPro = model.createProperty(URICollection.PROPERTY_VOID_TRIPLES);
		Property searchableTriplesPro = model.createProperty(URICollection.PROPERTY_SB_SEARCHABLE_TRIPLES);
		Property classRelationPro = model.createProperty(URICollection.PROPERTY_SB_CLASS_RELATION);

		HashSet<String> classURISet = new HashSet<String>();
		int wholePropertyCategory = 0;
		int totalNumTriples = 0;
		int[] countProperties = new int[4];
		long[] countAllTriples = new long[4];
		long[] countClassTriples = new long[4];

		int propCnt = 0;
		int propLen = propertyURIs.length;

		// for each property...
		for (String propURI : propertyURIs) {
			propCnt++;
			System.out.println("(" + propCnt + "/" + propLen + ")  " + propURI);
			HashSet<String> domClassSet = new HashSet<String>();
			HashSet<String> ranClassSet = new HashSet<String>();
			HashSet<String> literalDatatypeSet = new HashSet<String>();

			try {
				// QUERY
				// ---------------------------------------------------------------------------------
				// obtains all domain classes of the property by checking
				// triples <prop,rdfs:domain,?d>.
				// ---------------------------------------------------------------------------------------
				StringBuffer queryBuffer = new StringBuffer();
				queryBuffer.append("PREFIX owl: <" + URICollection.PREFIX_OWL + ">\n");
				queryBuffer.append("PREFIX rdfs: <" + URICollection.PREFIX_RDFS + ">\n");
				queryBuffer.append("PREFIX rdf: <" + URICollection.PREFIX_RDF + ">\n");
				queryBuffer.append("SELECT DISTINCT ?d\n");
				if (graphURIs != null && graphURIs.length != 0) {
					for (String graphURI : graphURIs) {
						queryBuffer.append("FROM <");
						queryBuffer.append(graphURI);
						queryBuffer.append(">\n");
					}
				}
				queryBuffer.append("WHERE{\n");
				queryBuffer.append(" <" + propURI + "> rdfs:domain ?d.\n");
				queryBuffer.append("}");
				String queryString = queryBuffer.toString();
				// System.out.println(queryString);
				Query query = QueryFactory.create(queryString);

				QueryExecution qexec = null;
				ResultSet results = null;
				try {
					// long start = System.currentTimeMillis();
					qexec = QueryExecutionFactory.sparqlService(endpointURI, query);
					endpointAccessCount++;
					interval();
					results = qexec.execSelect();
					// long end = System.currentTimeMillis();
					// System.out.println("EXEC TIME: " + (end - start));
					System.out.print("d");
				} catch (Exception ex) {
					System.out.println(queryString);
					ex.printStackTrace();
					throw ex;
				}
				for (; results.hasNext();) {
					QuerySolution sol = results.next();
					Resource dom = sol.getResource("d");
					if (dom != null && dom.getURI() != null) {
						domClassSet.add(dom.getURI());
					}
				}
				System.out.print("(" + domClassSet.size() + ")");
				qexec.close();

				// range
				// QUERY
				// ---------------------------------------------------------------------------------
				// obtains all range classes of the property by checking triples
				// <prop,rdfs:range,?r>.
				// ---------------------------------------------------------------------------------------
				queryBuffer = new StringBuffer();
				queryBuffer.append("PREFIX owl: <" + URICollection.PREFIX_OWL + ">\n");
				queryBuffer.append("PREFIX rdfs: <" + URICollection.PREFIX_RDFS + ">\n");
				queryBuffer.append("PREFIX rdf: <" + URICollection.PREFIX_RDF + ">\n");
				queryBuffer.append("SELECT DISTINCT ?r\n");
				if (graphURIs != null && graphURIs.length != 0) {
					for (String graphURI : graphURIs) {
						queryBuffer.append("FROM <");
						queryBuffer.append(graphURI);
						queryBuffer.append(">\n");
					}
				}
				queryBuffer.append("WHERE{\n");
				queryBuffer.append(" <" + propURI + "> rdfs:range ?r.\n");
				queryBuffer.append("}");
				queryString = queryBuffer.toString();
				// System.out.println(queryString);
				query = QueryFactory.create(queryString);

				qexec = null;
				results = null;
				try {
					// long start = System.currentTimeMillis();
					qexec = QueryExecutionFactory.sparqlService(endpointURI, query);
					endpointAccessCount++;
					interval();
					results = qexec.execSelect();
					// long end = System.currentTimeMillis();
					// System.out.println("EXEC TIME: " + (end - start));
					System.out.print("r");
				} catch (Exception ex) {
					System.out.println(queryString);
					ex.printStackTrace();
					throw ex;
				}
				for (; results.hasNext();) {
					QuerySolution sol = results.next();
					Resource ran = sol.getResource("r");
					if (ran != null && ran.getURI() != null) {
						// remove rdfs:literal
						if (!ran.getURI().equals(URICollection.PROPERTY_RDFS_LITERAL)) {
							if (!datatypeSet.contains(ran.getURI())) {
								ranClassSet.add(ran.getURI());
							}
						}
					}
				}
				System.out.print("(" + ranClassSet.size() + ")");
				qexec.close();

				// inference
				ArrayList<PropertyDomainRangeDecl> propDomRanDeclList = new ArrayList<PropertyDomainRangeDecl>();
				ArrayList<PropertyDomainRangeDecl> propDomRanDeclList2 = new ArrayList<PropertyDomainRangeDecl>();
				// QUERY
				// ---------------------------------------------------------------------------------
				queryBuffer = new StringBuffer();
				queryBuffer.append("PREFIX owl: <" + URICollection.PREFIX_OWL + ">\n");
				queryBuffer.append("PREFIX rdfs: <" + URICollection.PREFIX_RDFS + ">\n");
				queryBuffer.append("PREFIX rdf: <" + URICollection.PREFIX_RDF + ">\n");
				// queryBuffer
				// .append("SELECT ?d ?r (count(?i ?o) AS ?numTriples)
				// (count(DISTINCT ?i) AS ?numDomIns) (count(DISTINCT ?o) AS
				// ?numRanIns)\n");
				queryBuffer.append("SELECT DISTINCT ?d ?r\n");

				if (graphURIs != null && graphURIs.length != 0) {
					for (String graphURI : graphURIs) {
						queryBuffer.append("FROM <");
						queryBuffer.append(graphURI);
						queryBuffer.append(">\n");
					}
				}
				queryBuffer.append("WHERE{\n");
				queryBuffer.append("   ?i <" + propURI + "> ?o. \n");
				queryBuffer.append("OPTIONAL{ ?i rdf:type ?d.}\n");
				queryBuffer.append("OPTIONAL{ ?o rdf:type ?r.}\n");
				queryBuffer.append("}");
				queryString = queryBuffer.toString();
				// System.out.println(queryString);
				query = QueryFactory.create(queryString);

				qexec = null;
				results = null;

				int sCount = 3;
				while (sCount > 0) {

					try {
						// long start = System.currentTimeMillis();
						qexec = QueryExecutionFactory.sparqlService(endpointURI, query);
						endpointAccessCount++;
						qexec.setTimeout(-1);
						interval();
						results = qexec.execSelect();
						// long end = System.currentTimeMillis();
						// System.out.println("EXEC TIME: " + (end - start));
						System.out.print("i");
						break;
					} catch (Exception ex) {
						sCount--;
						if (sCount == 0) {
							System.out.println(queryString);
							ex.printStackTrace();
							throw ex;
						} else {
							interval_error();
						}

					}

				}
				// domain-range pair
				System.out.println("Sol: " + results.getRowNumber());
				for (; results.hasNext();) {
					QuerySolution sol = results.next();
					Resource dom = sol.getResource("d");
					Resource ran = sol.getResource("r");
					String domURI = null;
					if (dom != null) {
						domURI = dom.getURI();
					}
					String ranURI = null;
					if (ran != null) {
						if (!ran.getURI().equals(URICollection.PROPERTY_RDFS_LITERAL)
								&& !datatypeSet.contains(ran.getURI())) {
							ranURI = ran.getURI();
						}
					}

					// System.out.println(domURI + "--" + ranURI);
					// if( dom == null && ran == null ){
					// System.out.println("NullNull");
					// }

					PropertyDomainRangeDecl pdrd = new PropertyDomainRangeDecl(propURI, domURI, ranURI, null, 0, 0, 0,
							false, false);
					if (!propDomRanDeclList.contains(pdrd)) {
						propDomRanDeclList.add(pdrd);
					} else {
						System.err.println("ERROR: duplicate PDRD found! (L1144): ");
						System.out.println(pdrd.getDomainClass() + "-----" + pdrd.getRangeClass() + ","
								+ pdrd.getLiteralDataType());
					}
				}
				System.out.println("done");
				qexec.close();

				// literal
				// QUERY
				// ---------------------------------------------------------------------------------
				queryBuffer = new StringBuffer();
				queryBuffer.append("PREFIX owl: <" + URICollection.PREFIX_OWL + ">\n");
				queryBuffer.append("PREFIX rdfs: <" + URICollection.PREFIX_RDFS + ">\n");
				queryBuffer.append("PREFIX rdf: <" + URICollection.PREFIX_RDF + ">\n");
				// queryBuffer
				// .append("SELECT ?d ?r (count(?i) AS ?numTriples)
				// (count(DISTINCT ?i) AS ?numDomIns) (count(DISTINCT ?o) AS
				// ?numRanIns)\n");
				queryBuffer.append("SELECT DISTINCT ?d (datatype(?o) AS ?ldt)\n");

				if (graphURIs != null && graphURIs.length != 0) {
					for (String graphURI : graphURIs) {
						queryBuffer.append("FROM <");
						queryBuffer.append(graphURI);
						queryBuffer.append(">\n");
					}
				}
				queryBuffer.append("WHERE{\n");
				queryBuffer.append("   ?i <" + propURI + "> ?o. \n");
				queryBuffer.append("OPTIONAL{ ?i rdf:type ?d.}\n");
				queryBuffer.append("}");
				queryString = queryBuffer.toString();
				// System.out.println(queryString);
				query = QueryFactory.create(queryString);

				qexec = null;
				results = null;

				// TODO
				// ---------------------------------------------------------
				// Virtuoso
				// virtuoso error flag
				// ---------------------------------------------------------
				boolean virtuosoErrorFlag = false;

				sCount = 3;
				while (sCount > 0) {

					try {
						// long start = System.currentTimeMillis();
						qexec = QueryExecutionFactory.sparqlService(endpointURI, query);
						qexec.setTimeout(-1);
						endpointAccessCount++;
						interval();
						results = qexec.execSelect();
						// long end = System.currentTimeMillis();
						// System.out.println("EXEC TIME: " + (end - start));
						System.out.print("i");
						break;
					} catch (Exception ex) {
						sCount--;
						if (sCount == 0) {
							System.out.println(queryString);
							// TODO
							// ---------------------------------------------------------
							// Virtuoso
							// virtuoso error flag
							// ---------------------------------------------------------

							ex.printStackTrace();
							// throw ex;
							virtuosoErrorFlag = true;
						} else {
							interval_error();
							interval_error();
						}
					}

				}
				if (virtuosoErrorFlag) {
					System.err.println("Virtuoso ERROR: skip datatype check.");
				} else {
					for (; results.hasNext();) {
						QuerySolution sol = results.next();
						Resource dom = sol.getResource("d");
						String litStr = null;
						try {
							Literal lit = sol.getLiteral("ldt");
							if (lit != null) {
								litStr = lit.getString();
							}
						} catch (Exception ex) {
							Resource litRes = sol.getResource("ldt");
							if (litRes != null) {
								litStr = litRes.getURI();
							}
						}
						if (litStr != null) {
							String ranRef = litStr;
							literalDatatypeSet.add(ranRef);
							// System.out.println(ranRef);

							String domURI = null;
							if (dom != null) {
								domURI = dom.getURI();
							}
							// String ranURI = null;
							// if (ran != null) {
							// ranURI = ran.getURI();
							// }

							// System.out.println(domURI + "--" + ranURI);

							PropertyDomainRangeDecl pdrd = new PropertyDomainRangeDecl(propURI, domURI, null, ranRef, 0,
									0, 0, false, false);
							if (!propDomRanDeclList.contains(pdrd)) {
								propDomRanDeclList.add(pdrd);
							} else {
								System.err.println("ERROR: duplicate PDRD found! (L1259)");
								System.out.println(pdrd.getDomainClass() + "-----" + pdrd.getRangeClass() + ","
										+ pdrd.getLiteralDataType());
							}
						}
					}
				}
				qexec.close();

				// debug
				// for (PropertyDomainRangeDecl pdrd : propDomRanDeclList) {
				// System.out.println(pdrd.getProperty() + " (" +
				// pdrd.getDomainClass()
				// + ",{"+ pdrd.getRangeClass() +
				// ","+pdrd.getLiteralDataType()+"})");
				// }

				System.out.println("\n PDRD (L1274): " + propDomRanDeclList.size());

				for (PropertyDomainRangeDecl pdrd : propDomRanDeclList) {

					if (propDomRanDeclList.size() < 100) {
						System.out.println(pdrd.getDomainClass() + "---" + pdrd.getRangeClass() + ", "
								+ pdrd.getLiteralDataType());
					}

					// QUERY
					// ---------------------------------------------------------------------------------
					queryBuffer = new StringBuffer();
					queryBuffer.append("PREFIX owl: <" + URICollection.PREFIX_OWL + ">\n");
					queryBuffer.append("PREFIX rdfs: <" + URICollection.PREFIX_RDFS + ">\n");
					queryBuffer.append("PREFIX rdf: <" + URICollection.PREFIX_RDF + ">\n");
					queryBuffer.append(
							"SELECT (count(?i) AS ?numTriples) (count(DISTINCT ?i) AS ?numDomIns) (count(DISTINCT ?o) AS ?numRanIns) \n");

					if (graphURIs != null && graphURIs.length != 0) {
						for (String graphURI : graphURIs) {
							queryBuffer.append("FROM <");
							queryBuffer.append(graphURI);
							queryBuffer.append(">\n");
						}
					}
					queryBuffer.append("WHERE{\n");

					queryBuffer.append("SELECT DISTINCT ?i ?o WHERE{\n");
					queryBuffer.append("   ?i <" + propURI + "> ?o. \n");
					if (pdrd.getDomainClass() != null) {
						queryBuffer.append(" ?i rdf:type <" + pdrd.getDomainClass() + ">.\n");
					}
					if (pdrd.getRangeClass() != null) {
						queryBuffer.append("?o rdf:type <" + pdrd.getRangeClass() + ">.\n");
					} else {
						if (pdrd.getLiteralDataType() != null) {
							queryBuffer.append("FILTER( datatype(?o) = <" + pdrd.getLiteralDataType() + ">)\n");
						}
					}
					queryBuffer.append("}\n");
					queryBuffer.append("}");
					queryString = queryBuffer.toString();
					// System.out.println(queryString);

					qexec = null;
					results = null;
					sCount = 5;
					while (sCount > 0) {
						try {
							query = QueryFactory.create(queryString);
							// long start = System.currentTimeMillis();
							qexec = QueryExecutionFactory.sparqlService(endpointURI, query);
							qexec.setTimeout(-1);
							endpointAccessCount++;
							interval();
							results = qexec.execSelect();
							// long end = System.currentTimeMillis();
							// System.out.println("EXEC TIME: " + (end -
							// start));
							System.out.print("p");
							break;
						} catch (Exception ex) {
							sCount--;
							if (sCount == 0) {
								System.out.println(queryString);
								// TODO
								// ---------------------------------------------------------
								// Virtuoso
								// virtuoso error flag
								// ---------------------------------------------------------

								ex.printStackTrace();
								throw ex;
								// virtuosoErrorFlag = true;
							} else {
								interval_error();
								interval_error();
							}
						}
					}

					ArrayList<String> domList = new ArrayList<String>();
					ArrayList<String> ranList = new ArrayList<String>();
					String literalDataType = null;

					for (; results.hasNext();) {
						QuerySolution sol = results.next();
						String domURI = pdrd.getDomainClass();
						if (domURI != null) {
							domList.add(domURI);
						}
						if (domURI == null && domClassSet.size() > 0) {
							Iterator<String> it = domClassSet.iterator();
							while (it.hasNext()) {
								domList.add(it.next());
							}
						}

						String ranURI = pdrd.getRangeClass();
						if (ranURI != null) {
							ranList.add(ranURI);
						}
						if (ranURI == null) {
							if (pdrd.getLiteralDataType() != null) {
								literalDataType = pdrd.getLiteralDataType();
							} else {
								if (ranClassSet.size() > 0) {
									Iterator<String> it = ranClassSet.iterator();
									while (it.hasNext()) {
										ranList.add(it.next());
									}
								}
							}
						}

						Literal lit = sol.getLiteral("numTriples");
						int numTriples = 0;
						if (lit != null) {
							numTriples = lit.getInt();
						}
						lit = sol.getLiteral("numDomIns");
						int numDomIns = 0;
						if (lit != null) {
							numDomIns = lit.getInt();
						}
						lit = sol.getLiteral("numRanIns");
						int numRanIns = 0;
						if (lit != null) {
							numRanIns = lit.getInt();
						}

						for (String domURIs : domList) {
							for (String ranURIs : ranList) {
								PropertyDomainRangeDecl pdrd2 = new PropertyDomainRangeDecl(propURI, domURIs, ranURIs,
										null, numTriples, numDomIns, numRanIns, (domURI == null),
										(ranURI == null && literalDataType == null));
								if (!propDomRanDeclList2.contains(pdrd2)) {
									propDomRanDeclList2.add(pdrd2);
								} else {
									System.err.println("ERROR: duplicate PDRD found! (L1396)");
									System.out.println(pdrd2.getDomainClass() + "-----" + pdrd2.getRangeClass() + ","
											+ pdrd2.getLiteralDataType());

								}
							}
							if (literalDataType != null) {
								PropertyDomainRangeDecl pdrd2 = new PropertyDomainRangeDecl(propURI, domURIs, null,
										literalDataType, numTriples, numDomIns, numRanIns, (domURI == null), false);
								if (!propDomRanDeclList2.contains(pdrd2)) {
									propDomRanDeclList2.add(pdrd2);
								} else {
									System.err.println("ERROR: duplicate PDRD found! (L1405)");
									System.out.println(pdrd2.getDomainClass() + "-----" + pdrd2.getRangeClass() + ","
											+ pdrd2.getLiteralDataType());
								}
							}
						}
					}
					qexec.close();
				} // end of for pdrd

				// DEBUG
				// for (PropertyDomainRangeDecl pdrd : propDomRanDeclList2) {
				// System.out.println(pdrd.getProperty() + " (" +
				// pdrd.getDomainClass()
				// + ",{"+ pdrd.getRangeClass() +
				// ","+pdrd.getLiteralDataType()+"})");
				// }

				// numbers of triples, dom instances and ran instances
				int numDomIns = 0;
				int numRanIns = 0;
				int numTriples = 0;
				queryBuffer = new StringBuffer();
				queryBuffer.append("PREFIX owl: <" + URICollection.PREFIX_OWL + ">\n");
				queryBuffer.append("PREFIX rdfs: <" + URICollection.PREFIX_RDFS + ">\n");
				queryBuffer.append("PREFIX rdf: <" + URICollection.PREFIX_RDF + ">\n");
				queryBuffer.append(
						"SELECT (count(?i) AS ?numTriples) (count(DISTINCT ?i) AS ?numDomIns) (count(DISTINCT ?o) AS ?numRanIns) \n");

				if (graphURIs != null && graphURIs.length != 0) {
					for (String graphURI : graphURIs) {
						queryBuffer.append("FROM <");
						queryBuffer.append(graphURI);
						queryBuffer.append(">\n");
					}
				}
				queryBuffer.append("WHERE{\n");
				queryBuffer.append("   ?i <" + propURI + "> ?o. \n");
				queryBuffer.append("}");
				queryString = queryBuffer.toString();
				// System.out.println(queryString);
				query = QueryFactory.create(queryString);

				qexec = null;
				results = null;
				try {
					// long start = System.currentTimeMillis();
					qexec = QueryExecutionFactory.sparqlService(endpointURI, query);
					endpointAccessCount++;
					interval();
					results = qexec.execSelect();
					// long end = System.currentTimeMillis();
					// System.out.println("EXEC TIME: " + (end - start));
					System.out.print("n");
				} catch (Exception ex) {
					System.out.println(queryString);
					ex.printStackTrace();
					throw ex;
				}
				if (results.hasNext()) {
					QuerySolution sol = results.next();
					Literal lit = sol.getLiteral("numDomIns");
					if (lit != null) {
						numDomIns = lit.getInt();
					}
					lit = sol.getLiteral("numRanIns");
					if (lit != null) {
						numRanIns = lit.getInt();
					}
					lit = sol.getLiteral("numTriples");
					if (lit != null) {
						numTriples = lit.getInt();
					}
					// System.out.println("NumTriples: " + numTriples + ",
					// numDomIns: " + numDomIns + ", numRanIns: " + numRanIns);

				}
				qexec.close();

				int numTriplesWithBothClass = 0;
				int numDomInsWithClass = 0;
				int numTriplesWithDomClass = 0;
				int numRanInsWithClass = 0;
				int numTriplesWithRanClass = 0;

				// if (domClassSet.size() == 0 || (ranClassSet.size() == 0 &&
				// literalDatatypeSet.size() == 0) ) {

				// �ｿｽ�ｽｽN�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽX�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｾゅｑ�ｽｿ�ｽｽ�ｿｽ�ｽｽ�ｾ�繧托ｽｿ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽC�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽX�ｿｽ�ｽｽ^�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽX�ｿｽ�ｽｽ�ｾ梧腸�ｽｿ�ｽｽ�ｿｽ�ｽｽ﨟冗郷�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽ
				// QUERY
				// ---------------------------------------------------------------------------------
				// ---------------------------------------------------------------------------------------
				queryBuffer = new StringBuffer();
				queryBuffer.append("PREFIX owl: <" + URICollection.PREFIX_OWL + ">\n");
				queryBuffer.append("PREFIX rdfs: <" + URICollection.PREFIX_RDFS + ">\n");
				queryBuffer.append("PREFIX rdf: <" + URICollection.PREFIX_RDF + ">\n");
				queryBuffer.append("SELECT (count(DISTINCT ?i) AS ?numDomIns) (count(?i) AS ?numTriplesWithDom) \n");
				if (graphURIs != null && graphURIs.length != 0) {
					for (String graphURI : graphURIs) {
						queryBuffer.append("FROM <");
						queryBuffer.append(graphURI);
						queryBuffer.append(">\n");
					}
				}
				queryBuffer.append("WHERE {\n SELECT DISTINCT ?i ?o \n");
				queryBuffer.append("WHERE{\n");
				queryBuffer.append("   ?i <" + propURI + "> ?o. \n");
				queryBuffer.append("   ?i rdf:type ?d. \n");
				queryBuffer.append("}\n}");
				queryString = queryBuffer.toString();
				// System.out.println(queryString);

				qexec = null;
				results = null;
				try {
					query = QueryFactory.create(queryString);
					// long start = System.currentTimeMillis();
					qexec = QueryExecutionFactory.sparqlService(endpointURI, query);
					endpointAccessCount++;
					interval();
					results = qexec.execSelect();
					// long end = System.currentTimeMillis();
					// System.out.println("EXEC TIME: " + (end - start));
					System.out.print("t");
				} catch (Exception ex) {
					System.out.println(queryString);
					ex.printStackTrace();
					throw ex;
				}
				if (results.hasNext()) {
					QuerySolution sol = results.next();
					Literal lit = sol.getLiteral("numDomIns");
					if (lit != null) {
						numDomInsWithClass = lit.getInt();
					}
					lit = sol.getLiteral("numTriplesWithDom");
					if (lit != null) {
						numTriplesWithDomClass = lit.getInt();
					}
				}
				qexec.close();

				// range
				// QUERY
				// ---------------------------------------------------------------------------------
				// ---------------------------------------------------------------------------------------
				queryBuffer = new StringBuffer();
				queryBuffer.append("PREFIX owl: <" + URICollection.PREFIX_OWL + ">\n");
				queryBuffer.append("PREFIX rdfs: <" + URICollection.PREFIX_RDFS + ">\n");
				queryBuffer.append("PREFIX rdf: <" + URICollection.PREFIX_RDF + ">\n");
				queryBuffer.append("SELECT (count(DISTINCT ?o) AS ?numRanIns) (count(?o) AS ?numTriplesWithRan) \n");

				if (graphURIs != null && graphURIs.length != 0) {
					for (String graphURI : graphURIs) {
						queryBuffer.append("FROM <");
						queryBuffer.append(graphURI);
						queryBuffer.append(">\n");
					}
				}
				queryBuffer.append("WHERE {\n SELECT DISTINCT ?i ?o \n");
				queryBuffer.append("WHERE{\n");
				queryBuffer.append("   ?i <" + propURI + "> ?o. \n");
				queryBuffer.append("   ?o rdf:type ?r. \n");
				// queryBuffer.append("UNION{ FILTER(isLiteral(?o)) }\n");
				queryBuffer.append("}\n }");
				queryString = queryBuffer.toString();
				// System.out.println(queryString);
				query = QueryFactory.create(queryString);

				qexec = null;
				results = null;
				try {
					// long start = System.currentTimeMillis();
					qexec = QueryExecutionFactory.sparqlService(endpointURI, query);
					endpointAccessCount++;
					interval();
					results = qexec.execSelect();
					// long end = System.currentTimeMillis();
					// System.out.println("EXEC TIME: " + (end - start));
					System.out.print("r");
				} catch (Exception ex) {
					System.out.println(queryString);
					ex.printStackTrace();
					throw ex;
				}
				if (results.hasNext()) {
					QuerySolution sol = results.next();
					Literal lit = sol.getLiteral("numRanIns");
					if (lit != null) {
						numRanInsWithClass = lit.getInt();
					}
					lit = sol.getLiteral("numTriplesWithRan");
					if (lit != null) {
						numTriplesWithRanClass = lit.getInt();
					}

				}
				qexec.close();

				// literal
				// QUERY
				// ---------------------------------------------------------------------------------
				// ---------------------------------------------------------------------------------------
				queryBuffer = new StringBuffer();
				queryBuffer.append("PREFIX owl: <" + URICollection.PREFIX_OWL + ">\n");
				queryBuffer.append("PREFIX rdfs: <" + URICollection.PREFIX_RDFS + ">\n");
				queryBuffer.append("PREFIX rdf: <" + URICollection.PREFIX_RDF + ">\n");
				queryBuffer.append("SELECT (count(DISTINCT ?o) AS ?numRanIns) (count(?o) AS ?numTriplesWithRan) \n");

				if (graphURIs != null && graphURIs.length != 0) {
					for (String graphURI : graphURIs) {
						queryBuffer.append("FROM <");
						queryBuffer.append(graphURI);
						queryBuffer.append(">\n");
					}
				}
				queryBuffer.append("WHERE{\n SELECT DISTINCT ?i ?o \n");
				queryBuffer.append("WHERE{\n");
				queryBuffer.append("   ?i <" + propURI + "> ?o. \n");
				queryBuffer.append(" FILTER(isLiteral(?o)) \n");
				queryBuffer.append("}\n }");
				queryString = queryBuffer.toString();
				// System.out.println(queryString);
				query = QueryFactory.create(queryString);

				qexec = null;
				results = null;
				try {
					// long start = System.currentTimeMillis();
					qexec = QueryExecutionFactory.sparqlService(endpointURI, query);
					endpointAccessCount++;
					interval();
					results = qexec.execSelect();
					// long end = System.currentTimeMillis();
					// System.out.println("EXEC TIME: " + (end - start));
					System.out.print("l");
				} catch (Exception ex) {
					System.out.println(queryString);
					ex.printStackTrace();
					throw ex;
				}
				if (results.hasNext()) {
					QuerySolution sol = results.next();
					Literal lit = sol.getLiteral("numRanIns");
					if (lit != null) {
						numRanInsWithClass += lit.getInt();
					}
					lit = sol.getLiteral("numTriplesWithRan");
					if (lit != null) {
						numTriplesWithRanClass += lit.getInt();
					}

				}
				qexec.close();

				// // literal
				// queryBuffer = new StringBuffer();
				// queryBuffer
				// .append("PREFIX owl: <http://www.w3.org/2002/07/owl#>\n");
				// queryBuffer
				// .append("PREFIX rdfs:
				// <http://www.w3.org/2000/01/rdf-schema#>\n");
				// queryBuffer
				// .append("PREFIX rdf:
				// <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n");
				// queryBuffer
				// .append("SELECT (count(DISTINCT ?o) AS ?numRanIns)\n");
				// if (graphURIs != null && graphURIs.length != 0) {
				// for (String graphURI : graphURIs) {
				// queryBuffer.append("FROM <");
				// queryBuffer.append(graphURI);
				// queryBuffer.append(">\n");
				// }
				// }
				// queryBuffer.append("WHERE{\n");
				// queryBuffer.append(" ?i <" + propURI + "> ?o. \n");
				// queryBuffer.append("FILTER( datatype(?o) = ?c) \n");
				// queryBuffer.append("}");
				// queryString = queryBuffer.toString();
				// // System.out.println(queryString);
				// query = QueryFactory.create(queryString);
				//
				// qexec = null;
				// results = null;
				// try {
				// // long start = System.currentTimeMillis();
				// qexec = QueryExecutionFactory.sparqlService(endpointURI,
				// query);
				// results = qexec.execSelect();
				// // long end = System.currentTimeMillis();
				// // System.out.println("EXEC TIME: " + (end - start));
				// System.out.print(".");
				// } catch (Exception ex) {
				// System.out.println(queryString);
				// ex.printStackTrace();
				// throw ex;
				// }
				// for (; results.hasNext();) {
				// QuerySolution sol = results.next();
				// Literal lit = sol.getLiteral("numRanIns");
				// if (lit != null) {
				// numRanInsWithClass += lit.getInt();
				// }
				// }
				// qexec.close();

				// domain,
				// range闕ｳ�ｽ｡隴�ｽｹ陞ｳ�ｽ｣髫ｪ�ｿｽ�ｼ�郢ｧ蠕娯�ｻ邵ｺ�ｿｽ�ｽ狗ｹ晏現ﾎ懃ｹ晏干ﾎ晁ｬｨ�ｽｰ郢ｧ蜻育�夂ｸｺ蛹ｻ�ｽ�
				// QUERY
				// ---------------------------------------------------------------------------------
				queryBuffer = new StringBuffer();
				queryBuffer.append("PREFIX owl: <" + URICollection.PREFIX_OWL + ">\n");
				queryBuffer.append("PREFIX rdfs: <" + URICollection.PREFIX_RDFS + ">\n");
				queryBuffer.append("PREFIX rdf: <" + URICollection.PREFIX_RDF + ">\n");
				queryBuffer.append("SELECT (count(?i) AS ?numTriples)\n");
				if (graphURIs != null && graphURIs.length != 0) {
					for (String graphURI : graphURIs) {
						queryBuffer.append("FROM <");
						queryBuffer.append(graphURI);
						queryBuffer.append(">\n");
					}
				}
				queryBuffer.append("WHERE {\n SELECT DISTINCT ?i ?o\n");
				queryBuffer.append("WHERE{\n");
				queryBuffer.append("   ?i <" + propURI + "> ?o. \n");
				// if (domClassSet.size() == 0) {
				queryBuffer.append("   ?i rdf:type ?d. \n");
				// }
				// if (ranClassSet.size() == 0) {
				queryBuffer.append("   ?o rdf:type ?r. \n");
				// queryBuffer.append("FILTER( isLiteral(?o) ) \n");
				// }

				queryBuffer.append("} \n }");
				queryString = queryBuffer.toString();
				// System.out.println(queryString);
				query = QueryFactory.create(queryString);

				qexec = null;
				results = null;
				try {
					// long start = System.currentTimeMillis();
					qexec = QueryExecutionFactory.sparqlService(endpointURI, query);
					endpointAccessCount++;
					interval();
					results = qexec.execSelect();
					// long end = System.currentTimeMillis();
					// System.out.println("EXEC TIME: " + (end - start));
					System.out.print("m");
				} catch (Exception ex) {
					System.out.println(queryString);
					ex.printStackTrace();
					throw ex;
				}
				for (; results.hasNext();) {
					QuerySolution sol = results.next();
					Literal lit = sol.getLiteral("numTriples");
					if (lit != null) {
						numTriplesWithBothClass = lit.getInt();
					}
				}
				qexec.close();

				// QUERY
				// ---------------------------------------------------------------------------------
				queryBuffer = new StringBuffer();
				queryBuffer.append("PREFIX owl: <" + URICollection.PREFIX_OWL + ">\n");
				queryBuffer.append("PREFIX rdfs: <" + URICollection.PREFIX_RDFS + ">\n");
				queryBuffer.append("PREFIX rdf: <" + URICollection.PREFIX_RDF + ">\n");
				queryBuffer.append("SELECT (count(?i) AS ?numTriples) \n");
				if (graphURIs != null && graphURIs.length != 0) {
					for (String graphURI : graphURIs) {
						queryBuffer.append("FROM <");
						queryBuffer.append(graphURI);
						queryBuffer.append(">\n");
					}
				}
				queryBuffer.append("WHERE {\n  SELECT DISTINCT ?i ?o\n");
				queryBuffer.append("WHERE{\n");
				queryBuffer.append("   ?i <" + propURI + "> ?o. \n");
				// if (domClassSet.size() == 0) {
				queryBuffer.append("   ?i rdf:type ?d. \n");
				// }
				// if (ranClassSet.size() == 0) {
				// queryBuffer.append(" ?o rdf:type ?r. \n");
				queryBuffer.append("FILTER( isLiteral(?o) ) \n");
				// }

				queryBuffer.append("} \n }");
				queryString = queryBuffer.toString();
				// System.out.println(queryString);
				query = QueryFactory.create(queryString);

				qexec = null;
				results = null;
				try {
					// long start = System.currentTimeMillis();
					qexec = QueryExecutionFactory.sparqlService(endpointURI, query);
					endpointAccessCount++;
					interval();
					results = qexec.execSelect();
					// long end = System.currentTimeMillis();
					// System.out.println("EXEC TIME: " + (end - start));
					System.out.print("d");
				} catch (Exception ex) {
					System.out.println(queryString);
					ex.printStackTrace();
					throw ex;
				}
				for (; results.hasNext();) {
					QuerySolution sol = results.next();
					Literal lit = sol.getLiteral("numTriples");
					if (lit != null) {
						numTriplesWithBothClass += lit.getInt();
					}
				}
				qexec.close();

				// } // end of IF

				// int numTriplesWithClass =
				// (numTriplesWithDomClass+numTriplesWithRanClass) -
				// numTriplesWithBothClass;

				// System.out.println("\nnumTriplesWithClass:
				// "+numTriplesWithClass);
				System.out.print("\nnumTriplesWithDomClass: " + numTriplesWithDomClass);
				System.out.print("   numTriplesWithRanClass: " + numTriplesWithRanClass);
				System.out.println("   numTriplesWithBothClass: " + numTriplesWithBothClass);
				System.out.println("   numTriples: " + numTriples);

				// label
				ArrayList<String> labelList = new ArrayList<String>();
				// QUERY
				// ---------------------------------------------------------------------------------
				queryBuffer = new StringBuffer();
				queryBuffer.append("PREFIX owl: <" + URICollection.PREFIX_OWL + ">\n");
				queryBuffer.append("PREFIX rdfs: <" + URICollection.PREFIX_RDFS + ">\n");
				queryBuffer.append("PREFIX rdf: <" + URICollection.PREFIX_RDF + ">\n");
				queryBuffer.append("SELECT ?label\n");
				if (graphURIs != null && graphURIs.length != 0) {
					for (String graphURI : graphURIs) {
						queryBuffer.append("FROM <");
						queryBuffer.append(graphURI);
						queryBuffer.append(">\n");
					}
				}
				queryBuffer.append("WHERE{\n");
				queryBuffer.append("  <" + propURI + "> rdfs:label ?label. \n");
				queryBuffer.append("}");
				queryString = queryBuffer.toString();
				// System.out.println(queryString);
				query = QueryFactory.create(queryString);

				qexec = null;
				results = null;
				try {
					// long start = System.currentTimeMillis();
					qexec = QueryExecutionFactory.sparqlService(endpointURI, query);
					endpointAccessCount++;
					interval();
					results = qexec.execSelect();
					// long end = System.currentTimeMillis();
					// System.out.println("EXEC TIME: " + (end - start));
					// System.out.print(".");
					System.out.print("L");
				} catch (Exception ex) {
					System.out.println(queryString);
					ex.printStackTrace();
					throw ex;
				}
				for (; results.hasNext();) {
					QuerySolution sol = results.next();
					Literal lit = sol.getLiteral("label");
					if (lit != null) {
						String label = lit.getString();
						labelList.add(label);
						// pro.addLiteral(labelPro, label);
					}
				}
				qexec.close();

				// analysis
				int propCategory = 0;
				// domain
				if (numTriples == 0) {
					propCategory = 4;
				} else {
					if (domClassSet.size() == 0) {
						// dom is not defined.

						if (numDomIns - numDomInsWithClass == 0) {
							// �ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｾ励※縺ｮ繧､�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽX�ｿｽ�ｽｽ^�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽX�ｿｽ�ｽｽ�ｿｽ�ｽｽclass�ｿｽ�ｽｽ鬪ｭ�ｽｾ�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｾ�繧托ｽｿ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｾ�繧托ｽｿ�ｽｽ
							// GUI�ｿｽ�ｽｽ�ｾ�繧ｯ�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽX�ｿｽ�ｽｽI�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｾり�ｽ(�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽ_�ｿｽ�ｽｽ�ｾ�謨托ｿｽ�ｽｽ�ｿｽ�ｽｽ)
							propCategory = 2;
						} else {
							if (numDomInsWithClass == 0) {
								// class�ｿｽ�ｽｽ鬪ｭ�ｽｾ�ｿｽ�ｽｽ�ｾ後ｑ�ｽｿ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽC�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽX�ｿｽ�ｽｽ^�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽX�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｾ医ｑ�ｽｿ�ｽｽ�ｿｽ�ｽｽ�ｾ�繧托ｽｿ�ｽｽ
								// �ｿｽ�ｽｽ�ｾ�縺ｮ繧､�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽX�ｿｽ�ｽｽ^�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽX�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽN�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽX�ｿｽ�ｽｽ鬪ｭ�ｽｾ�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｾ�繧托ｽｿ�ｽｽ�ｿｽ�ｽｽ�ｾ医ｑ�ｽｿ�ｽｽ�ｿｽ�ｽｽ�ｾ�繧托ｽｿ�ｽｽ
								// Junk�ｿｽ�ｽｽv�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽp�ｿｽ�ｽｽe�ｿｽ�ｽｽB(�ｿｽ�ｽｽ~�ｿｽ�ｽｽ�ｾ上〒繧托ｽｿ�ｽｽ�ｿｽ�ｽｽ�ｾ医ｑ�ｽｿ�ｽｽ)
								propCategory = 4;
								numDomIns = 0;
							} else {
								// �ｿｽ�ｽｽ鮨ｦ雋ｻ�ｽｿ�ｽｽ~�ｿｽ�ｽｽ�ｿｽ�ｽｽ
								propCategory = 3;
								numDomIns = numDomInsWithClass;
							}
						}
					} else {
						if (numDomIns - numDomInsWithClass == 0) {
							// okay
							propCategory = 1;
						} else {
							propCategory = 2;
						}
					}
				}

				// System.out.println("\nDomPropCat: " + propCategory);

				// range
				if (numTriples == 0) {
					propCategory = 4;
				} else {
					if (ranClassSet.size() == 0 && literalDatatypeSet.size() == 0) {
						// ran is not defined.
						if (numRanIns - numRanInsWithClass == 0) {
							// �ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｾ励※縺ｮ繧､�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽX�ｿｽ�ｽｽ^�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽX�ｿｽ�ｽｽ�ｿｽ�ｽｽclass�ｿｽ�ｽｽ鬪ｭ�ｽｾ�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｾ�繧托ｽｿ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｾ�繧托ｽｿ�ｽｽ
							// GUI�ｿｽ�ｽｽ�ｾ�繧ｯ�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽX�ｿｽ�ｽｽI�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｾり�ｽ(�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽ_�ｿｽ�ｽｽ�ｾ�謨托ｿｽ�ｽｽ�ｿｽ�ｽｽ)
							if (propCategory == 1) {
								propCategory = 2;
							}
						} else {
							if (numRanInsWithClass == 0) {
								// class�ｿｽ�ｽｽ鬪ｭ�ｽｾ�ｿｽ�ｽｽ�ｾ後ｑ�ｽｿ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽC�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽX�ｿｽ�ｽｽ^�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽX�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｾ医ｑ�ｽｿ�ｽｽ�ｿｽ�ｽｽ�ｾ�繧托ｽｿ�ｽｽ
								// �ｿｽ�ｽｽ�ｾ�縺ｮ繧､�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽX�ｿｽ�ｽｽ^�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽX�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽN�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽX�ｿｽ�ｽｽ鬪ｭ�ｽｾ�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｾ�繧托ｽｿ�ｽｽ�ｿｽ�ｽｽ�ｾ医ｑ�ｽｿ�ｽｽ�ｿｽ�ｽｽ�ｾ�繧托ｽｿ�ｽｽ
								// Junk�ｿｽ�ｽｽv�ｿｽ�ｽｽ�ｿｽ�ｽｽ�ｿｽ�ｽｽp�ｿｽ�ｽｽe�ｿｽ�ｽｽB(�ｿｽ�ｽｽ~�ｿｽ�ｽｽ�ｾ上〒繧托ｽｿ�ｽｽ�ｿｽ�ｽｽ�ｾ医ｑ�ｽｿ�ｽｽ)
								propCategory = 4;
								numRanIns = 0;
							} else {
								// �ｿｽ�ｽｽ鮨ｦ雋ｻ�ｽｿ�ｽｽ~�ｿｽ�ｽｽ�ｿｽ�ｽｽ
								propCategory = Math.max(propCategory, 3);
								numRanIns = numRanInsWithClass;
							}
						}
					} else {
						if (ranClassSet.size() == 0 && literalDatatypeSet.size() != 0) {
							// ran is not defined.
							if (numRanIns - numRanInsWithClass == 0) {
								// nothing to do
							} else {
								if (numRanInsWithClass == 0) {
									propCategory = 4;
									numRanIns = 0;
								} else {
									// �ｿｽ�ｽｽ鮨ｦ雋ｻ�ｽｿ�ｽｽ~�ｿｽ�ｽｽ�ｿｽ�ｽｽ
									propCategory = Math.max(propCategory, 3);
									numRanIns = numRanInsWithClass;
								}
							}
						} else {
							if (numRanIns - numRanInsWithClass == 0) {
								// okay
								// nothing to do
							} else {
								propCategory = Math.max(propCategory, 2);
							}
						}
					}
				}

				// System.out.println("\nDomRanPropCat: " + propCategory);

				int numTriplesWithClass = 0;
				if (domClassSet.size() == 0 && (ranClassSet.size() == 0 && literalDatatypeSet.size() == 0)) {
					numTriplesWithClass = numTriplesWithBothClass;
				} else {
					if (domClassSet.size() == 0 && (ranClassSet.size() != 0 || literalDatatypeSet.size() != 0)) {
						// range邵ｺ�ｽｯ陞ｳ螟ゑｽｾ�ｽｩ邵ｺ霈費ｽ檎ｸｺ�ｽｦ邵ｺ�ｿｽ�ｽ狗ｸｺ�ｽｮ邵ｺ�ｽｧ邵ｲ窶･omain邵ｺ�ｽｮ陋ｻ�ｽｶ驍擾ｿｽ�ｿｽ邵ｺ荵敖ｰ邵ｺ�ｽ｣邵ｺ貅假ｽらｸｺ�ｽｮ邵ｺ�ｿｽ�ｿ�郢ｧ�ｽｫ郢ｧ�ｽｦ郢晢ｽｳ郢晢ｿｽ
						numTriplesWithClass = numTriplesWithDomClass;
					} else {
						if (domClassSet.size() != 0 && (ranClassSet.size() == 0 && literalDatatypeSet.size() == 0)) {
							numTriplesWithClass = numTriplesWithRanClass;
						} else {
							numTriplesWithClass = numTriples;
						}
					}
				}

				if (wholePropertyCategory == 0) {
					wholePropertyCategory = propCategory;
				} else {
					if (propCategory == 1) {
						if (wholePropertyCategory == 4) {
							wholePropertyCategory = 3;
						}
					} else {
						if (propCategory == 2) {
							if (wholePropertyCategory == 1) {
								wholePropertyCategory = 2;
							} else {
								if (wholePropertyCategory == 4) {
									wholePropertyCategory = 3;
								}
							}
						} else {
							if (wholePropertyCategory == 1 || wholePropertyCategory == 2) {
								wholePropertyCategory = 3;
							}
						}
					}
				}

				countProperties[propCategory - 1]++;
				countAllTriples[propCategory - 1] += numTriples;
				countClassTriples[propCategory - 1] += numTriplesWithClass;

				// if (propCategory != 4) {

				// write to model
				Resource propPartRes = model.createResource(AnonId.create());
				datasetRes.addProperty(propertyPartitionPro, propPartRes);
				// property
				Property pro = model.createProperty(propURI);
				// labels
				for (String label : labelList) {
					pro.addLiteral(labelPro, label);
				}
				propPartRes.addProperty(propertyPro, pro);
				propPartRes.addLiteral(triplesPro, numTriples);

				System.out.println("\n PDRD classRelations(L2033): " + propDomRanDeclList2.size());

				// class relations
				for (PropertyDomainRangeDecl pdrd : propDomRanDeclList2) {
					Resource classRelation = model.createResource(AnonId.create());
					classRelation.addProperty(typePro, classRelationCls);
					propPartRes.addProperty(classRelationPro, classRelation);

					// 闕ｳ�ｽｻ髫ｱ讒ｭ縺醍ｹ晢ｽｩ郢ｧ�ｽｹ
					classRelation.addProperty(subjectClsPro, model.createResource(pdrd.getDomainClass()));

					classURISet.add(pdrd.getDomainClass());

					// 騾ｶ�ｽｮ騾ｧ�ｿｽ�ｽｪ讒ｭ縺醍ｹ晢ｽｩ郢ｧ�ｽｹ/郢晢ｿｽ�ｿｽ郢ｧ�ｽｿ郢ｧ�ｽｻ郢晢ｿｽ繝ｨ
					if (pdrd.getRangeClass() != null) {
						classRelation.addProperty(objectClsPro, model.createResource(pdrd.getRangeClass()));
						classURISet.add(pdrd.getRangeClass());
					} else {
						if (pdrd.getLiteralDataType() != null) {
							classRelation.addProperty(objectDatatypePro,
									model.createResource(pdrd.getLiteralDataType()));
						}
					}

					// classRelation.addProperty(propPro,
					// model.createProperty(pdrd.getProperty()));

					// 陷茨ｽｨ郢晏現ﾎ懃ｹ晏干ﾎ晁ｬｨ�ｽｰ
					classRelation.addLiteral(triplesPro, pdrd.getNumTriples());
					// 闕ｳ�ｽｻ髫ｱ讒ｭ縺�郢晢ｽｳ郢ｧ�ｽｹ郢ｧ�ｽｿ郢晢ｽｳ郢ｧ�ｽｹ隰ｨ�ｽｰ
					classRelation.addLiteral(distinctSubjectsPro, pdrd.getNumDomainInstances());
					// 騾ｶ�ｽｮ騾ｧ�ｿｽ�ｽｪ讒ｭ縺�郢晢ｽｳ郢ｧ�ｽｹ郢ｧ�ｽｿ郢晢ｽｳ郢ｧ�ｽｹ隰ｨ�ｽｰ
					classRelation.addLiteral(distinctObjectsPro, pdrd.getNumRangeInstances());

					// TODO 雎ｸ蛹ｻ�ｼ�邵ｺ�ｽｦ郢ｧ蛹ｻ�ｼ樒ｸｺ迢暦ｽ｢�ｽｺ髫ｱ�ｿｽ
					// classRelation.addLiteral(isDomClsLimPro,
					// pdrd.isDomainClassLimitedQ());
					// classRelation.addLiteral(isRanClsLimPro,
					// pdrd.isRangeClassLimitedQ());
				}

				// Resource propProfile = model.createResource(AnonId.create());
				// propProfile.addProperty(typePro, propProfileCls);

				propPartRes.addProperty(propPro, pro);
				propPartRes.addLiteral(propCatPro, propCategory);
				pro.addProperty(typePro, propRes);

				for (String domURI : domClassSet) {
					Resource dom = model.createResource(domURI);
					dom.addProperty(typePro, clsCls);
					pro.addProperty(domainPro, dom);
					classURISet.add(domURI);
				}

				for (String ranURI : ranClassSet) {
					Resource ran = model.createResource(ranURI);
					ran.addProperty(typePro, clsCls);
					pro.addProperty(rangePro, ran);
					classURISet.add(ranURI);
				}

				// TODO literal
				// 郢晢ｿｽ�ｿｽ郢ｧ�ｽｿ郢ｧ�ｽｿ郢ｧ�ｽ､郢晏干�ｽ池ange邵ｺ�ｽｨ邵ｺ蜉ｱ窶ｻ闕ｳ蠑ｱ竏ｴ邵ｺ�ｽｦ郢ｧ蛹ｻ�ｼ樒ｸｺ蜈ｷ�ｽｼ�ｿｽ
				for (String datatypeURI : literalDatatypeSet) {
					Resource ran = model.createResource(datatypeURI);
					ran.addProperty(typePro, clsCls);
					pro.addProperty(rangePro, ran);
				}

				propPartRes.addLiteral(classesPro, classURISet.size());
				// propPartRes.addLiteral(datatypesPro, datatypeSet.size());

				propPartRes.addLiteral(subjectClassesPro, domClassSet.size());
				propPartRes.addLiteral(objectClassesPro, ranClassSet.size());
				propPartRes.addLiteral(objectDatatypesPro, literalDatatypeSet.size());

				propPartRes.addLiteral(distinctSubjectsPro, numDomIns);
				propPartRes.addLiteral(distinctObjectsPro, numRanIns);
				propPartRes.addLiteral(triplesPro, numTriples);

				totalNumTriples += numTriples;

				// } //end if cat

				System.out.println("\n" + propURI + " (" + propCategory + ")(" + numTriplesWithClass + " / "
						+ numTriples + ", " + numDomIns + ", " + numRanIns + ")");
			} catch (Exception ex) {
				System.out.println("\nERROR: propURI " + propURI);
				ex.printStackTrace();
			}

		} // end of for (propURI)

		Property subsetProp = model.createProperty(URICollection.PROPERTY_SB_PROPERTY_CATEGORY_SUBSET);
		Property propertyCategoryPro = model.createProperty(URICollection.PROPERTY_SB_PROPERTY_CATEGORY);
		Property propertiesPro = model.createProperty(URICollection.PROPERTY_VOID_PROPERTIES);

		int proLen = 0;
		if (countProperties != null) {
			int cnt = 1;
			for (int c : countProperties) {
				Resource sbRes = model.createResource(AnonId.create());
				datasetRes.addProperty(subsetProp, sbRes);
				sbRes.addLiteral(triplesPro, countAllTriples[cnt - 1]);
				sbRes.addLiteral(searchableTriplesPro, countClassTriples[cnt - 1]);
				sbRes.addLiteral(propertyCategoryPro, cnt);
				sbRes.addLiteral(propertiesPro, c);

				System.out.println("property[" + cnt + "]: " + c + ": " + countClassTriples[cnt - 1] + " / "
						+ countAllTriples[cnt - 1]);
				proLen += c;
				cnt++;
			}
		}

		System.out.println("#Datatypes: " + datatypeSet.size());
		System.out.println("#Classes: " + classURISet.size());
		System.out.println("#Triples: " + totalNumTriples);

		datasetRes.addLiteral(triplesPro, totalNumTriples);
		datasetRes.addLiteral(classesPro, classURISet.size());
		datasetRes.addLiteral(propertiesPro, proLen);
		datasetRes.addLiteral(datatypesPro, datatypeSet.size());

		Resource datasetClsRes = model.createResource(URICollection.RESOURCE_RDFS_DATATYPE);
		for (String datatype : datatypeSet) {
			Resource datatypeRes = model.createResource(datatype);
			datatypeRes.addProperty(typePro, datasetClsRes);
		}

		return new Schema(model, datasetRes, wholePropertyCategory, countProperties, 0, 0, totalNumTriples, classURISet,
				datatypeSet);
	}

	class PropertyDomainRangeDecl {

		String property = null;
		String domainClass = null;
		String rangeClass = null;
		String literalDataType = null;
		int numTriples = 0;
		int numDomainInstances = 0;
		int numRangeInstances = 0;
		boolean domainClassLimitedQ = false;
		boolean rangeClassLimitedQ = false;

		public PropertyDomainRangeDecl(String property, String domainClass, String rangeClass, String literalDataType,
				int numTriples, int numDomainInstances, int numRangeInstances, boolean domainClassLimitedQ,
				boolean rangeClassLimitedQ) {
			this.property = property;
			this.domainClass = domainClass;
			this.rangeClass = rangeClass;
			this.literalDataType = literalDataType;
			this.numTriples = numTriples;
			this.numDomainInstances = numDomainInstances;
			this.numRangeInstances = numRangeInstances;
			this.domainClassLimitedQ = domainClassLimitedQ;
			this.rangeClassLimitedQ = rangeClassLimitedQ;
		}

		public boolean equals(Object obj) {
			if (!(obj instanceof PropertyDomainRangeDecl)) {
				return false;
			}
			PropertyDomainRangeDecl pdrd = (PropertyDomainRangeDecl) obj;
			if (property == null) {
				if (pdrd.getProperty() != null) {
					return false;
				}
			} else {
				if (!property.equals(pdrd.getProperty())) {
					return false;
				}
			}
			if (domainClass == null) {
				if (pdrd.getDomainClass() != null) {
					return false;
				}
			} else {
				if (!domainClass.equals(pdrd.getDomainClass())) {
					return false;
				}
			}
			if (rangeClass == null) {
				if (pdrd.getRangeClass() != null) {
					return false;
				}
			} else {
				if (!rangeClass.equals(pdrd.getRangeClass())) {
					return false;
				}
			}
			if (literalDataType == null) {
				if (pdrd.getLiteralDataType() != null) {
					return false;
				}
			} else {
				if (!literalDataType.equals(pdrd.getLiteralDataType())) {
					return false;
				}
			}

			return true;
		}

		public int hashCode() {
			return super.hashCode();
		}

		public String getProperty() {
			return property;
		}

		public String getDomainClass() {
			return domainClass;
		}

		public String getRangeClass() {
			return rangeClass;
		}

		public String getLiteralDataType() {
			return literalDataType;
		}

		public final int getNumTriples() {
			return numTriples;
		}

		public final int getNumDomainInstances() {
			return numDomainInstances;
		}

		public final int getNumRangeInstances() {
			return numRangeInstances;
		}

		public final boolean isDomainClassLimitedQ() {
			return domainClassLimitedQ;
		}

		public final boolean isRangeClassLimitedQ() {
			return rangeClassLimitedQ;
		}

	}

	public RDFsCrawlerImpl(String endpointURI) {
		this.endpointURI = endpointURI;
	}

	public RDFsCrawlerImpl(String endpointURI, String crawlName, String outDirName) throws Exception {
		this.endpointURI = endpointURI;
		this.crawlName = crawlName;
		this.outDir = new File(outDirName);
		if (!this.outDir.exists()) {
			this.outDir.mkdirs();
		} else {
			if (this.outDir.isFile()) {
				throw new Exception("Output File exists and new output directory cannot be created: "
						+ this.outDir.getCanonicalPath());
			}
		}
	}

	public RDFsCrawlerImpl(String endpointURI, String[] graphURIs) {
		this.endpointURI = endpointURI;
		this.graphURIs = graphURIs;
	}

	public Model getPropertiesFromInstanceDecls() throws Exception {

		// QUERY
		// ---------------------------------------------------------------------------------
		// obtains all triples [?p,?d,?r] where classes of subject and object in
		// triple <?i,?p,?o>
		// are declared by <?i,rdf:type,?d> and <?j,rdf:type,?r>.
		// ---------------------------------------------------------------------------------------
		StringBuffer queryBuffer = new StringBuffer();
		queryBuffer.append("PREFIX owl: <" + URICollection.PREFIX_OWL + ">\n");
		queryBuffer.append("PREFIX rdfs: <" + URICollection.PREFIX_RDFS + ">\n");
		queryBuffer.append("PREFIX rdf: <" + URICollection.PREFIX_RDF + ">\n");
		queryBuffer.append("SELECT DISTINCT ?p ?d ?r \n");
		if (graphURIs != null && graphURIs.length != 0) {
			for (String graphURI : graphURIs) {
				queryBuffer.append("FROM <");
				queryBuffer.append(graphURI);
				queryBuffer.append(">\n");
			}
		}
		queryBuffer.append("WHERE{\n");
		queryBuffer.append("  ?i rdf:type ?d.\n");
		queryBuffer.append("  ?j rdf:type ?r.\n");
		queryBuffer.append("  ?i ?p ?j.\n");
		// queryBuffer.append(" ?p rdfs:domain ?m.\n");
		// queryBuffer.append(" ?p rdfs:range ?r.\n");
		queryBuffer.append("}");

		String queryString = queryBuffer.toString();

		// System.out.println(queryString);

		Query query = QueryFactory.create(queryString);

		QueryExecution qexec = null;
		ResultSet results = null;
		try {
			// long start = System.currentTimeMillis();
			qexec = QueryExecutionFactory.sparqlService(endpointURI, query);
			endpointAccessCount++;
			interval();
			results = qexec.execSelect();
			// long end = System.currentTimeMillis();
			// System.out.println("EXEC TIME: " + (end - start));
			System.out.print(".");
		} catch (Exception ex) {
			ex.printStackTrace();
			throw ex;
		}

		// create model
		Model model = ModelFactory.createDefaultModel();
		Property domainPro = model.createProperty(URICollection.PROPERTY_RDFS_DOMAIN);
		Property rangePro = model.createProperty(URICollection.PROPERTY_RDFS_RANGE);

		// FILTER LITERAL+alpha
		String[] filterStrs = URICollection.FILTER_PROPERTY;
		for (; results.hasNext();) {
			QuerySolution sol = results.next();
			Resource pro = sol.getResource("p");
			Resource dom = sol.getResource("d");
			Resource ren = sol.getResource("r");
			if (pro != null && uriFilter(pro.getURI(), filterStrs) != null) {
				pro = model.createResource(pro);
				if (dom != null) {
					dom = model.createResource(dom);
					pro.addProperty(domainPro, dom);
				}
				if (ren != null) {
					ren = model.createResource(ren);
					pro.addProperty(rangePro, ren);
				}
			}
		}
		qexec.close();
		return model;
	}

	public Model getProperiesFromDomainRangeDecls() throws Exception {

		// QUERY
		// ---------------------------------------------------------------------------------
		// obtains all pairs of domain and range for each property.
		// ---------------------------------------------------------------------------------------
		StringBuffer queryBuffer = new StringBuffer();
		queryBuffer.append("PREFIX owl: <" + URICollection.PREFIX_OWL + ">\n");
		queryBuffer.append("PREFIX rdfs: <" + URICollection.PREFIX_RDFS + ">\n");
		queryBuffer.append("PREFIX rdf: <" + URICollection.PREFIX_RDF + ">\n");
		queryBuffer.append("SELECT ?p ?d ?r \n");
		if (graphURIs != null && graphURIs.length != 0) {
			for (String graphURI : graphURIs) {
				queryBuffer.append("FROM <");
				queryBuffer.append(graphURI);
				queryBuffer.append(">\n");
			}
		}
		queryBuffer.append("WHERE{\n");
		queryBuffer.append("  ?p rdfs:domain ?d.\n");
		queryBuffer.append("  ?p rdfs:range ?r.\n");
		queryBuffer.append("}");

		String queryString = queryBuffer.toString();

		// System.out.println(queryString);

		Query query = QueryFactory.create(queryString);

		QueryExecution qexec = null;
		ResultSet results = null;
		try {
			// long start = System.currentTimeMillis();
			qexec = QueryExecutionFactory.sparqlService(endpointURI, query);
			endpointAccessCount++;
			interval();
			results = qexec.execSelect();
			// long end = System.currentTimeMillis();
			// System.out.println("EXEC TIME: " + (end - start));
			System.out.print("x");
		} catch (Exception ex) {
			ex.printStackTrace();
			throw ex;
		}

		// create model
		Model model = ModelFactory.createDefaultModel();
		Property domainPro = model.createProperty(URICollection.PROPERTY_RDFS_DOMAIN);
		Property rangePro = model.createProperty(URICollection.PROPERTY_RDFS_RANGE);

		// FILTER LITERAL+alpha
		String[] filterStrs = URICollection.FILTER_PROPERTY;
		for (; results.hasNext();) {
			QuerySolution sol = results.next();
			Resource pro = sol.getResource("p");
			Resource dom = sol.getResource("d");
			Resource ren = sol.getResource("r");
			if (pro != null && uriFilter(pro.getURI(), filterStrs) != null) {
				pro = model.createResource(pro);
				if (dom != null) {
					dom = model.createResource(dom);
					pro.addProperty(domainPro, dom);
				}
				if (ren != null) {
					ren = model.createResource(ren);
					pro.addProperty(rangePro, ren);
				}
			}
		}
		qexec.close();
		return model;
	}

	// heavy
	public String[] getDeclaredRDFsClasses() throws Exception {

		String[] filterStrs = URICollection.FILTER_CLASS;
		String[] unfilterStrs = null;
		// String[] unfilterStrs = URICollection.UNFILTER_CLASS;
		// where
		String[] lines = new String[] { "", "?c rdf:type rdfs:Class.", "[] rdf:type ?c.", "[] rdfs:domain ?c.",
				"[] rdfs:range ?c.", "?c rdfs:subclassOf [].", "[] rdfs:subclassOf ?c." };

		// QUERY
		// ---------------------------------------------------------------------------------
		// obtains all classes by checking triples of <?c,rdf:type,rdfs:Class>,
		// <[],rdf:type,?c>,
		// <[],rdfs:domain,?c>, <[],rdfs:range,?c>, <?c,rdfs:subclassOf,[]> and
		// <[] rdfs:subClassof,?c>.
		// ---------------------------------------------------------------------------------------
		StringBuffer queryBuffer = new StringBuffer();
		for (int i = 1; i < lines.length; i++) {
			if (i != 1) {
				queryBuffer.append(" UNION ");
			}
			queryBuffer.append("{");
			queryBuffer.append(lines[i]);
			queryBuffer.append("}\n");
		}
		lines[0] = queryBuffer.toString();

		HashSet<String> resultClassSet = new HashSet<String>();
		for (int i = 0; i < lines.length; i++) {
			queryBuffer = new StringBuffer();
			queryBuffer.append("PREFIX owl: <" + URICollection.PREFIX_OWL + ">\n");
			queryBuffer.append("PREFIX rdfs: <" + URICollection.PREFIX_RDFS + ">\n");
			queryBuffer.append("PREFIX rdf: <" + URICollection.PREFIX_RDF + ">\n");
			queryBuffer.append("SELECT DISTINCT ?c  \n");
			if (graphURIs != null && graphURIs.length != 0) {
				for (String graphURI : graphURIs) {
					queryBuffer.append("FROM <");
					queryBuffer.append(graphURI);
					queryBuffer.append(">\n");
				}
			}
			queryBuffer.append("WHERE{\n");
			queryBuffer.append(lines[i]);
			queryBuffer.append("}");

			String queryString = queryBuffer.toString();

			// System.out.println(queryString);

			Query query = QueryFactory.create(queryString);

			QueryExecution qexec = null;
			ResultSet results = null;
			try {
				// long start = System.currentTimeMillis();
				qexec = QueryExecutionFactory.sparqlService(endpointURI, query);
				endpointAccessCount++;
				interval();
				results = qexec.execSelect();
				// long end = System.currentTimeMillis();
				// System.out.println("EXEC TIME: " + (end - start));
				System.out.print(".");
				for (; results.hasNext();) {
					QuerySolution sol = results.next();
					Resource res = null;
					try {
						res = sol.getResource("c");
					} catch (Exception ex) {
						// todo
						ex.printStackTrace();
						// just try
						try {
							Literal lit = sol.getLiteral("c");
							System.out.println("Warning: found Literal: " + lit.toString());
						} catch (Exception ex2) {
							// nothing to do
						}
					}
					if (res != null && uriFilter(res.getURI(), filterStrs, unfilterStrs) != null) {
						resultClassSet.add(res.getURI());
					}
				}
				qexec.close();
				if (i == 0) {
					break;
				}
			} catch (Exception ex) {
				qexec.close();
				System.out.println(queryString);
				ex.printStackTrace();
				if (i != 0) {
					throw ex;
				}
			}
		}
		return resultClassSet.toArray(new String[0]);
	}

	public String[] getInferedRDFsClassesFromInstances() throws Exception {
		// QUERY
		// ---------------------------------------------------------------------------------
		// obtains all classes of instances by simply checking triples []
		// rdf:type ?c.
		// ---------------------------------------------------------------------------------------
		StringBuffer queryBuffer = new StringBuffer();
		queryBuffer.append("PREFIX owl: <" + URICollection.PREFIX_OWL + ">\n");
		queryBuffer.append("PREFIX rdfs: <" + URICollection.PREFIX_RDFS + ">\n");
		queryBuffer.append("PREFIX rdf: <" + URICollection.PREFIX_RDF + ">\n");
		queryBuffer.append("SELECT DISTINCT ?c  \n");
		if (graphURIs != null && graphURIs.length != 0) {
			for (String graphURI : graphURIs) {
				queryBuffer.append("FROM <");
				queryBuffer.append(graphURI);
				queryBuffer.append(">\n");
			}
		}
		queryBuffer.append("WHERE{\n  [] rdf:type ?c. \n");
		queryBuffer.append("}");
		String queryString = queryBuffer.toString();

		// System.out.println(queryString);

		Query query = QueryFactory.create(queryString);

		QueryExecution qexec = null;
		ResultSet results = null;
		try {
			// long start = System.currentTimeMillis();
			qexec = QueryExecutionFactory.sparqlService(endpointURI, query);
			endpointAccessCount++;
			interval();
			results = qexec.execSelect();
			// long end = System.currentTimeMillis();
			// System.out.println("EXEC TIME: " + (end - start));
			System.out.print("I");
		} catch (Exception ex) {
			ex.printStackTrace();
			throw ex;
		}

		ArrayList<String> resultList = new ArrayList<String>();
		String[] filterStrs = URICollection.FILTER_CLASS;
		String[] unfilterStrs = URICollection.UNFILTER_CLASS;

		for (; results.hasNext();) {
			QuerySolution sol = results.next();
			Resource res = sol.getResource("c");
			if (res != null && uriFilter(res.getURI(), filterStrs, unfilterStrs) != null) {
				resultList.add(res.getURI());
			}
		}
		qexec.close();
		return resultList.toArray(new String[0]);
	}

	public String[] getRDFProperties() throws Exception {
		return getRDFProperties(graphURIs);
	}

	public String[] getRDFProperties(String graphURI) throws Exception {
		return getRDFProperties(new String[] { graphURI });
	}

	public String[] getRDFProperties(String[] graphURIs) throws Exception {
		String stepName = "getRDFProperties";

		// QUERY
		// ---------------------------------------------------------------------------------
		// obtains all properties written in the given graphs by simply checking
		// triples ?s ?p ?o.
		// ---------------------------------------------------------------------------------------
		StringBuffer queryBuffer = new StringBuffer();
		queryBuffer.append("PREFIX owl: <" + URICollection.PREFIX_OWL + ">\n");
		queryBuffer.append("PREFIX rdfs: <" + URICollection.PREFIX_RDFS + ">\n");
		queryBuffer.append("PREFIX rdf: <" + URICollection.PREFIX_RDF + ">\n");

		queryBuffer.append("SELECT DISTINCT ?p  \n");
		if (graphURIs != null && graphURIs.length != 0) {
			for (String graphURI : graphURIs) {
				queryBuffer.append("FROM <");
				queryBuffer.append(graphURI);
				queryBuffer.append(">\n");
			}
		}
		queryBuffer.append("WHERE{\n");
		queryBuffer.append("{  ?s ?p ?o.}\n");
		// queryBuffer.append("UNION\n");
		// queryBuffer.append("{ ?p rdfs:domain ?d.}\n");
		// queryBuffer.append("UNION\n");
		// queryBuffer.append("{ ?p rdfs:range ?r.}\n");
		queryBuffer.append("}");
		String queryString = queryBuffer.toString();

		// System.out.println(queryString);

		Query query = QueryFactory.create(queryString);

		QueryExecution qexec = null;
		ResultSet results = null;

		int sCount = 3;
		while (sCount > 0) {
			try {
				// long start = System.currentTimeMillis();
				qexec = QueryExecutionFactory.sparqlService(endpointURI, query);
				// qexec.setTimeout(240000L);
				endpointAccessCount++;
				results = qexec.execSelect();
				// long end = System.currentTimeMillis();
				// System.out.println("EXEC TIME: " + (end - start));
				System.out.print("P");
				break;
			} catch (Exception ex) {
				sCount--;
				if (sCount == 0) {
					ex.printStackTrace();
					// recoveredStrings =
					// logFileManagerForRecovery.recoverStrings(stepName, null);
					// if (recoveredStrings == null) {
					// todoFileManager.writeQuery(stepName, null, queryString);
					throw ex;
					// }
				} else {
					interval_error();
				}
			}
		}

		ArrayList<String> resultList = new ArrayList<String>();
		String[] filterStrs = URICollection.FILTER_PROPERTY;
		ArrayList<String> forLog = new ArrayList<String>();
		for (; results.hasNext();) {
			QuerySolution sol = results.next();
			Resource res = sol.getResource("p");
			if (res != null) {
				forLog.add(res.getURI());
				if (uriFilter(res.getURI(), filterStrs) != null) {
					resultList.add(res.getURI());
				}
			}
		}
		logFileManager.writeResult(stepName, null, forLog.toArray(new String[0]));
		qexec.close();
		String[] resultStringArray = resultList.toArray(new String[0]);
		return resultStringArray;
	}

	public String[] getDatatypes(String[] propertyURIs) throws Exception {
		HashSet<String> datatypes = new HashSet<String>();

		boolean errorFlag = false;
		if (propertyURIs != null) {
			String stepName = "getDatatypes";
			for (String propertyURI : propertyURIs) {
				String target = propertyURI;
				boolean targetErrorFlag = false;

				// QUERY
				// ---------------------------------------------------------------------------------
				// obtains all datatypes associated with the given properties
				// Note: virtuoso has bugs and cannot handle datatypes
				// correctly.
				// ---------------------------------------------------------------------------------------
				StringBuffer queryBuffer = new StringBuffer();
				queryBuffer.append("PREFIX owl: <" + URICollection.PREFIX_OWL + ">\n");
				queryBuffer.append("PREFIX rdfs: <" + URICollection.PREFIX_RDFS + ">\n");
				queryBuffer.append("PREFIX rdf: <" + URICollection.PREFIX_RDF + ">\n");
				queryBuffer.append("SELECT DISTINCT (datatype(?o) AS ?ldt) \n");
				if (graphURIs != null && graphURIs.length != 0) {
					for (String graphURI : graphURIs) {
						queryBuffer.append("FROM <");
						queryBuffer.append(graphURI);
						queryBuffer.append(">\n");
					}
				}
				// if (graphURIs != null && graphURIs.length != 0) {
				// for (String graphURI : graphURIs) {
				// queryBuffer.append("FROM <");
				// queryBuffer.append(graphURI);
				// queryBuffer.append(">\n");
				// }
				// }
				queryBuffer.append("WHERE{\n");
				queryBuffer.append(" [] <");
				queryBuffer.append(propertyURI);
				queryBuffer.append("> ?o.\n  FILTER(isLiteral(?o))\n");
				queryBuffer.append("}");
				String queryString = queryBuffer.toString();

				// System.out.println(queryString);

				Query query = QueryFactory.create(queryString);

				QueryExecution qexec = null;
				ResultSet results = null;
				String[] recoveredStringArray = null;

				int sCount = 3;
				while (sCount > 0) {
					try {
						// long start = System.currentTimeMillis();
						qexec = QueryExecutionFactory.sparqlService(endpointURI, query);
						endpointAccessCount++;
						qexec.setTimeout(-1);
						interval();
						results = qexec.execSelect();
						break;
					} catch (Exception ex) {
						sCount--;
						if (sCount == 0) {
							ex.printStackTrace();
							System.out.println(queryString);
							// recovery
							recoveredStringArray = logFileManagerForRecovery.recoverStrings(stepName, target);
							if (recoveredStringArray == null) {
								todoFileManager.writeQuery(stepName, target, queryString);
								targetErrorFlag = true;
								errorFlag = true;
							}
						} else {
							interval_error();
						}
					}
				}

				if (!targetErrorFlag) {
					if (recoveredStringArray != null) {
						String[] filterStrs = URICollection.FILTER_CLASS;
						String[] unfilterStrs = URICollection.UNFILTER_CLASS;
						for (String litURI : recoveredStringArray) {
							if (uriFilter(litURI, filterStrs, unfilterStrs) != null) {
								datatypes.add(litURI);
							}
						}
					} else {
						String[] filterStrs = URICollection.FILTER_CLASS;
						String[] unfilterStrs = URICollection.UNFILTER_CLASS;
						ArrayList<String> targetDataTypes = new ArrayList<String>();
						for (; results.hasNext();) {
							QuerySolution sol = results.next();
							Resource lit = sol.getResource("ldt");
							if (lit != null) {
								String litURI = lit.getURI();
								if (litURI != null) {
									targetDataTypes.add(litURI);
									if (uriFilter(litURI, filterStrs, unfilterStrs) != null) {
										datatypes.add(litURI);
									}
								}
							}
						}
						logFileManager.writeResult(stepName, target, targetDataTypes.toArray(new String[0]));
					}
				}
				qexec.close();
			}
		}
		if (!errorFlag) {
			String[] resultStringArray = datatypes.toArray(new String[0]);
			return resultStringArray;
		}
		throw new Exception();
	}

	public String uriFilter(String uriStr, String[] filterStrs) throws Exception {
		if (filterStrs == null || filterStrs.length == 0) {
			return uriStr;
		}
		for (String str : filterStrs) {
			if (uriStr != null) {
				if (uriStr.startsWith(str)) {
					return null;
				}
			}

		}
		return uriStr;
	}

	public String uriFilter(String uriStr, String[] filterStrs, String[] unFilterStrs) throws Exception {
		if (unFilterStrs == null || unFilterStrs.length == 0) {
			// nothing to do
		} else {
			for (String str : unFilterStrs) {
				if (uriStr != null) {
					if (uriStr.startsWith(str)) {
						return uriStr;
					}
				}
			}
		}

		if (filterStrs == null || filterStrs.length == 0) {
			return uriStr;
		}
		for (String str : filterStrs) {
			if (uriStr != null) {
				if (uriStr.startsWith(str)) {
					return null;
				}
			}

		}
		return uriStr;
	}

}
