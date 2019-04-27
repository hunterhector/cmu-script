package edu.cmu.cs.lti.salience.annotators;

import edu.cmu.cs.lti.annotators.StanfordCoreNlpAnnotator;
import edu.cmu.cs.lti.collection_reader.AnnotatedNytReader;
import edu.cmu.cs.lti.pipeline.BasicPipeline;
import edu.cmu.cs.lti.salience.utils.SalienceUtils;
import edu.cmu.cs.lti.salience.utils.TextUtils;
import edu.cmu.cs.lti.script.type.Article;
import edu.cmu.cs.lti.script.type.Body;
import edu.cmu.cs.lti.uima.annotator.AbstractLoggingAnnotator;
import edu.cmu.cs.lti.uima.util.UimaConvenience;
import org.apache.commons.io.FileUtils;
import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.collection.metadata.CpeDescriptorException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.factory.TypeSystemDescriptionFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import static edu.cmu.cs.lti.utils.FileUtils.ensureDirectory;

//import edu.cmu.cs.lti.utils.FileUtils;

/**
 * Create text documents that is consistent with the pre-processing steps in the salience task.
 * One can recover the NYT text documents from using this class.
 * <p>
 * Date: 2019-04-12
 * Time: 11:55
 *
 * @author Zhengzhong Liu
 */
public class SalienceDatasetTextOnlyWriter extends AbstractLoggingAnnotator {
    public final static String PARAM_PARENT_OUTPUT_DIR_PATH = "outputDirPath";
    @ConfigurationParameter(name = PARAM_PARENT_OUTPUT_DIR_PATH)
    private String parentOut;

    public final static String PARAM_ABSTRACT_FILES = "abstractFileList";
    @ConfigurationParameter(name = PARAM_ABSTRACT_FILES)
    private File abstractFileLst;

    private Set<String> abstractDocs;

    private File bodyOut;
    private File abstractOut;

    @Override
    public void initialize(UimaContext aContext) throws ResourceInitializationException {
        super.initialize(aContext);

        bodyOut = new File(parentOut, "body");
        abstractOut = new File(parentOut, "abstract");

        ensureDirectory(bodyOut);
        ensureDirectory(abstractOut);

        logger.info("Body will be output at: " + bodyOut);
        logger.info("Abstract will be output at: " + abstractOut);

        try {
            abstractDocs = SalienceUtils.readSplit(abstractFileLst);
        } catch (IOException e) {
            throw new ResourceInitializationException(e);
        }

    }

    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        String articleName = UimaConvenience.getArticleName(jCas);

        if (!abstractDocs.contains(articleName)) {
            return;
        }

        Body body = JCasUtil.selectSingle(jCas, Body.class);
        String bodyText = TextUtils.asTokenized(body);

        JCas abstractView = JCasUtil.getView(jCas, AnnotatedNytReader.ABSTRACT_VIEW_NAME, jCas);
        Article abstractArticle = JCasUtil.selectSingle(abstractView, Article.class);

        String abstractText = TextUtils.asTokenized(abstractArticle);

        try {
            FileUtils.write(new File(bodyOut, UimaConvenience.getArticleName(jCas) + ".txt"), bodyText);
            FileUtils.write(new File(abstractOut, UimaConvenience.getArticleName(jCas) + ".txt"), abstractText);
        } catch (IOException e) {
            throw new AnalysisEngineProcessException(e);
        }
    }


    public static void main(String[] args) throws UIMAException, IOException, CpeDescriptorException, SAXException {
        String nytPath = args[0];
        String textOutput = args[1];
        String fileList = args[2];
        int numThreads = Integer.parseInt(args[3]);

        TypeSystemDescription typeSystemDescription = TypeSystemDescriptionFactory
                .createTypeSystemDescription("TypeSystem");

        CollectionReaderDescription reader = CollectionReaderFactory.createReaderDescription(
                AnnotatedNytReader.class, typeSystemDescription,
                AnnotatedNytReader.PARAM_DATA_PATH, nytPath,
                AnnotatedNytReader.PARAM_EXTENSION, ".tgz",
                AnnotatedNytReader.PARAM_RECURSIVE, true
        );

        AnalysisEngineDescription parser = AnalysisEngineFactory.createEngineDescription(
                StanfordCoreNlpAnnotator.class, typeSystemDescription,
                StanfordCoreNlpAnnotator.PARAM_LANGUAGE, "en",
                StanfordCoreNlpAnnotator.PARAM_SPLIT_ONLY, true,
                StanfordCoreNlpAnnotator.PARAM_KEEP_QUIET, true,
                StanfordCoreNlpAnnotator.PARAM_ADDITIONAL_VIEWS, AnnotatedNytReader.ABSTRACT_VIEW_NAME
        );

        AnalysisEngineDescription textWriter = AnalysisEngineFactory.createEngineDescription(
                SalienceDatasetTextOnlyWriter.class, typeSystemDescription,
                SalienceDatasetTextOnlyWriter.PARAM_PARENT_OUTPUT_DIR_PATH, textOutput,
                SalienceDatasetTextOnlyWriter.PARAM_ABSTRACT_FILES, fileList
        );

        new BasicPipeline(reader, numThreads, parser, textWriter).run();
    }

}
