package edu.cmu.cs.lti.script.annotators.writer;

import com.google.common.collect.ArrayListMultimap;
import com.google.gson.Gson;
import edu.cmu.cs.lti.collection_reader.JsonEventDataReader;
import edu.cmu.cs.lti.script.Cloze;
import edu.cmu.cs.lti.script.Cloze.ClozeDoc;
import edu.cmu.cs.lti.script.Cloze.ClozeEntity;
import edu.cmu.cs.lti.script.Cloze.ClozeEventMention;
import edu.cmu.cs.lti.script.Cloze.CorefCluster;
import edu.cmu.cs.lti.script.type.*;
import edu.cmu.cs.lti.script.utils.ImplicitFeaturesExtractor;
import edu.cmu.cs.lti.uima.annotator.AbstractLoggingAnnotator;
import edu.cmu.cs.lti.uima.util.UimaAnnotationUtils;
import edu.cmu.cs.lti.uima.util.UimaConvenience;
import edu.cmu.cs.lti.uima.util.UimaNlpUtils;
import edu.cmu.cs.lti.utils.DebugUtils;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.apache.commons.io.FileUtils;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.FSCollectionFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSList;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.GZIPOutputStream;


/**
 * Given dependency parses and coreference chains, create argument.
 * <p>
 * This works for Propbank Style stuff at this moment (Propbank and Nombank).
 * cloze tasks.
 * Date: 3/24/18
 * Time: 4:32 PM
 *
 * @author Zhengzhong Liu
 */
public class ArgumentClozeTaskWriter extends AbstractLoggingAnnotator {
    public static final String PARAM_OUTPUT_FILE = "outputFile";
    @ConfigurationParameter(name = PARAM_OUTPUT_FILE)
    private String outputFile;

    public static final String PARAM_FRAME_FORMALISM = "frameFormalism";
    @ConfigurationParameter(name = PARAM_FRAME_FORMALISM)
    private String frameFormalism;

    public static final String PARAM_USE_GOLD_FRAME = "useGoldFrame";
    @ConfigurationParameter(name = PARAM_USE_GOLD_FRAME, defaultValue = "false")
    private boolean useGoldFrame;

    public static final String PARAM_CONTEXT_WINDOW = "contextWindow";
    @ConfigurationParameter(name = PARAM_CONTEXT_WINDOW, defaultValue = "5")
    private int contextWindowSize;

    public static final String PARAM_FRAME_MAPPINGS = "frameMappings";
    @ConfigurationParameter(name = PARAM_FRAME_MAPPINGS, mandatory = false)
    private File frameMappingFile;

    public static final String PARAM_ADD_EVENT_COREF = "addEventCoref";
    @ConfigurationParameter(name = PARAM_ADD_EVENT_COREF, defaultValue = "false")
    private boolean addEventCoref;

    public static final String PARAM_USE_NOMBANK_DEP_MAP = "useNombankDepMap";
    @ConfigurationParameter(name = PARAM_USE_NOMBANK_DEP_MAP, defaultValue = "true")
    private boolean useNomBankDepMap;

    private OutputStreamWriter writer;

    private Gson gson = new Gson();

    private Map<String, String> verbFormMap;
    private Map<String, String> nombankBaseFormMap;


    private TObjectIntMap<String> counters;

    @Override
    public void initialize(UimaContext aContext) throws ResourceInitializationException {
        super.initialize(aContext);
        try {
            writer = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(outputFile + ".gz")));
        } catch (IOException e) {
            throw new ResourceInitializationException(e);
        }

        verbFormMap = new HashMap<>();

        counters = new TObjectIntHashMap<>();

        if (useNomBankDepMap) {
            logger.info("Loading nombank dependency map.");
            URL url = this.getClass().getResource("/nombankArgMap.tsv");
            try {
                List<String> headers = new ArrayList<>();

                Files.lines(Paths.get(url.toURI())).forEach(
                        line -> {
                            line = line.trim();
                            if (line.startsWith("#")) {
                                String[] parts = line.substring(1).split("\t");
                                headers.addAll(Arrays.asList(parts).subList(2, parts.length));
                            } else if (headers.size() > 0) {
                                String[] parts = line.split("\t");

                                String nomForm = parts[0];
                                String verbalForm = parts[1];

                                for (int i = 2; i < parts.length; i++) {
                                    String depName = parts[i];
                                    String argName = headers.get(i - 2);
                                }

                                verbFormMap.put(nomForm, verbalForm);
                            }
                        }
                );
            } catch (IOException | URISyntaxException e) {
                e.printStackTrace();
            }
        }

        nombankBaseFormMap = new HashMap<>();
        try {
            // This file is used to convert noun predicates to their base form.
            // https://nlp.cs.nyu.edu/meyers/nombank/nombank-specs-2007.pdf
            Files.lines(Paths.get(this.getClass().getResource("/nombank-morph.dict.1.0").toURI())).forEach(
                    line -> {
                        String[] forms = line.trim().split(" ");
                        for (int i = 1; i < forms.length; i++) {
                            nombankBaseFormMap.put(forms[i], forms[0]);
                        }
                    }
            );
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
    }

    private void setArgumentDepType(EventMention mention, Collection<EventMentionArgumentLink> argumentLinks) {
        Map<Word, String> eventDeps = UimaNlpUtils.getDepChildren(mention.getHeadWord());

        for (EventMentionArgumentLink argumentLink : argumentLinks) {
            Word argHead = argumentLink.getArgument().getHead();
            if (eventDeps.containsKey(argHead)) {
                argumentLink.setDependency(eventDeps.get(argHead));
            }
        }
    }

    private void addCoref(JCas aJCas, ClozeDoc doc, TObjectIntMap<EventMention> eid2Event) {
        doc.eventCorefClusters = new ArrayList<>();

        for (Event event : JCasUtil.select(aJCas, Event.class)) {
            CorefCluster cluster = new CorefCluster();
            cluster.elementIds = new ArrayList<>();

            for (EventMention eventMention : FSCollectionFactory.create(event.getEventMentions(), EventMention.class)) {
                if (eid2Event.containsKey(eventMention)) {
                    cluster.elementIds.add(eid2Event.get(eventMention));
                }
            }
            doc.eventCorefClusters.add(cluster);
        }
    }

    @Override
    public void process(JCas aJCas) throws AnalysisEngineProcessException {
        // Assign IDs.
        int id = 0;
        for (Entity entity : JCasUtil.select(aJCas, Entity.class)) {
            entity.setId(String.valueOf(id));
            entity.setIndex(id);
            id++;
        }

        ClozeDoc doc = new ClozeDoc();
        doc.events = new ArrayList<>();
        doc.docid = UimaConvenience.getArticleName(aJCas);
        doc.sentences = new ArrayList<>();
        doc.text = aJCas.getDocumentText();

        Collection<StanfordCorenlpToken> allTokens = JCasUtil.select(aJCas, StanfordCorenlpToken.class);
        String[] lemmas = new String[allTokens.size()];
        int tIndex = 0;
        for (StanfordCorenlpToken token : allTokens) {
            lemmas[tIndex] = token.getLemma().toLowerCase();
            token.setIndex(tIndex);
            tIndex++;
        }

        List<StanfordCorenlpSentence> sentences = new ArrayList<>(JCasUtil.select(aJCas,
                StanfordCorenlpSentence.class));


        Map<EntityMention, StanfordCorenlpSentence> entitySentences = new HashMap<>();
        Map<EventMention, StanfordCorenlpSentence> eventSentences = new HashMap<>();

        for (int i = 0; i < sentences.size(); i++) {
            StanfordCorenlpSentence sentence = sentences.get(i);
            sentence.setIndex(i);
            for (EntityMention entityMention : JCasUtil.selectCovered(EntityMention.class, sentence)) {
                entitySentences.put(entityMention, sentence);
            }

            for (EventMention eventMention : JCasUtil.selectCovered(EventMention.class, sentence)) {
                eventSentences.put(eventMention, sentence);
            }

            Cloze.Span sentSpan = new Cloze.Span();
            sentSpan.begin = sentence.getBegin();
            sentSpan.end = sentence.getEnd();
            doc.sentences.add(sentSpan);
        }

        ArrayListMultimap<EntityMention, ClozeEventMention.ClozeArgument> argumentMap = ArrayListMultimap.create();
        TObjectIntMap<EventMention> eid2Event = new TObjectIntHashMap<>();

        int eventId = 0;
        for (EventMention eventMention : JCasUtil.select(aJCas, EventMention.class)) {
            if (eventMention.getHeadWord() == null) {
                eventMention.setHeadWord(UimaNlpUtils.findHeadFromStanfordAnnotation(eventMention));
            }

            if (!eventSentences.containsKey(eventMention)) {
                logger.info(String.format("No sentence found for in event %s in doc %s", eventMention.getCoveredText(),
                        UimaConvenience.getDocId(aJCas)));
            }

            ClozeEventMention ce = new ClozeEventMention();
            ce.sentenceId = eventSentences.get(eventMention).getIndex();

            List<Word> complements = new ArrayList<>();

            String frame = null;
            if (useGoldFrame) {
                if (!eventMention.getEventType().equals("Verbal")) {
                    frame = eventMention.getEventType();
                }
            } else {
                frame = eventMention.getFrameName();
            }

            if (frame == null) {
                frame = "NA";
            }

            String predicate_context = getContext(lemmas, (StanfordCorenlpToken) eventMention.getHeadWord());

            ce.predicate = UimaNlpUtils.getPredicate(eventMention.getHeadWord(), complements, false);
            ce.predicatePhrase = onlySpace(eventMention.getCoveredText());
            ce.context = predicate_context;
            ce.predicateStart = eventMention.getBegin();
            ce.predicateEnd = eventMention.getEnd();
            ce.frame = frame;
            ce.eventType = eventMention.getEventType();
            ce.eventId = eventId++;
            ce.node = UimaAnnotationUtils.readMeta(eventMention).get("node");
            ce.fromGC = Boolean.valueOf(UimaAnnotationUtils.readMeta(eventMention).get("from_gc"));

            if (ce.fromGC) {
                counters.adjustOrPutValue("GC predicates", 1, 1);
            }

            String predicateBase = eventMention.getHeadWord().getLemma().toLowerCase();
            if (nombankBaseFormMap.containsKey(predicateBase)) {
                // Convert variants to the base form, e.g. small-investor -> investor
                predicateBase = nombankBaseFormMap.get(predicateBase);
            }

            if (verbFormMap.containsKey(predicateBase)) {
                // Nom form to vorb form, e.g. investor -> invest
                ce.verbForm = verbFormMap.get(predicateBase);
            }

            eid2Event.put(eventMention, ce.eventId);

            FSList argsFS = eventMention.getArguments();
            Collection<EventMentionArgumentLink> argLinks = argsFS == null ? new ArrayList<>() :
                    FSCollectionFactory.create(argsFS, EventMentionArgumentLink.class);

            // Make sure we know the dependency types of the arguments.
            setArgumentDepType(eventMention, argLinks);

            List<ClozeEventMention.ClozeArgument> clozeArguments = new ArrayList<>();

            boolean hasImplicit = false;
            for (EventMentionArgumentLink argLink : argLinks) {
                ClozeEventMention.ClozeArgument ca = new ClozeEventMention.ClozeArgument();

                String pbRole = argLink.getPropbankRoleName();
                if (pbRole == null) {
                    pbRole = "NA";
                }

                String fe = argLink.getFrameElementName();
                if (fe == null) {
                    fe = "NA";
                }

                EntityMention ent = argLink.getArgument();
                Word argHead = ent.getHead();

                String argText = ent.getHead().getLemma();
                argText = onlySpace(argText);

                String argumentContext = getContext(lemmas, (StanfordCorenlpToken) argHead);

                ca.propbankRole = pbRole;
                ca.feName = fe;
                ca.goldRole = argLink.getArgumentRole();


                ca.context = argumentContext;
                ca.node = UimaAnnotationUtils.readMeta(argLink).get("node");

                if (!entitySentences.containsKey(ent)) {
                    logger.info(String.format("No sentence found for entity [%s] (by %s) in doc %s",
                            ent.getCoveredText(), ent.getComponentId(), UimaConvenience.getDocId(aJCas)));
                }

                ca.sentenceId = entitySentences.get(ent).getIndex();

                String entType = ent.getEntityType();
                if (entType != null && !entType.equals("ARG_ENT") && !entType.equals("Entity")) {
                    ca.ner = entType;
                }

                String dep = argLink.getDependency();
                ca.dep = dep == null ? "NA" : dep;

                ca.entityId = ent.getReferingEntity().getIndex();
                ca.text = onlySpace(argText);
                ca.argumentPhrase = onlySpace(ent.getCoveredText());

                ca.argStart = ent.getBegin();
                ca.argEnd = ent.getEnd();

                Map<String, String> argMeta = UimaAnnotationUtils.readMeta(argLink);
                ca.isImplicit = Boolean.valueOf(argMeta.get("implicit"));
                ca.isIncorporated = Boolean.valueOf(argMeta.get("incorporated"));
                ca.isSucceeding = Boolean.valueOf(argMeta.get("succeeding"));

                if (argLink.getComponentId().equals(JsonEventDataReader.class.getSimpleName())) {
                    ca.source = "gold";
                    if (eventMention.getEventType().equals("NOMBANK")) {
                        counters.adjustOrPutValue("Arguments from NomBank", 1, 1);
                    } else if (eventMention.getEventType().equals("PROPBANK")) {
                        counters.adjustOrPutValue("Arguments from PropBank", 1, 1);
                    }
                } else {
                    ca.source = "automatic";
                    counters.adjustOrPutValue("Arguments from parsers", 1, 1);
                }

                if (ca.isImplicit) {
                    counters.adjustOrPutValue("Implicit Arguments", 1, 1);
                    hasImplicit = true;
                }

                clozeArguments.add(ca);
                argumentMap.put(ent, ca);
            }

            if (hasImplicit) {
                String predText = eventMention.getHeadWord().getLemma().toLowerCase();
                if (predText.equals("small-investor")) {
                    predText = "investor";
                }
            }

            ce.arguments = clozeArguments;

            doc.events.add(ce);
        }
//        }

        if (addEventCoref) {
            addCoref(aJCas, doc, eid2Event);
        }

        Map<Entity, SortedMap<String, Double>> implicitFeatures = ImplicitFeaturesExtractor.getArgumentFeatures(aJCas);


        List<ClozeEntity> clozeEntities = new ArrayList<>();

        for (Map.Entry<Entity, SortedMap<String, Double>> entityFeatures : implicitFeatures.entrySet()) {
            Entity entity = entityFeatures.getKey();
            Map<String, Double> features = entityFeatures.getValue();

            ClozeEntity clozeEntity = new ClozeEntity();
            clozeEntity.representEntityHead = onlySpace(entity.getRepresentativeMention()
                    .getHead().getLemma().toLowerCase());

            double[] featureArray = new double[features.size()];
            String[] featureNameArray = new String[features.size()];
            int index = 0;
            for (Map.Entry<String, Double> feature : features.entrySet()) {
                featureArray[index] = feature.getValue();
                featureNameArray[index] = feature.getKey();
                index++;
            }
            clozeEntity.entityFeatures = featureArray;
            clozeEntity.featureNames = featureNameArray;
            clozeEntity.entityId = entity.getIndex();
            if (entity.getEntityType() != null) {
                clozeEntity.entityType = entity.getEntityType();
            }

            clozeEntities.add(clozeEntity);
        }

        doc.entities = clozeEntities;

        try {
            writer.write(gson.toJson(doc) + "\n");
        } catch (IOException e) {
            throw new AnalysisEngineProcessException(e);
        }
    }

    private String onlySpace(String text) {
        return text.trim().replaceAll("\t", " ").replaceAll("\n", " ");
    }

    private String getContext(String[] lemmas, StanfordCorenlpToken token) {
        int index = token.getIndex();

        int left = index - contextWindowSize;
        if (left < 0) {
            left = 0;
        }

        int right = index + contextWindowSize;
        if (right > lemmas.length - 1) {
            right = lemmas.length - 1;
        }

        StringBuilder sb = new StringBuilder();

        for (int i = left; i < index; i++) {
            sb.append(lemmas[i]).append(" ");
        }

        // Indicate the context center.
        sb.append("___");

        for (int i = index + 1; i <= right; i++) {
            sb.append(" ").append(lemmas[i]);
        }

        return onlySpace(sb.toString());
    }

    @Override
    public void collectionProcessComplete() throws AnalysisEngineProcessException {
        super.collectionProcessComplete();

        logger.info("Statistics for the corpus:");
        counters.forEachEntry((s, i) -> {
            logger.info(String.format("%s : %d", s, i));
            return true;
        });

        try {
            writer.close();
        } catch (IOException e) {
            throw new AnalysisEngineProcessException(e);
        }
    }
}
