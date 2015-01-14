package edu.cmu.cs.lti.script.annotators.learn.test;

import com.google.common.base.Joiner;
import edu.cmu.cs.lti.cds.ml.features.CompactFeatureExtractor;
import edu.cmu.cs.lti.collections.TLongShortDoubleHashTable;
import edu.cmu.cs.lti.script.model.ContextElement;
import edu.cmu.cs.lti.script.model.LocalArgumentRepre;
import edu.cmu.cs.lti.script.model.MooneyEventRepre;
import edu.cmu.cs.lti.script.utils.DataPool;
import edu.cmu.cs.lti.utils.Comparators;
import edu.cmu.cs.lti.utils.TLongBasedFeatureTable;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.uima.UimaContext;
import weka.core.SerializationHelper;

import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: zhengzhongliu
 * Date: 1/14/15
 * Time: 1:16 AM
 */
public class CompactLogLinearTester extends MultiArgumentClozeTest {

    public static final String PARAM_DB_DIR_PATH = "dbLocation";
    public static final String PARAM_MODEL_PATH = "modelPath";
    public static final String PARAM_SKIP_GRAM_N = "skipgramN";
    public static final String PARAM_FEATURE_NAMES = "featureNames";

    private CompactFeatureExtractor extractor;
    private TLongBasedFeatureTable compactWeights;
    private int skipGramN = 2;
    private String[] allPredicates;

    @Override
    protected String initializePredictor(UimaContext aContext) {

        String predictorName = this.getClass().getSimpleName();

        String[] featureImplNames = (String[]) aContext.getConfigParameterValue(PARAM_FEATURE_NAMES);

        predictorName += "_" + Joiner.on(",").join(featureImplNames);

        logger.info("Initializing predictor : " + predictorName);

        skipGramN = (Integer) aContext.getConfigParameterValue(PARAM_SKIP_GRAM_N);

        String modelPath = (String) aContext.getConfigParameterValue(PARAM_MODEL_PATH);

        logger.info("Loading from " + modelPath);

        try {
            compactWeights = (TLongBasedFeatureTable) SerializationHelper.read(modelPath);
            extractor = new CompactFeatureExtractor(compactWeights, featureImplNames);
        } catch (Exception e) {
            e.printStackTrace();
        }

        allPredicates = DataPool.headWords;
        return predictorName;
    }


    @Override
    protected PriorityQueue<Pair<MooneyEventRepre, Double>> predict(List<ContextElement> chain, Set<Integer> entities, int testIndex, int numArguments) {
        ContextElement answer = chain.get(testIndex);
        logEvalInfo("Answer is " + answer.getMention());

        PriorityQueue<Pair<MooneyEventRepre, Double>> rankedEvents = new PriorityQueue<>(allPredicates.length,
                new Comparators.DescendingScoredPairComparator<MooneyEventRepre, Double>());

        Set<Integer> mooneyEntities = getRewritedEntitiesFromChain(chain);

        ContextElement realElement = chain.get(testIndex);

        for (String head : allPredicates) {
            List<MooneyEventRepre> candidateMooeyEvms = MooneyEventRepre.generateTuples(head, mooneyEntities);
            for (MooneyEventRepre candidateEvm : candidateMooeyEvms) {
                ContextElement candidate = ContextElement.fromMooney(realElement.getJcas(), realElement.getSent(), realElement.getHead(), candidateEvm);
                TLongShortDoubleHashTable features = extractor.getFeatures(chain, candidate, testIndex, skipGramN, false);

                double score = compactWeights.dotProd(features);
                if (score > 0) {
                    logEvalInfo("Candidate is " + candidate.getMention());
                    logEvalInfo("Feature score " + score);
                    compactWeights.dotProd(features);
                    logEvalInfo("Candidate features : ");
                    logEvalInfo(features.dump(DataPool.headWords, extractor.getFeatureNamesByIndex()));
//                    compactWeights.dotProd(features, extractor.getFeatureNamesByIndex(), DataPool.headWords);
                }

                if (candidate.getMention().mooneyMatch(answer.getMention())) {
                    logEvalInfo("Answer candidate appears: " + candidate.getMention());
                    logEvalInfo("Feature score " + score);
                    logEvalInfo("Answer features : ");
                    logEvalInfo(features.dump(DataPool.headWords, extractor.getFeatureNamesByIndex()));
                }
                rankedEvents.add(Pair.of(candidateEvm, score));
            }
        }
        return rankedEvents;
    }

    public static Set<Integer> getRewritedEntitiesFromChain(List<ContextElement> chain) {
        Set<Integer> entities = new HashSet<>();
        for (ContextElement rep : chain) {
            for (LocalArgumentRepre arg : rep.getMention().getArgs()) {
                if (arg != null && !arg.isOther()) {
                    entities.add(arg.getRewritedId());
                }
            }
        }
        return entities;
    }
}
