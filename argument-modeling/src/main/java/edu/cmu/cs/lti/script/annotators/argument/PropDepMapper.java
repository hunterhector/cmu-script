package edu.cmu.cs.lti.script.annotators.argument;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import edu.cmu.cs.lti.pipeline.BasicPipeline;
import edu.cmu.cs.lti.script.type.ComponentAnnotation;
import edu.cmu.cs.lti.script.type.FanseToken;
import edu.cmu.cs.lti.script.type.SemanticRelation;
import edu.cmu.cs.lti.script.type.StanfordCorenlpToken;
import edu.cmu.cs.lti.uima.annotator.AbstractLoggingAnnotator;
import edu.cmu.cs.lti.uima.io.reader.CustomCollectionReaderFactory;
import edu.cmu.cs.lti.uima.util.UimaNlpUtils;
import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.TypeSystemDescriptionFactory;
import org.apache.uima.fit.util.FSCollectionFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSList;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.javatuples.Pair;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * Date: 2020-05-04
 * Time: 23:48
 *
 * @author Zhengzhong Liu
 */
public class PropDepMapper extends AbstractLoggingAnnotator {

    public static final String PARAM_OUTPUT_FILE = "ouputFile";

    @ConfigurationParameter(name = PARAM_OUTPUT_FILE)
    private File outputFile;

    private Table<Pair<String, String>, String, Integer> propDepCounts = HashBasedTable.create();

    @Override
    public void initialize(UimaContext aContext) throws ResourceInitializationException {
        super.initialize(aContext);
    }

    private StanfordCorenlpToken getToken(ComponentAnnotation arg) {
        StanfordCorenlpToken childToken = null;

        for (StanfordCorenlpToken t : JCasUtil.selectCovered(StanfordCorenlpToken.class, arg)) {
            childToken = t;
        }

        return childToken;
    }


    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        for (FanseToken token : JCasUtil.select(jCas, FanseToken.class)) {
            FSList childSemanticFS = token.getChildSemanticRelations();
            if (childSemanticFS == null) {
                continue;
            }

            for (SemanticRelation relation : FSCollectionFactory.create(childSemanticFS,
                    SemanticRelation.class)) {
                StanfordCorenlpToken childToken = getToken(relation.getChild());
                StanfordCorenlpToken headToken = getToken(relation.getHead());
                String propRole = relation.getSemanticAnnotation();

                if (childToken != null && headToken != null) {
                    String dep = UimaNlpUtils.findDirectDep(headToken, childToken);

                    if (dep != null) {
                        Pair<String, String> verbProp = Pair.with(headToken.getLemma(), propRole);

                        if (propDepCounts.contains(verbProp, dep)) {
                            propDepCounts.put(verbProp, dep, propDepCounts.get(verbProp, dep) + 1);
                        } else {
                            propDepCounts.put(verbProp, dep, 1);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void collectionProcessComplete() throws AnalysisEngineProcessException {
        super.collectionProcessComplete();

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));

            for (Pair<String, String> verbProp : propDepCounts.rowKeySet()) {
                String verb = verbProp.getValue0();
                String prop = verbProp.getValue1();

                StringBuilder countStr = new StringBuilder("\t");
                for (Map.Entry<String, Integer> depCount : propDepCounts.row(verbProp).entrySet()) {
                    countStr.append(depCount.getKey());
                    countStr.append(":");
                    countStr.append(depCount.getValue());
                }

                try {
                    writer.write(String.format("%s\t%s\t%s\n", verb, prop, countStr));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] argv) throws UIMAException {
        TypeSystemDescription typeSystemDescription = TypeSystemDescriptionFactory
                .createTypeSystemDescription("TypeSystem");
        String inputDir = argv[0];
        String outputFile = argv[1];

        CollectionReaderDescription reader = CustomCollectionReaderFactory
                .createRecursiveGzippedXmiReader(typeSystemDescription, inputDir);

        AnalysisEngineDescription writer = AnalysisEngineFactory.createEngineDescription(
                PropDepMapper.class, typeSystemDescription,
                PropDepMapper.PARAM_OUTPUT_FILE, outputFile
        );

        new BasicPipeline(reader, true, true, 1, writer).setProgressFreq(5000).run();
    }
}
