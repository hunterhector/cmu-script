package edu.cmu.cs.lti.emd.stat;

import edu.cmu.cs.lti.script.type.EventMention;
import edu.cmu.cs.lti.script.type.StanfordCorenlpToken;
import edu.cmu.cs.lti.uima.io.writer.AbstractPlainTextAggregator;
import edu.cmu.cs.lti.uima.util.UimaNlpUtils;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.procedure.TObjectIntProcedure;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.util.*;

public class PotentialEventSufaceStats extends AbstractPlainTextAggregator {

    private TObjectIntMap<String> allVerbs;
    private TObjectIntMap<String> eventTypes;
    private TObjectIntHashMap<String> predictedTypePair;
    private TObjectIntHashMap<String> unmarkedVerbs;

    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
        allVerbs = new TObjectIntHashMap<>();
        unmarkedVerbs = new TObjectIntHashMap<>();
        eventTypes = new TObjectIntHashMap<>();
        predictedTypePair = new TObjectIntHashMap<>();
    }

    @Override
    public String getAggregatedTextToPrint() {
        StringBuilder text = new StringBuilder("Event and Verb Statistics.");

        text.append("\n");
        text.append("##Verb Counts\n");
        for (Pair<String, Integer> verbCount : getCountSortedMap(allVerbs, 2)) {
            text.append(verbCount.getKey()).append("\t").append(verbCount.getValue()).append("\n");
        }

        text.append("\n");
        text.append("##Verbs not annotated\n");
        for (Pair<String, Integer> nonEventVerbCount : getCountSortedMap(unmarkedVerbs, 2)) {
            text.append(nonEventVerbCount.getKey()).append("\t").append(nonEventVerbCount.getValue()).append("\n");
        }

        text.append("\n");
        text.append("##Event Type Count\n");
        for (Pair<String, Integer> eventCount : getCountSortedMap(eventTypes, 2)) {
            text.append(eventCount.getKey()).append("\t").append(eventCount.getValue()).append("\n");
        }

        text.append("\n");
        text.append("##Type Text Count\n");
        for (Pair<String, Integer> tCount : getCountSortedMap(predictedTypePair, 2)) {
            text.append(tCount.getKey()).append("\t").append(tCount.getValue()).append("\n");
        }

        return text.toString();
    }

    private List<Pair<String, Integer>> getCountSortedMap(TObjectIntMap<String> inputMap, int threshold) {
        List<Pair<String, Integer>> collection = new ArrayList<>();
        inputMap.forEachEntry((s, i) -> {
            if (i >= threshold) {
                collection.add(Pair.of(s, i));
            }
            return true;
        });

        // Sort in inverse order.
        collection.sort((o1, o2) -> o2.getValue().compareTo(o1.getValue()));

        return collection;
    }

    @Override
    public void process(JCas aJCas) throws AnalysisEngineProcessException {
        Set<StanfordCorenlpToken> markedTokens = new HashSet<>();
        for (EventMention evm : JCasUtil.select(aJCas, EventMention.class)) {
            eventTypes.adjustOrPutValue(evm.getEventType(), 1, 1);
            predictedTypePair.adjustOrPutValue(evm.getEventType() + "_" +
                    UimaNlpUtils.getLemmatizedAnnotation(evm).toLowerCase(), 1, 1);

            if (!evm.getEventType().equals("Verbal")) {
                markedTokens.add((StanfordCorenlpToken) evm.getHeadWord());
            }
        }

        for (StanfordCorenlpToken token : JCasUtil.select(aJCas, StanfordCorenlpToken.class)) {
            if (token.getPos().startsWith("V")) {
                allVerbs.adjustOrPutValue(token.getLemma().toLowerCase(), 1, 1);

                if (!markedTokens.contains(token)) {
                    unmarkedVerbs.adjustOrPutValue(token.getLemma().toLowerCase(), 1, 1);
                }
            }
        }
    }

}
