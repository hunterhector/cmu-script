package edu.cmu.cs.lti.script.io;

import stanford.nlp.jcoref.docclustering.DocumentClustering.Document;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class GigawordTextReader {

  private File[] textFiles;

  public GigawordTextReader(String path) throws FileNotFoundException, IOException {
    this(new File(path));
  }

  public GigawordTextReader(File dir) throws FileNotFoundException, IOException {
    textFiles = dir.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return name.toLowerCase().endsWith(".txt");
      }
    });

  }

  public List<String> getDocumentAsString() throws FileNotFoundException, IOException {
    List<String> docStrings = new ArrayList<String>();
    for (File gigaTextDoc : textFiles) {
      docStrings.add(IOUtils.toString(new FileInputStream(gigaTextDoc)));
    }
    return docStrings;
  }

  public List<Document> getDocumentForClustering() throws FileNotFoundException, IOException {
    List<Document> docStrings = new ArrayList<Document>();
    for (File gigaTextDoc : textFiles) {
      docStrings.add(new Document(getDocId(gigaTextDoc), IOUtils.toString(new FileInputStream(
              gigaTextDoc))));
    }
    return docStrings;
  }

  public String getDocId(File gigaDoc) {
    return FilenameUtils.getBaseName(gigaDoc.getName());
  }

}
