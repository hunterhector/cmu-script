package edu.cmu.cs.lti.script.annotators;

import com.google.common.collect.Table;
import edu.cmu.cs.lti.script.type.*;
import edu.cmu.cs.lti.uima.annotator.AbstractLoggingAnnotator;
import edu.cmu.cs.lti.uima.util.EntityMentionManager;
import edu.cmu.cs.lti.uima.util.UimaAnnotationUtils;
import edu.cmu.cs.lti.uima.util.UimaConvenience;
import edu.cmu.cs.lti.uima.util.UimaNlpUtils;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.FSCollectionFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * Date: 4/1/18
 * Time: 5:14 PM
 *
 * @author Zhengzhong Liu
 */
public class VerbBasedEventDetector extends AbstractLoggingAnnotator {

    private Set<String> ignoredHeadWords;

    @Override
    public void initialize(UimaContext aContext) throws ResourceInitializationException {
        super.initialize(aContext);
        String[] ignoredVerbs = new String[]{"become", "be", "do", "have", "seem", "go", "have", "keep", "argue",
                "claim", "say", "suggest", "tell"};

        ignoredHeadWords = new HashSet<>();
        Collections.addAll(ignoredHeadWords, ignoredVerbs);
    }

    @Override
    public void process(JCas aJCas) throws AnalysisEngineProcessException {
        String lang = aJCas.getDocumentLanguage();
        EntityMentionManager manager = new EntityMentionManager(aJCas);

        // Can handle English. Will also consider unspecified as English.
        if (!lang.equals("en") && !lang.equals("x-unspecified")) {
            return;
        }

        Table<Integer, Integer, EventMention> span2Events = UimaNlpUtils.indexEventMentions(aJCas);

        int eventId = span2Events.size();
        for (StanfordCorenlpToken token : JCasUtil.select(aJCas, StanfordCorenlpToken.class)) {
            if (token.getPos() == null)
                logger.info(String.format("Cannot find pos for token %s [%d:%d] in %s", token.getCoveredText(),
                        token.getBegin(), token.getEnd(), UimaConvenience.getArticleName(aJCas)));

            if (!token.getPos().startsWith("V")) {
                continue;
            } else if (ignoredHeadWords.contains(token.getLemma().toLowerCase())) {
                continue;
            }

            EventMention eventMention;
            if (span2Events.contains(token.getBegin(), token.getEnd())) {
                eventMention = span2Events.get(token.getBegin(), token.getEnd());
            } else {
                eventMention = new EventMention(aJCas, token.getBegin(), token.getEnd());
                UimaAnnotationUtils.finishAnnotation(eventMention, COMPONENT_ID, eventId++, aJCas);
                eventMention.setEventType("Verbal");
            }
            eventMention.setHeadWord(token);

            eventMention.setArguments(
                    FSCollectionFactory.createFSList(aJCas, createArgsFromDependency(aJCas, manager, eventMention)));
        }

        UimaNlpUtils.cleanEntityMentionMetaData(aJCas, new ArrayList<>(JCasUtil.select(aJCas, EntityMention.class)),
                COMPONENT_ID);
    }

    /**
     * @param aJCas        The JCas context.
     * @param manager      An entity mention manager to help search for existing entity mentions.
     * @param eventMention The event mention to extract from.
     * @return
     */
    private List<EventMentionArgumentLink> createArgsFromDependency(
            JCas aJCas, EntityMentionManager manager, EventMention eventMention) {
        Map<EntityMention, EventMentionArgumentLink> existingArgs = UimaNlpUtils.indexArgs(eventMention);
        List<EventMentionArgumentLink> argumentLinks = new ArrayList<>(existingArgs.values());

        Word headToken = eventMention.getHeadWord();
        Map<String, Word> args = getDependents(headToken);

        for (Map.Entry<String, Word> arg : args.entrySet()) {
            String depType = arg.getKey();
            Word argWord = arg.getValue();
            String inferredRole = takeDep(depType);

            if (inferredRole == null) {
                continue;
            }

            if (UimaNlpUtils.isWhWord(argWord)) {
                argWord = UimaNlpUtils.findWhTarget(argWord);

                if (argWord == null) {
                    continue;
                }
            }

            EventMentionArgumentLink argumentLink = UimaNlpUtils.addEventArgument(
                    aJCas, eventMention, manager, existingArgs, argumentLinks,
                    argWord, COMPONENT_ID);

            argumentLink.setDependency(depType);
            if (argumentLink.getArgumentRole() == null) {
                argumentLink.setArgumentRole(inferredRole);
            }
        }

        return argumentLinks;
    }

    private static Map<String, Word> getDependents(Word predicate) {
        Map<String, Word> args = new HashMap<>();
        if (predicate.getChildDependencyRelations() != null) {
            for (StanfordDependencyRelation dep : FSCollectionFactory.create(predicate
                    .getChildDependencyRelations(), StanfordDependencyRelation.class)) {
                args.put(dep.getDependencyType(), dep.getChild());
            }
        }
        return args;
    }

    public static String takeDep(String depType) {
        if (depType.equals("nsubj") || depType.contains("agent")) {
            return "subj";
        } else if (depType.equals("dobj") || depType.equals("nsubjpass")) {
            return "obj";
        } else if (depType.equals("iobj")) {
            return "iobj";
        } else if (depType.startsWith("prep_")) {
            return depType;
        } else if (depType.startsWith("nmod:")) {
            if (depType.equals("nmod:tmod") || depType.equals("nmod:poss")) {
                return depType;
            } else {
                return depType.replace("nmod:", "prep_");
            }
        }
        return null;
    }
}
