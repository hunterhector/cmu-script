package edu.cmu.cs.lti.learning.feature.sequence.document.functions;

import com.google.common.collect.Table;
import edu.cmu.cs.lti.learning.feature.sequence.FeatureUtils;
import edu.cmu.cs.lti.learning.feature.sequence.base.SequenceFeatureWithFocus;
import edu.cmu.cs.lti.learning.utils.MentionTypeUtils;
import edu.cmu.cs.lti.script.type.EventMention;
import edu.cmu.cs.lti.utils.Configuration;
import gnu.trove.map.TObjectDoubleMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.uima.jcas.JCas;

import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * Date: 4/24/16
 * Time: 2:41 PM
 *
 * @author Zhengzhong Liu
 */
public class TypeHistoryFeatures extends SequenceFeatureWithFocus<EventMention> {
    public TypeHistoryFeatures(Configuration generalConfig, Configuration featureConfig) {
        super(generalConfig, featureConfig);
    }

    @Override
    public void initDocumentWorkspace(JCas context) {

    }

    @Override
    public void resetWorkspace(JCas aJCas) {

    }

    @Override
    public void extract(List<EventMention> sequence, int focus, TObjectDoubleMap<String> nodeFeatures,
                        Table<Pair<Integer, Integer>, String, Double> edgeFeatures) {

    }

    @Override
    public void extractGlobal(List<EventMention> sequence, int focus, TObjectDoubleMap<String> globalFeatures,
                              Map<Integer, String> knownStates) {
        for (int i = 0; i < focus; i++) {
            if (knownStates.containsKey(i)) {
                String historyType = knownStates.get(i);
                for (String s : MentionTypeUtils.splitToTmultipleTypes(historyType)) {
                    addToFeatures(globalFeatures, FeatureUtils.formatFeatureName("history_type", s), 1);
                }
            }
        }
    }
}
