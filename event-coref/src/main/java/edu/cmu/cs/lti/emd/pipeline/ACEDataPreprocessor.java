package edu.cmu.cs.lti.emd.pipeline;

import edu.cmu.cs.lti.collection_reader.AceDataCollectionReader;
import edu.cmu.cs.lti.uima.io.writer.CustomAnalysisEngineFactory;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.uimafit.factory.TypeSystemDescriptionFactory;

/**
 * This pipeline runs FanseAnnotator.
 */
public class ACEDataPreprocessor {
    private static String className = ACEDataPreprocessor.class.getSimpleName();

    public static void main(String[] args) throws UIMAException {
        System.out.println(className + " started...");
        String paramInputDir =
                "data/raw/ACE2005-TrainingData-V6.0/English";

        // Parameters for the writer
        String paramParentOutputDir = "data/ACE2005_Training/";
        String paramBaseOutputDirName = "semafor_processed";

        String paramTypeSystemDescriptor = "TypeSystem";

        String semaforModelDirectory = "../models/semafor_malt_model_20121129";
        String fanseModelDirectory = "../models/fanse_models";

        String goldView = "goldStandard";

        // Instantiate the analysis engine.
        TypeSystemDescription typeSystemDescription = TypeSystemDescriptionFactory
                .createTypeSystemDescription(paramTypeSystemDescriptor);

        CollectionReaderDescription reader = CollectionReaderFactory.createReaderDescription(
                AceDataCollectionReader.class, typeSystemDescription,
                AceDataCollectionReader.PARAM_ACE_DATA_PATH, paramInputDir,
                AceDataCollectionReader.PARAM_GOLD_STANDARD_VIEW_NAME, goldView
        );
//        AnalysisEngineDescription goldstandardAnnotator = AnalysisEngineFactory.createEngineDescription(
//                AceDataGoldenAnnotator.class, typeSystemDescription,
//                AceDataGoldenAnnotator.PARAM_GOLD_STANDARD_VIEW_NAME, goldView);
//
//
//        AnalysisEngineDescription stanfordAnalyzer = AnalysisEngineFactory.createEngineDescription(
//                StanfordCoreNlpAnnotator.class, typeSystemDescription,
//                StanfordCoreNlpAnnotator.PARAM_USE_SUTIME, true);
//
//        AnalysisEngineDescription semaforAnalyzer = AnalysisEngineFactory.createEngineDescription(
//                SemaforAnnotator.class, typeSystemDescription,
//                SemaforAnnotator.SEMAFOR_MODEL_PATH, semaforModelDirectory);
//
//        AnalysisEngineDescription fanseParser = AnalysisEngineFactory.createEngineDescription(
//                FanseAnnotator.class, typeSystemDescription, FanseAnnotator.PARAM_MODEL_BASE_DIR,
//                fanseModelDirectory);

        AnalysisEngineDescription writer = CustomAnalysisEngineFactory.createXmiWriter(paramParentOutputDir,
                paramBaseOutputDirName, 0, null);

        // Run the pipeline.
        try {
//            SimplePipeline.runPipeline(reader, goldstandardAnnotator, stanfordAnalyzer, fanseParser,
// semaforAnalyzer, writer);
            SimplePipeline.runPipeline(reader, writer);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        System.out.println(className + " successfully completed.");
    }

}
