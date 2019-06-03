package edu.cmu.cs.lti.script.pipeline;

import edu.cmu.cs.lti.annotators.FanseAnnotator;
import edu.cmu.cs.lti.annotators.StanfordCoreNlpAnnotator;
import edu.cmu.cs.lti.collection_reader.JsonEventDataReader;
import edu.cmu.cs.lti.pipeline.BasicPipeline;
import edu.cmu.cs.lti.script.annotators.ArgumentMerger;
import edu.cmu.cs.lti.script.annotators.FrameBasedEventDetector;
import edu.cmu.cs.lti.script.annotators.SemaforAnnotator;
import edu.cmu.cs.lti.script.annotators.VerbBasedEventDetector;
import edu.cmu.cs.lti.script.annotators.writer.ArgumentClozeTaskWriter;
import edu.cmu.cs.lti.uima.annotator.AbstractAnnotator;
import edu.cmu.cs.lti.uima.io.reader.CustomCollectionReaderFactory;
import edu.cmu.cs.lti.uima.io.reader.PlainTextCollectionReader;
import edu.cmu.cs.lti.uima.util.UimaNlpUtils;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.factory.TypeSystemDescriptionFactory;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Created with IntelliJ IDEA.
 * Date: 10/22/18
 * Time: 12:06 PM
 *
 * @author Zhengzhong Liu
 */
public class ImplicitFeatureExtractionPipeline {
    private static final Logger logger = LoggerFactory.getLogger(UimaNlpUtils.class);

    private static void full_run(String[] args) throws UIMAException {
        String resourceDir = args[0];
        String sourceTextDir = args[1];
        String annotateDir = args[2];
        String workingDir = args[3];
        String corpusName = args[4];

        TypeSystemDescription des = TypeSystemDescriptionFactory.createTypeSystemDescription("TypeSystem");

        if (!new File(workingDir, "parsed").exists()) {
            logger.info("Parsed directory not found, now parse it.");

            String semaforModelDirectory = "../models/semafor_malt_model_20121129";
            String fanseModelDirectory = "../models/fanse_models";

            boolean useWhiteSpaceTokenization = false;
            boolean eolSentenceSplit = false;
            if (corpusName.equals("nombank")) {
                useWhiteSpaceTokenization = true;
                eolSentenceSplit = true;
            }

            CollectionReaderDescription reader = CollectionReaderFactory.createReaderDescription(
                    PlainTextCollectionReader.class,
                    PlainTextCollectionReader.PARAM_INPUTDIR, sourceTextDir,
                    PlainTextCollectionReader.PARAM_TEXT_SUFFIX, ".txt");

            AnalysisEngineDescription parser = AnalysisEngineFactory.createEngineDescription(
                    StanfordCoreNlpAnnotator.class, des,
                    StanfordCoreNlpAnnotator.PARAM_LANGUAGE, "en",
                    StanfordCoreNlpAnnotator.PARAM_WHITESPACE_TOKENIZE, useWhiteSpaceTokenization,
//                    StanfordCoreNlpAnnotator.PARAM_SPLIT_ONLY, true,
                    StanfordCoreNlpAnnotator.PARAM_EOL_SENTENCE_ONLY, eolSentenceSplit,
                    StanfordCoreNlpAnnotator.PARAM_STANFORD_DEP, true
            );

            AnalysisEngineDescription semafor = AnalysisEngineFactory.createEngineDescription(
                    SemaforAnnotator.class, des,
                    SemaforAnnotator.SEMAFOR_MODEL_PATH, semaforModelDirectory);

            AnalysisEngineDescription fanse = AnalysisEngineFactory.createEngineDescription(
                    FanseAnnotator.class, des,
                    FanseAnnotator.PARAM_MODEL_BASE_DIR, fanseModelDirectory,
                    AbstractAnnotator.MULTI_THREAD, true
            );

            AnalysisEngineDescription merger = AnalysisEngineFactory.createEngineDescription(ArgumentMerger.class, des);

//            BasicPipeline pipeline = new BasicPipeline(reader, workingDir, "parsed", 16, parser);

            BasicPipeline pipeline = new BasicPipeline(reader, workingDir, "parsed", 16, parser, fanse, semafor,
                    merger);
            pipeline.run();
        } else {
            logger.info("Do not re-parse documents.");
        }

        CollectionReaderDescription parsedData = CustomCollectionReaderFactory.createRecursiveXmiReader(workingDir,
                "parsed");

        // Gold standard event annotators.
        AnalysisEngineDescription goldAnnotator = AnalysisEngineFactory.createEngineDescription(
                JsonEventDataReader.class, des,
                JsonEventDataReader.PARAM_JSON_ANNO_DIR, annotateDir,
                JsonEventDataReader.PARAM_CLEANUP_ENTITY, true
        );

        // Non-gold event annotators.
        AnalysisEngineDescription verbEvents = AnalysisEngineFactory.createEngineDescription(
                VerbBasedEventDetector.class, des
        );

        AnalysisEngineDescription frameEvents = AnalysisEngineFactory.createEngineDescription(
                FrameBasedEventDetector.class, des,
                FrameBasedEventDetector.PARAM_FRAME_RELATION, new File(resourceDir, "fndata-1.7/frRelation.xml"),
                FrameBasedEventDetector.PARAM_IGNORE_BARE_FRAME, true
        );

        AnalysisEngineDescription featureExtractor = AnalysisEngineFactory.createEngineDescription(
                ArgumentClozeTaskWriter.class, des,
                ArgumentClozeTaskWriter.PARAM_OUTPUT_FILE, new File(workingDir, "cloze.json")
        );

        new BasicPipeline(parsedData, workingDir, "events", 16, goldAnnotator,
                verbEvents, frameEvents, featureExtractor).run();
    }

    private static void cloze_only(String[] args) throws UIMAException {
        String workingDir = args[0];

        TypeSystemDescription des = TypeSystemDescriptionFactory.createTypeSystemDescription("TypeSystem");

        CollectionReaderDescription reader = CustomCollectionReaderFactory.createXmiReader(
                des, workingDir, "events"
        );

        AnalysisEngineDescription featureExtractor = AnalysisEngineFactory.createEngineDescription(
                ArgumentClozeTaskWriter.class, des,
                ArgumentClozeTaskWriter.PARAM_OUTPUT_FILE, new File(workingDir, "cloze.json")
        );

        new BasicPipeline(reader, 16, featureExtractor).run();
    }


    public static void main(String[] args) throws UIMAException {
        if (args.length == 5) {
            full_run(args);
        } else if (args.length == 1) {
            cloze_only(args);
        }
    }
}
