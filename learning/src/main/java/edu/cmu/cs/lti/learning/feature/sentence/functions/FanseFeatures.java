package edu.cmu.cs.lti.learning.feature.sentence.functions;

import edu.cmu.cs.lti.learning.feature.sentence.FeatureUtils;
import edu.cmu.cs.lti.script.type.*;
import edu.cmu.cs.lti.uima.util.TokenAlignmentHelper;
import edu.cmu.cs.lti.utils.Configuration;
import gnu.trove.map.TObjectDoubleMap;
import java8.util.function.BiConsumer;
import org.apache.uima.fit.util.FSCollectionFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Created with IntelliJ IDEA.
 * Date: 9/12/15
 * Time: 3:10 PM
 *
 * @author Zhengzhong Liu
 */
public class FanseFeatures extends SequenceFeatureWithFocus {
    private TokenAlignmentHelper align;

    private List<BiConsumer<TObjectDoubleMap<String>, FanseToken>> headTemplates;

    private List<BiConsumer<TObjectDoubleMap<String>, FanseSemanticRelation>> argumentTemplates;

    private boolean loadWordnetSenseTokens;

    private boolean loadNerTokens;

    private Map<Word, String> fanseToken2WordnetType;

    public FanseFeatures(Configuration generalConfig, Configuration featureConfig) {
        super(generalConfig, featureConfig);

        align = new TokenAlignmentHelper();

        headTemplates = new ArrayList<BiConsumer<TObjectDoubleMap<String>, FanseToken>>();
        argumentTemplates = new ArrayList<BiConsumer<TObjectDoubleMap<String>, FanseSemanticRelation>>();

        for (String templateName : featureConfig.getList(this.getClass().getSimpleName() + ".templates")) {
            if (templateName.equals("FanseHeadSense")) {
                headTemplates.add(new BiConsumer<TObjectDoubleMap<String>, FanseToken>() {
                    @Override
                    public void accept(TObjectDoubleMap<String> features, FanseToken fanseToken) {
                        FanseFeatures.this.fanseHeadSenseTemplate(features, fanseToken);
                    }
                });

            } else if (templateName.equals("FanseArgumentRole")) {
                argumentTemplates.add(new BiConsumer<TObjectDoubleMap<String>, FanseSemanticRelation>() {
                    @Override
                    public void accept(TObjectDoubleMap<String> features, FanseSemanticRelation relation) {
                        FanseFeatures.this.fanseArgumentRoles(features, relation);
                    }
                });

            } else if (templateName.equals("FanseArgumentNer")) {
                argumentTemplates.add(new BiConsumer<TObjectDoubleMap<String>, FanseSemanticRelation>() {
                    @Override
                    public void accept(TObjectDoubleMap<String> features, FanseSemanticRelation relation) {
                        FanseFeatures.this.fanseArgumentNer(features, relation);
                    }
                });
                loadNerTokens = true;

            } else if (templateName.equals("FanseArgumentLemma")) {
                argumentTemplates.add(new BiConsumer<TObjectDoubleMap<String>, FanseSemanticRelation>() {
                    @Override
                    public void accept(TObjectDoubleMap<String> features, FanseSemanticRelation relation) {
                        FanseFeatures.this.fanseArgumentLemma(features, relation);
                    }
                });

            } else if (templateName.equals("FanseArgumentWordNetSense")) {
                argumentTemplates.add(new BiConsumer<TObjectDoubleMap<String>, FanseSemanticRelation>() {
                    @Override
                    public void accept(TObjectDoubleMap<String> features, FanseSemanticRelation relation) {
                        FanseFeatures.this.fanseArgumentWordNetSense(features, relation);
                    }
                });
                loadWordnetSenseTokens = true;

            } else {
                logger.warn(String.format("Template [%s] not recognized.", templateName));
            }
        }
    }

    @Override
    public void initDocumentWorkspace(JCas context) {
        align.loadStanford2Fanse(context);
        // Set types to each token for easy feature extraction.
        if (loadNerTokens) {
            for (StanfordEntityMention mention : JCasUtil.select(context, StanfordEntityMention.class)) {
                String entityType = mention.getEntityType();
                for (StanfordCorenlpToken token : JCasUtil.selectCovered(StanfordCorenlpToken.class, mention)) {
                    token.setNerTag(entityType);
                }
            }
        }

        if (loadWordnetSenseTokens) {
            fanseToken2WordnetType = new HashMap<Word, String>();
            for (WordNetBasedEntity anno : JCasUtil.select(context, WordNetBasedEntity.class)) {
                for (FanseToken token : JCasUtil.selectCovered(FanseToken.class, anno)) {
                    fanseToken2WordnetType.put(token, anno.getSense());
                }
            }
        }
    }

    @Override
    public void resetWorkspace(JCas aJCas, int begin, int end) {

    }

    @Override
    public void extract(List<StanfordCorenlpToken> sequence, int focus, final TObjectDoubleMap<String> features,
                        TObjectDoubleMap<String> featuresNeedForState) {
        if (focus > sequence.size() - 1 || focus < 0) {
            return;
        }
        StanfordCorenlpToken token = sequence.get(focus);

        final FanseToken fanseToken = align.getFanseToken(token);

        headTemplates.forEach(new Consumer<BiConsumer<TObjectDoubleMap<String>, FanseToken>>() {
            @Override
            public void accept(BiConsumer<TObjectDoubleMap<String>, FanseToken> t) {
                t.accept(features, fanseToken);
            }
        });

        FSList childRelations = token.getChildSemanticRelations();

        if (childRelations != null) {
            for (final FanseSemanticRelation r : FSCollectionFactory.create(childRelations, FanseSemanticRelation
                    .class)) {
                argumentTemplates.forEach(new Consumer<BiConsumer<TObjectDoubleMap<String>, FanseSemanticRelation>>() {
                    @Override
                    public void accept(BiConsumer<TObjectDoubleMap<String>, FanseSemanticRelation> t) {
                        t.accept(features, r);
                    }
                });
            }
        }
    }

    private void fanseHeadLemmaTemplate(TObjectDoubleMap<String> features, FanseToken token) {
        features.put(FeatureUtils.formatFeatureName("FanseHeadLemma", token.getLemma()), 1);
    }

    private void fanseHeadSenseTemplate(TObjectDoubleMap<String> features, FanseToken token) {
        if (token.getLexicalSense() != null) {
            features.put(FeatureUtils.formatFeatureName("FanseHeadSense", token.getLexicalSense()), 1);
        }
    }

    private void fanseArgumentRoles(TObjectDoubleMap<String> features, FanseSemanticRelation relation) {
        features.put(FeatureUtils.formatFeatureName("FanseArgumentRole", relation.getSemanticAnnotation()), 1);
    }


    private void fanseArgumentLemma(TObjectDoubleMap<String> features, FanseSemanticRelation relation) {
        features.put(FeatureUtils.formatFeatureName("FanseArgumentLemma", relation.getChild().getLemma()), 1);
    }

    private void fanseArgumentNer(TObjectDoubleMap<String> features, FanseSemanticRelation relation) {
        features.put(FeatureUtils.formatFeatureName("FanseArgumentNer", relation.getChild().getNerTag()), 1);
    }

    private void fanseArgumentWordNetSense(TObjectDoubleMap<String> features, FanseSemanticRelation relation) {
        Word child = relation.getChild();
        if (fanseToken2WordnetType.containsKey(child)) {
            features.put(FeatureUtils.formatFeatureName("FanseArgumentWordNetSense",
                    fanseToken2WordnetType.get(child)), 1);
        }
    }
}
