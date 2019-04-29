package edu.cmu.cs.lti.script.pipeline;

import edu.cmu.cs.lti.annotators.EventMentionRemover;
import edu.cmu.cs.lti.annotators.GoldStandardEventMentionAnnotator;
import edu.cmu.cs.lti.model.UimaConst;
import edu.cmu.cs.lti.pipeline.BasicPipeline;
import edu.cmu.cs.lti.script.annotators.EnglishSrlArgumentExtractor;
import edu.cmu.cs.lti.script.annotators.FrameBasedEventDetector;
import edu.cmu.cs.lti.script.annotators.VerbBasedEventDetector;
import edu.cmu.cs.lti.script.annotators.writer.ArgumentClozeTaskWriter;
import edu.cmu.cs.lti.uima.io.reader.CustomCollectionReaderFactory;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.TypeSystemDescriptionFactory;
import org.apache.uima.resource.metadata.TypeSystemDescription;

import java.io.File;

/**
 * Created with IntelliJ IDEA.
 * Date: 2019-04-27
 * Time: 14:08
 *
 * @author Zhengzhong Liu
 */
public class AutoClozeTrainCreationPipeline {
    public static void main(String[] args) throws UIMAException {
        String paramTypeSystemDescriptor = "TaskEventMentionDetectionTypeSystem";

        String resourceDir = args[0];
        String workingDir = args[1];
        String inputBase = args[2];
        String outputFile = args[3];

        boolean takeAllFrames = false;
        if (args.length > 4) {
            if (args[4].equals("allFrames")) {
                takeAllFrames = true;
            }
        }

        // Instantiate the analysis engine.
        TypeSystemDescription typeSystemDescription = TypeSystemDescriptionFactory
                .createTypeSystemDescription(paramTypeSystemDescriptor);

        // Reader and extractors for unsupervised events.
        CollectionReaderDescription reader = CustomCollectionReaderFactory.createRecursiveGzippedXmiReader(
                typeSystemDescription, workingDir, inputBase
        );


        AnalysisEngineDescription remover = AnalysisEngineFactory.createEngineDescription(EventMentionRemover.class);

        AnalysisEngineDescription frameEvents = AnalysisEngineFactory.createEngineDescription(
                FrameBasedEventDetector.class, typeSystemDescription,
                FrameBasedEventDetector.PARAM_FRAME_RELATION, new File(resourceDir, "fndata-1.7/frRelation.xml"),
                FrameBasedEventDetector.PARAM_IGNORE_BARE_FRAME, true,
                FrameBasedEventDetector.PARAM_TAKE_ALL_FRAMES, takeAllFrames
        );

        AnalysisEngineDescription verbEvents = AnalysisEngineFactory.createEngineDescription(
                VerbBasedEventDetector.class, typeSystemDescription
        );


        //        // Reader and extractors for existing mentions.
//        CollectionReaderDescription reader = CustomCollectionReaderFactory.createXmiReader(
//                typeSystemDescription, workingDir, inputBase
//        );

        AnalysisEngineDescription goldAnnotator = AnalysisEngineFactory.createEngineDescription(
                GoldStandardEventMentionAnnotator.class, typeSystemDescription,
                GoldStandardEventMentionAnnotator.PARAM_TARGET_VIEWS,
                new String[]{CAS.NAME_DEFAULT_SOFA, UimaConst.inputViewName},
                GoldStandardEventMentionAnnotator.PARAM_COPY_MENTION_TYPE, true,
                GoldStandardEventMentionAnnotator.PARAM_COPY_REALIS, true,
                GoldStandardEventMentionAnnotator.PARAM_COPY_CLUSTER, true,
                GoldStandardEventMentionAnnotator.PARAM_COPY_RELATIONS, true
        );

        AnalysisEngineDescription arguments = AnalysisEngineFactory.createEngineDescription(
                EnglishSrlArgumentExtractor.class, typeSystemDescription,
                EnglishSrlArgumentExtractor.PARAM_ADD_SEMAFOR, true,
                EnglishSrlArgumentExtractor.PARAM_ADD_FANSE, false,
                EnglishSrlArgumentExtractor.PARAM_ADD_DEPENDENCY, true
        );

        AnalysisEngineDescription clozeExtractor = AnalysisEngineFactory.createEngineDescription(
                ArgumentClozeTaskWriter.class, typeSystemDescription,
                ArgumentClozeTaskWriter.PARAM_OUTPUT_FILE, outputFile,
                ArgumentClozeTaskWriter.PARAM_ADD_EVENT_COREF, true
        );


        // Write only clozes.
//        new BasicPipeline(reader, false, true, 7, goldAnnotator, arguments, clozeExtractor).run();
        new BasicPipeline(reader, false, true, 7, remover, frameEvents, verbEvents,
                goldAnnotator, arguments, clozeExtractor).run();

    }
}
