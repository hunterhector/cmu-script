package edu.cmu.cs.lti.event_coref.pipeline;

import edu.cmu.cs.lti.event_coref.train.PaLatentTreeTrainer;
import edu.cmu.cs.lti.uima.pipeline.LoopPipeline;
import edu.cmu.cs.lti.utils.Configuration;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeSystemDescription;

import java.io.File;
import java.io.IOException;

/**
 * Train the latent tree coreference engine.
 *
 * @author Zhengzhong Liu
 */
public class LatentTreeTrainingLooper extends LoopPipeline {
    private int maxIteration;
    private int numIteration;
    private String modelBasename;

    protected LatentTreeTrainingLooper(Configuration taskConfig, String modelOutputBasename, String cacheDir,
                                       TypeSystemDescription typeSystemDescription,
                                       CollectionReaderDescription readerDescription) throws
            ResourceInitializationException {
        super(readerDescription, setup(typeSystemDescription, cacheDir, taskConfig));
        this.maxIteration = taskConfig.getInt("edu.cmu.cs.lti.perceptron.maxiter", 20);
        this.numIteration = 0;
        this.modelBasename = modelOutputBasename;

        logger.info("Latent Tree training started, maximum iteration is " + maxIteration);
    }

    @Override
    protected boolean checkStopCriteria() {
        return numIteration >= maxIteration;
    }

    @Override
    protected void stopActions() {
        logger.info("Finalizing coreference training ...");
        logger.info("Saving final models at " + modelBasename);
        try {
            PaLatentTreeTrainer.saveModels(new File(modelBasename));
            PaLatentTreeTrainer.finish();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void loopActions() {
        numIteration++;
        try {
            PaLatentTreeTrainer.saveModels(new File(modelBasename + "_iter" + numIteration));
        } catch (IOException e) {
            e.printStackTrace();
        }

        logger.info(String.format("Latent Tree Training Iteration %d finished ... ", numIteration));
    }

    private static AnalysisEngineDescription setup(TypeSystemDescription typeSystemDescription, String cacheDir,
                                                   Configuration taskConfig) throws ResourceInitializationException {
        return AnalysisEngineFactory.createEngineDescription(
                PaLatentTreeTrainer.class, typeSystemDescription,
                PaLatentTreeTrainer.PARAM_CONFIG_PATH, taskConfig.getConfigFile().getPath(),
                PaLatentTreeTrainer.PARAM_CACHE_DIRECTORY, cacheDir
        );
    }
}