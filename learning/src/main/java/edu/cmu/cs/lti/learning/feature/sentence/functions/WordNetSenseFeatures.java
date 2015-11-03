package edu.cmu.cs.lti.learning.feature.sentence.functions;

import edu.cmu.cs.lti.learning.feature.sentence.FeatureUtils;
import edu.cmu.cs.lti.ling.WordNetSearcher;
import edu.cmu.cs.lti.script.type.Dependency;
import edu.cmu.cs.lti.script.type.StanfordCorenlpToken;
import edu.cmu.cs.lti.script.type.WordNetBasedEntity;
import edu.cmu.cs.lti.utils.Configuration;
import gnu.trove.map.TObjectDoubleMap;
import java8.util.function.BiConsumer;
import java8.util.function.Consumer;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;
import org.apache.uima.fit.util.FSCollectionFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSList;
import org.javatuples.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * Date: 9/15/15
 * Time: 1:42 AM
 *
 * @author Zhengzhong Liu
 */
public class WordNetSenseFeatures extends SequenceFeatureWithFocus {
    Set<StanfordCorenlpToken> jobTitleWords;
    WordNetSearcher searcher;

    List<BiConsumer<TObjectDoubleMap<String>, StanfordCorenlpToken>> featureTemplates;

    public WordNetSenseFeatures(Configuration generalConfig, Configuration featureConfig) throws IOException {
        super(generalConfig, featureConfig);
        searcher = new WordNetSearcher(generalConfig.get("edu.cmu.cs.lti.wndict.path"));

        featureTemplates = new ArrayList<BiConsumer<TObjectDoubleMap<String>, StanfordCorenlpToken>>();

        final WordNetSenseFeatures featureExtractor = this;

        for (String templateName : featureConfig.getList(this.getClass().getSimpleName() + ".templates")) {
            if (templateName.equals("JobTitle")) {
                featureTemplates.add(new BiConsumer<TObjectDoubleMap<String>, StanfordCorenlpToken>() {
                    @Override
                    public void accept(TObjectDoubleMap<String> features, StanfordCorenlpToken token) {
                        WordNetSenseFeatures.this.modifyingJobTitle(features, token);
                    }
                });

            } else if (templateName.equals("Synonym")) {
                featureTemplates.add(new BiConsumer<TObjectDoubleMap<String>, StanfordCorenlpToken>() {
                    @Override
                    public void accept(TObjectDoubleMap<String> features, StanfordCorenlpToken token) {
                        WordNetSenseFeatures.this.synonymFeatures(features, token);
                    }
                });

            } else if (templateName.equals("Derivation")) {
                featureTemplates.add(new BiConsumer<TObjectDoubleMap<String>, StanfordCorenlpToken>() {
                    @Override
                    public void accept(TObjectDoubleMap<String> features, StanfordCorenlpToken token) {
                        WordNetSenseFeatures.this.derivationFeatures(features, token);
                    }
                });

            } else {
                logger.warn(String.format("Template [%s] not recognized.", templateName));
            }
        }
    }

    @Override
    public void initDocumentWorkspace(JCas context) {
        jobTitleWords = new HashSet<StanfordCorenlpToken>();
        for (WordNetBasedEntity title : JCasUtil.select(context, WordNetBasedEntity.class)) {
            if (title.getSense().equals("JobTitle")) {
                jobTitleWords.addAll(StreamSupport.stream(JCasUtil.selectCovered(StanfordCorenlpToken.class, title))
                        .collect(Collectors.<StanfordCorenlpToken>toList()));
            }
        }
    }

    @Override
    public void resetWorkspace(JCas aJCas, int begin, int end) {

    }

    @Override
    public void extract(List<StanfordCorenlpToken> sequence, int focus, TObjectDoubleMap<String> features,
                        TObjectDoubleMap<String> featuresNeedForState) {
        if (focus > 0 && focus < sequence.size()) {
            for (BiConsumer<TObjectDoubleMap<String>, StanfordCorenlpToken> featureTemplate : featureTemplates) {
                featureTemplate.accept(features, sequence.get(focus));
            }
        }
    }

    private void modifyingJobTitle(final TObjectDoubleMap<String> features, StanfordCorenlpToken token) {
        FSList headDeps = token.getHeadDependencyRelations();
        if (headDeps != null) {
            StreamSupport.stream(FSCollectionFactory.create(headDeps, Dependency.class)).forEach(new Consumer<Dependency>() {
                @Override
                public void accept(Dependency dep) {
                    if (dep.getDependencyType().endsWith("mod") && jobTitleWords.contains(dep.getHead())) {
                        features.put("TriggerModifyingJobTitle", 1);
                    }
                }
            });
        }
    }

    private void synonymFeatures(TObjectDoubleMap<String> features, StanfordCorenlpToken token) {
        for (String synonym : searcher.getAllSynonyms(token.getLemma().toLowerCase(), token.getPos())) {
            features.put(FeatureUtils.formatFeatureName("TriggerLemmaSynonym", synonym), 1);
        }
    }

    private void derivationFeatures(TObjectDoubleMap<String> features, StanfordCorenlpToken token) {
        Set<String> derivedWordType = new HashSet<String>();

        for (Pair<String, String> der : searcher.getDerivations(token.getLemma().toLowerCase(), token.getPos())) {
            derivedWordType.add(der.getValue0());
        }

        if (!derivedWordType.isEmpty()) {
            derivedWordType.add(token.getLemma().toLowerCase());
        }

        for (String s : derivedWordType) {
            features.put(FeatureUtils.formatFeatureName("TriggerDerivationForm", s), 1);
        }
    }
}
