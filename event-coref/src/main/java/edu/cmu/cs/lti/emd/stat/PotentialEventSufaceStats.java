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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PotentialEventSufaceStats extends AbstractPlainTextAggregator {

    private TObjectIntMap<String> allVerbs;
    private TObjectIntMap<String> eventTypes;
    private TObjectIntHashMap<String> predictedTypePair;

    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
        allVerbs = new TObjectIntHashMap<>();
        eventTypes = new TObjectIntHashMap<>();
        predictedTypePair = new TObjectIntHashMap<>();
    }

    @Override
    public String getAggregatedTextToPrint() {
        StringBuilder text = new StringBuilder("##Verb Counts");

        for (Pair<String, Integer> verbCount : getCountSortedMap(allVerbs, 2)) {
            text.append(verbCount.getKey()).append("\t").append(verbCount.getValue()).append("\n");
        }

        text.append("\n");
        text.append("##Event Type Count");

        for (Pair<String, Integer> eventCount : getCountSortedMap(eventTypes, 2)) {
            text.append(eventCount.getKey()).append("\t").append(eventCount.getValue()).append("\n");
        }

        text.append("\n");
        text.append("##Type Text Count");
        for (Pair<String, Integer> tCount : getCountSortedMap(eventTypes, 2)) {
            text.append(tCount.getKey()).append("\t").append(tCount.getValue()).append("\n");
        }

        return text.toString();
    }

    private List<Pair<String, Integer>> getCountSortedMap(TObjectIntMap<String> inputMap, int threshold) {
        List<Pair<String, Integer>> collection = new ArrayList<>();
        inputMap.forEachEntry(new TObjectIntProcedure<String>() {
            @Override
            public boolean execute(String s, int i) {
                if (i >= threshold) {
                    collection.add(Pair.of(s, i));
                }
                return true;
            }
        });

        collection.sort(new Comparator<Pair<String, Integer>>() {
            @Override
            public int compare(Pair<String, Integer> o1, Pair<String, Integer> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }
        });

        return collection;
    }

    @Override
    public void process(JCas aJCas) throws AnalysisEngineProcessException {
        for (StanfordCorenlpToken token : JCasUtil.select(aJCas, StanfordCorenlpToken.class)) {
            if (token.getPos().startsWith("V")) {
                allVerbs.adjustOrPutValue(token.getLemma().toLowerCase(), 1, 1);
            }
        }

        for (EventMention evm : JCasUtil.select(aJCas, EventMention.class)) {
            eventTypes.adjustOrPutValue(evm.getEventType(), 1, 1);
            predictedTypePair.adjustOrPutValue(evm.getEventType() + "_" +
                    UimaNlpUtils.getLemmatizedAnnotation(evm).toLowerCase(), 1, 1);
        }
    }

}
