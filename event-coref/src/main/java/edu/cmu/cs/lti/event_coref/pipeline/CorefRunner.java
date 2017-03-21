package edu.cmu.cs.lti.event_coref.pipeline;

import edu.cmu.cs.lti.utils.Configuration;
import org.apache.uima.UIMAException;

import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * Date: 9/30/15
 * Time: 6:43 AM
 *
 * @author Zhengzhong Liu
 */
public class CorefRunner {
    public static void main(String[] argv) throws UIMAException, IOException {
        if (argv.length < 1) {
            System.err.println("Please provide one argument for the settings file.");
        }

        Configuration taskConfig = new Configuration(argv[0]);
        Configuration commonConfig = new Configuration("settings/common.properties");

        String modelPath = commonConfig.get("edu.cmu.cs.lti.model.dir");
        String typeSystemName = commonConfig.get("edu.cmu.cs.lti.event.typesystem");

        CorefPipeline pipeline = new CorefPipeline(typeSystemName, modelPath, taskConfig);

        String preprocesseBase = "preprocessed";
        String mentionBase = "mention_annotated";

        String testDir = taskConfig.get("edu.cmu.cs.lti.coref.test.dir");

//        pipeline.prepareEventMentions(testDir, preprocesseBase);
//        pipeline.extra(testDir, preprocesseBase, mentionBase);

//        String finalModel = pipeline.trainFinal(preprocesseBase);

        // TODO a rough way of running all iterations.
        for (int i = 15; i <= 15; i++) {
            pipeline.testCoref(taskConfig, testDir, mentionBase,
                    "../models/latent_tree_coref_kbp/all_iter" + i, "test_out",
                    "_coref_model_" + String.valueOf(i));
        }
    }
}
