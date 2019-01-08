package edu.cmu.cs.lti.script.pipeline;

import edu.cmu.cs.lti.annotators.FanseAnnotator;
import edu.cmu.cs.lti.annotators.StanfordCoreNlpAnnotator;
import edu.cmu.cs.lti.collection_reader.JsonEventDataReader;
import edu.cmu.cs.lti.pipeline.BasicPipeline;
import edu.cmu.cs.lti.script.annotators.ArgumentMerger;
import edu.cmu.cs.lti.script.annotators.SemaforAnnotator;
import edu.cmu.cs.lti.script.annotators.SingletonAnnotator;
import edu.cmu.cs.lti.script.annotators.writer.ArgumentClozeTaskWriter;
import edu.cmu.cs.lti.uima.annotator.AbstractAnnotator;
import edu.cmu.cs.lti.uima.io.reader.PlainTextCollectionReader;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.collection.metadata.CpeDescriptorException;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.factory.TypeSystemDescriptionFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * Date: 10/22/18
 * Time: 12:06 PM
 *
 * @author Zhengzhong Liu
 */
public class ImplicitFeatureExtractionPipeline {
    public static void main(String[] args) throws UIMAException, SAXException, CpeDescriptorException, IOException {
        String sourceTextDir = args[0];
        String annotateDir = args[1];
        String workingDir = args[2];

        String semaforModelDirectory = "../models/semafor_malt_model_20121129";
        String fanseModelDirectory = "../models/fanse_models";

        TypeSystemDescription des = TypeSystemDescriptionFactory.createTypeSystemDescription("TypeSystem");

        CollectionReaderDescription reader = CollectionReaderFactory.createReaderDescription(
                PlainTextCollectionReader.class,
                PlainTextCollectionReader.PARAM_INPUTDIR, sourceTextDir,
                PlainTextCollectionReader.PARAM_TEXT_SUFFIX, ".txt");

        AnalysisEngineDescription parser = AnalysisEngineFactory.createEngineDescription(
                StanfordCoreNlpAnnotator.class, des,
                StanfordCoreNlpAnnotator.PARAM_LANGUAGE, "en"
        );

        AnalysisEngineDescription goldAnnotator = AnalysisEngineFactory.createEngineDescription(
                JsonEventDataReader.class, des,
                JsonEventDataReader.PARAM_JSON_ANNO_DIR, annotateDir
        );

        AnalysisEngineDescription semafor = AnalysisEngineFactory.createEngineDescription(
                SemaforAnnotator.class, des,
                SemaforAnnotator.SEMAFOR_MODEL_PATH, semaforModelDirectory);

        AnalysisEngineDescription fanse = AnalysisEngineFactory.createEngineDescription(
                FanseAnnotator.class, des,
                FanseAnnotator.PARAM_MODEL_BASE_DIR, fanseModelDirectory,
                AbstractAnnotator.MULTI_THREAD, true
        );

        AnalysisEngineDescription singletonCreator = AnalysisEngineFactory.createEngineDescription(
                SingletonAnnotator.class, des);

        AnalysisEngineDescription merger = AnalysisEngineFactory.createEngineDescription(
                ArgumentMerger.class, des);

        AnalysisEngineDescription featureExtractor = AnalysisEngineFactory.createEngineDescription(
                ArgumentClozeTaskWriter.class, des,
                ArgumentClozeTaskWriter.PARAM_OUTPUT_FILE, new File(workingDir, "cloze.json")
        );

        BasicPipeline pipeline = new BasicPipeline(reader, workingDir, "gold", goldAnnotator);

//        BasicPipeline pipeline = new BasicPipeline(reader, workingDir, "gold", parser, fanse, semafor, goldAnnotator,
//                merger, singletonCreator);

        pipeline.run();

        CollectionReaderDescription dataReader = pipeline.getOutput();


        SimplePipeline.runPipeline(dataReader, featureExtractor);
    }
}
