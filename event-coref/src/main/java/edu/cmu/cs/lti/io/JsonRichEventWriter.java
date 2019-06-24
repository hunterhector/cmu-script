package edu.cmu.cs.lti.io;

import com.google.gson.Gson;
import edu.cmu.cs.lti.annotators.FanseAnnotator;
import edu.cmu.cs.lti.emd.annotators.CrfMentionTypeAnnotator;
import edu.cmu.cs.lti.model.Span;
import edu.cmu.cs.lti.model.UimaConst;
import edu.cmu.cs.lti.script.annotators.FrameBasedEventDetector;
import edu.cmu.cs.lti.script.annotators.VerbBasedEventDetector;
import edu.cmu.cs.lti.script.type.*;
import edu.cmu.cs.lti.uima.annotator.AbstractLoggingAnnotator;
import edu.cmu.cs.lti.uima.util.UimaConvenience;
import edu.cmu.cs.lti.utils.DispatchReader;
import org.apache.commons.io.FileUtils;
import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.TypeSystemDescriptionFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.fit.util.FSCollectionFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSList;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeSystemDescription;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Rich content from events requires slightly richer writer.
 * Date: 4/25/18
 * Time: 9:25 AM
 *
 * @author Zhengzhong Liu
 */
public class JsonRichEventWriter extends AbstractLoggingAnnotator {
    public static final String PARAM_OUTPUT_DIR = "outputFile";

    @ConfigurationParameter(name = PARAM_OUTPUT_DIR)
    private File outputDir;

    private int objectIndex;
    private ArrayList<Word> allWords;

    private Map<String, Double> confidenceScale;
    private double defaultScale = 0.5;

    @Override
    public void initialize(UimaContext aContext) throws ResourceInitializationException {
        super.initialize(aContext);
        logger.info("Writing JSON Rich events to : " + outputDir);
        edu.cmu.cs.lti.utils.FileUtils.ensureDirectory(outputDir);

        confidenceScale = new HashMap<>();
        confidenceScale.put(
                CrfMentionTypeAnnotator.class.getSimpleName(), 0.6
        );
        confidenceScale.put(
                FrameBasedEventDetector.class.getSimpleName(), 0.64
        );
        confidenceScale.put(
                VerbBasedEventDetector.class.getSimpleName(), 0.6
        );
        confidenceScale.put(
                FanseAnnotator.class.getSimpleName(), 0.4
        );
    }

    @Override
    public void process(JCas aJCas) throws AnalysisEngineProcessException {
        Gson gson = new Gson();

        objectIndex = 0;

        File outputFile = new File(outputDir, UimaConvenience.getArticleName(aJCas) + ".json");
        try {
            FileUtils.write(outputFile, gson.toJson(buildJson(aJCas)) + "\n");
        } catch (IOException e) {
            throw new AnalysisEngineProcessException(e);
        }
    }


    private JsonEntityMention createEntity(ComponentAnnotation anno, Word head, String entityForm) {
        JsonEntityMention jsonEnt = new JsonEntityMention(objectIndex++, anno);
        jsonEnt.component = anno.getComponentId();

        jsonEnt.headWord = new JsonWord(objectIndex++, head);
        jsonEnt.entityForm = entityForm;

        if (anno instanceof EntityMention) {
            String type = ((EntityMention) anno).getEntityType();
            if (type != null) {
                jsonEnt.type = type;
            }
        }

        Map<String, Word> entityModifier = findTokenModifier(head);
        if (entityModifier != null) {
            if (entityModifier.containsKey("Negation")) {
                jsonEnt.negationWord = entityModifier.get("Negation").getCoveredText();
            }
        }

        return jsonEnt;
    }

    private Map<String, Word> findTokenModifier(Word token) {
        Map<String, Word> modifiers = new HashMap<>();

        FSList relationsFS = token.getChildSemanticRelations();
        if (relationsFS != null) {
            for (SemanticRelation relation : FSCollectionFactory.create(relationsFS, SemanticRelation.class)) {
                String pbRole = relation.getPropbankRoleName();
                Word childHead = relation.getChild().getHead();
                if (pbRole != null && childHead != null) {
                    if (pbRole.equals("ARGM-NEG")) {
                        modifiers.put("Negation", childHead);
                    } else if (pbRole.equals("ARGM-MOD")) {
                        modifiers.put("Modal", childHead);
                    }
                }
            }
        }

        return modifiers;
    }

    private String getEntityFormFromHead(Word head) {
        String pos = head.getPos();
        if (pos.startsWith("PR")) {
            return "pronominal";
        } else if (pos.startsWith("NNP")) {
            return "named";
        } else if (pos.startsWith("N")) {
            return "nominal";
        } else {
            return null;
        }
    }

    private List<Integer> getEventMentionExtent(EventMention mention) {
        List<Integer> just_span = new ArrayList<>();

        int jus_begin = mention.getBegin();
        int jus_end = mention.getEnd();
        FSList argsFs = mention.getArguments();

        if (argsFs != null) {
            for (EventMentionArgumentLink link : FSCollectionFactory.create(argsFs, EventMentionArgumentLink.class)) {
                int b = link.getArgument().getBegin();
                int e = link.getArgument().getEnd();
                if (b <= jus_begin) {
                    jus_begin = b;
                }
                if (e >= jus_end) {
                    jus_end = e;
                }
            }
        }

        just_span.add(jus_begin);
        just_span.add(jus_end);

        return just_span;
    }

    private List<Integer> getArumengJustification(EventMention mention, EntityMention argument) {
        int jus_begin = Math.min(mention.getBegin(), argument.getBegin());
        int jus_end = Math.max(mention.getEnd(), argument.getEnd());

        List<Integer> jus = new ArrayList<>();
        jus.add(jus_begin);
        jus.add(jus_end);

        return jus;
    }

    private Document buildJson(JCas aJCas) {
        String docid = UimaConvenience.getArticleName(aJCas);

        Document doc = new Document();
        doc.docid = docid;
        doc.eventMentions = new ArrayList<>();

        doc.text = aJCas.getDocumentText();

        Map<Span, JsonEventMention> evmMap = new HashMap<>();
        Map<Span, JsonEntityMention> jsonEntMap = new HashMap<>();

        int wordId = 0;
        allWords = new ArrayList<>();
        for (Word word : JCasUtil.select(aJCas, Word.class)) {
            if (word.getComponentId().equals(UimaConst.goldComponentName)) {
                word.setIndex(wordId++);
                allWords.add(word);
            }
        }

        // Adding entity mentions.
        for (EntityMention mention : JCasUtil.select(aJCas, EntityMention.class)) {
            Word head = mention.getHead();

            if (head == null) {
                logger.warn(String.format("Cannot find head word for entity mention [%s][%d:%d] (%s).", mention
                        .getCoveredText(), mention.getBegin(), mention.getEnd(), mention.getComponentId()));
                continue;
            }

            Span headSpan = Span.of(head.getBegin(), head.getEnd());

            String namedType = mention.getEntityType();

            String entityForm;
            if (namedType != null) {
                if (namedType.equals("DATE") || namedType.equals("NUMBER")) {
                    entityForm = null;
                } else {
                    entityForm = "named";
                }
            } else {
                entityForm = getEntityFormFromHead(head);
            }

            JsonEntityMention jsonEnt = createEntity(mention, head, entityForm);
            jsonEntMap.put(headSpan, jsonEnt);
        }

        // Adding event mentions.
        for (EventMention mention : JCasUtil.select(aJCas, EventMention.class)) {
            JsonEventMention jsonEvm = new JsonEventMention(objectIndex++, mention);
            jsonEvm.type = mention.getEventType();
            jsonEvm.realis = mention.getRealisType();
            jsonEvm.component = mention.getComponentId();

            double confidence = mention.getEventTypeConfidence();
            if (confidence == 0) {
                confidence = 1;
            }

            if (confidenceScale.containsKey(mention.getComponentId())) {
                confidence *= confidenceScale.get(mention.getComponentId());
            } else {
                confidence *= defaultScale;
            }

            jsonEvm.score = confidence;

            doc.eventMentions.add(jsonEvm);

            Word headword = mention.getHeadWord();

            if (headword == null) {
                logger.warn(String.format("Cannot find head word for event mention [%s][%d:%d].", mention
                        .getCoveredText(), mention.getBegin(), mention.getEnd()));
                continue;
            }

            if (mention.getFrameName() == null) {
                jsonEvm.frame = headword.getFrameName();
            } else {
                jsonEvm.frame = mention.getFrameName();
            }

            jsonEvm.headWord = new JsonWord(objectIndex++, headword);
            jsonEvm.arguments = new ArrayList<>();
            jsonEvm.headLemma = headword.getLemma();
            List<Integer> evmExtent = getEventMentionExtent(mention);
            jsonEvm.extentText = aJCas.getDocumentText().substring(evmExtent.get(0), evmExtent.get(1));
            jsonEvm.extentSpan = evmExtent;

            Map<String, Word> evmModifier = findTokenModifier(headword);
            if (evmModifier != null) {
                if (evmModifier.containsKey("Negation")) {
                    jsonEvm.negationWord = evmModifier.get("Negation").getCoveredText();
                }
                if (evmModifier.containsKey("Modal")) {
                    jsonEvm.modalWord = evmModifier.get("Modal").getCoveredText();
                }
            }

            FSList eventArgumentsFs = mention.getArguments();

            if (eventArgumentsFs != null) {
                for (EventMentionArgumentLink argumentLink : FSCollectionFactory.create(eventArgumentsFs,
                        EventMentionArgumentLink.class)) {
                    EntityMention arg = argumentLink.getArgument();

                    Span argHeadSpan;
                    if (arg.getHead() == null){
                        int wordsUnder = JCasUtil.selectCovered(StanfordCorenlpToken.class, arg).size();
                        logger.warn(String.format("Cannot find head for argument: %s [%d:%d] at document %s, " +
                                "it covers %d words.", arg.getCoveredText(), arg.getBegin(), arg.getEnd(),
                                UimaConvenience.getArticleName(aJCas), wordsUnder));

                        //                        // Cannot find head, use the full span.
//                        argHeadSpan = Span.of(arg.getBegin(), arg.getEnd());

                        continue;
                    }else {
                        argHeadSpan = Span.of(arg.getHead().getBegin(), arg.getHead().getEnd());
                    }

                    JsonEntityMention jsonEnt;
                    if (jsonEntMap.containsKey(argHeadSpan)) {
                        jsonEnt = jsonEntMap.get(argHeadSpan);
                    } else {
                        jsonEnt = createEntity(arg, arg.getHead(), getEntityFormFromHead(arg.getHead()));
                        jsonEntMap.put(argHeadSpan, jsonEnt);
                    }

                    JsonArgument jsonArg = new JsonArgument();
                    jsonArg.roles = new ArrayList<>();
                    jsonArg.entityId = jsonEnt.id;
                    jsonArg.eventId = jsonEvm.id;

                    if (argumentLink.getDependency() != null) {
                        jsonArg.dependency = argumentLink.getDependency();
                    }else{
                        jsonArg.dependency = "NA";
                    }

                    List<Integer> argJus = getArumengJustification(mention, arg);
                    jsonArg.justification = aJCas.getDocumentText().substring(argJus.get(0), argJus.get(1));
                    jsonArg.justificationSpan = argJus;

                    String pbName = argumentLink.getPropbankRoleName();
                    String fnName = argumentLink.getFrameElementName();

                    if (pbName != null) {
                        jsonArg.roles.add("pb:" + pbName);
                    }

                    if (fnName != null) {
                        jsonArg.roles.add("fn:" + fnName);
                    }
                    jsonArg.component = arg.getComponentId();

                    jsonArg.score = confidenceScale.getOrDefault(argumentLink.getComponentId(), defaultScale);

                    jsonEvm.arguments.add(jsonArg);
                }
            }

            Span evmSpan = Span.of(mention.getBegin(), mention.getEnd());
            evmMap.put(evmSpan, jsonEvm);
        }

        doc.clusters = new ArrayList<>();
        for (Event event : JCasUtil.select(aJCas, Event.class)) {
            if (event.getEventMentions().size() > 1) {
                JsonCluster cluster = new JsonCluster();
                cluster.clusterType = "event_coreference";
                cluster.arguments = new ArrayList<>();

                boolean isFirst = true;
                for (EventMention evm : FSCollectionFactory.create(event.getEventMentions(), EventMention.class)) {
                    Span evmSpan = Span.of(evm.getBegin(), evm.getEnd());
                    JsonEventMention jsonEvm = evmMap.get(evmSpan);
                    cluster.arguments.add(jsonEvm.id);

                    if (isFirst) {
                        cluster.representative = jsonEvm.id;
                    }

                    isFirst = false;
                }
                doc.clusters.add(cluster);
            }
        }

        for (Entity entity : JCasUtil.select(aJCas, Entity.class)) {
            EntityMention represent = entity.getRepresentativeMention();

            if (entity.getEntityMentions().size() > 1) {
                JsonCluster cluster = new JsonCluster();
                cluster.clusterType = "entity_coreference";
                cluster.arguments = new ArrayList<>();

                for (EntityMention ent : FSCollectionFactory.create(entity.getEntityMentions(), EntityMention.class)) {
                    Word head = ent.getHead();
                    Span headSpan = Span.of(head.getBegin(), head.getEnd());
                    if (jsonEntMap.containsKey(headSpan)) {
                        JsonEntityMention jsonEnt = jsonEntMap.get(headSpan);
                        cluster.arguments.add(jsonEnt.id);

                        if (ent == represent) {
                            cluster.representative = jsonEnt.id;
                        }
                    } else {
                        logger.warn(String.format("Entity mention [%s] : [%s] not found at [%s]",
                                ent.getCoveredText(), headSpan, docid));
                    }

                }
                doc.clusters.add(cluster);
            }
        }

        doc.entityMentions = new ArrayList<>();
        doc.entityMentions.addAll(jsonEntMap.values());

        return doc;
    }


    class Document {
        String docid;
        String text;
        List<JsonEventMention> eventMentions;
        List<JsonEntityMention> entityMentions;
        List<JsonCluster> clusters;
    }

    class DiscourseObject {
        int id;
        List<Integer> span;
        List<Integer> tokens;
        String text;

        DiscourseObject(int id, ComponentAnnotation anno, String text) {
            this.id = id;
            this.text = text;
            setSpan(anno);
            setTokens(anno);
        }

        void setSpan(ComponentAnnotation anno) {
            span = Arrays.asList(anno.getBegin(), anno.getEnd());
        }

        void setTokens(ComponentAnnotation anno) {
            List<Word> words = JCasUtil.selectCovered(Word.class, anno);
            if (words.size() == 0) {
                words = JCasUtil.selectCovering(Word.class, anno);
            }
            tokens = new ArrayList<>();
            for (Word word : words) {
                if (word.getComponentId().equals(UimaConst.goldComponentName)) {
                    tokens.add(word.getIndex());
                }
            }
        }
    }

    class JsonEventMention extends DiscourseObject {
        JsonWord headWord;
        String headLemma;

        List<JsonArgument> arguments;

        String type;
        String realis;
        String frame;
        String component;

        String negationWord;
        String modalWord;

        double score;

        String extentText;
        List<Integer> extentSpan;

        JsonEventMention(int id, ComponentAnnotation anno) {
            super(id, anno, anno.getCoveredText());
        }
    }

    class JsonEntityMention extends DiscourseObject {
        JsonWord headWord;
        String type;
        String component;

        String negationWord;

        String entityForm;

        JsonEntityMention(int id, ComponentAnnotation anno) {
            super(id, anno, anno.getCoveredText());
        }
    }

    class JsonWord extends DiscourseObject {
        String lemma;
        String pos;

        JsonWord(int id, Word word) {
            super(id, word, word.getCoveredText());
            lemma = word.getLemma();
            pos = word.getPos();
        }
    }

    class JsonCluster {
        List<Integer> arguments;
        String clusterType;
        int representative;
    }

    class JsonArgument {
        int eventId;
        int entityId;
        List<String> roles;
        String dependency;
        String component;
        double score;

        String justification;
        List<Integer> justificationSpan;
    }


    public static void main(String[] args) throws UIMAException, IOException {
        TypeSystemDescription typeSystemDescription = TypeSystemDescriptionFactory
                .createTypeSystemDescription("TypeSystem");

        String inputPath = args[0];
        String inputType = args[1]; //xmi
        String outputPath = args[2];

        CollectionReaderDescription reader = DispatchReader.getReader(typeSystemDescription, inputPath, inputType,
                null);

        AnalysisEngineDescription writer = AnalysisEngineFactory.createEngineDescription(
                JsonRichEventWriter.class, typeSystemDescription,
                JsonRichEventWriter.PARAM_OUTPUT_DIR, outputPath
        );

        SimplePipeline.runPipeline(reader, writer);
    }
}
