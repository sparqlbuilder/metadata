package org.sparqlbuilder.metadata.crawler.datastructure;

import java.io.File;
import java.io.FileOutputStream;

import com.hp.hpl.jena.rdf.model.Model;

public class SchemaCategory {

	public Model model = null;
	public int category = 0;


	public void write2File(String fileName, String lang) throws Exception{
		if( model != null && fileName != null ){
			if( lang == null ) {
				lang = "RDF/XML-ABBREV";
			}
			File file = new File(fileName);
			FileOutputStream fos = new FileOutputStream(file);
			model.write(fos, lang,"http://sparqlbuilder.org/");
		}
	}


	public SchemaCategory(int category, Model model ){
		this.category = category;
		this.model = model;
	}


	public final Model getModel() {
		return model;
	}


	public final void setModel(Model model) {
		this.model = model;
	}


	public final int getCategory() {
		return category;
	}


	public final void setCategory(int category) {
		this.category = category;
	}

}
