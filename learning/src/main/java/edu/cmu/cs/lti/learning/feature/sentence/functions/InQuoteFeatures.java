package edu.cmu.cs.lti.learning.feature.sentence.functions;

import edu.cmu.cs.lti.script.type.QuotedContent;
import edu.cmu.cs.lti.script.type.StanfordCorenlpToken;
import edu.cmu.cs.lti.utils.Configuration;
import gnu.trove.map.TObjectDoubleMap;
import java8.util.function.Consumer;
import java8.util.function.Predicate;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * Date: 9/14/15
 * Time: 2:24 PM
 *
 * @author Zhengzhong Liu
 */
public class InQuoteFeatures extends SequenceFeatureWithFocus {
    private Set<StanfordCorenlpToken> phraseQuotedTokens;

    public InQuoteFeatures(Configuration generalConfig, Configuration featureConfig) {
        super(generalConfig, featureConfig);
    }

    @Override
    public void initDocumentWorkspace(JCas context) {
        phraseQuotedTokens = new HashSet<StanfordCorenlpToken>();
        StreamSupport.stream(JCasUtil.select(context, QuotedContent.class)).filter(new Predicate<QuotedContent>() {
            @Override
            public boolean test(QuotedContent content) {
                return content.getPhraseQuote();
            }
        }).forEach(new Consumer<QuotedContent>() {
            @Override
            public void accept(QuotedContent content) {
                phraseQuotedTokens.addAll(StreamSupport.stream(
                        JCasUtil.selectCovered(StanfordCorenlpToken.class, content))
                        .collect(Collectors.<StanfordCorenlpToken>toList()));
            }
        });
    }

    @Override
    public void resetWorkspace(JCas aJCas, int begin, int end) {

    }

    @Override
    public void extract(List<StanfordCorenlpToken> sequence, int focus, TObjectDoubleMap<String> features,
                        TObjectDoubleMap<String> featuresNeedForState) {
        if (focus > 0 && focus < sequence.size()) {
            isInPhraseQuote(features, sequence.get(focus));
        }
    }

    private void isInPhraseQuote(TObjectDoubleMap<String> features, StanfordCorenlpToken token) {
        if (phraseQuotedTokens.contains(token)) {
            features.put("InPhraseQuote", 1);
        }
    }
}
