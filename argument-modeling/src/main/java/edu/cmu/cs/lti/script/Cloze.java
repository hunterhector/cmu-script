package edu.cmu.cs.lti.script;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * Date: 10/23/18
 * Time: 12:11 PM
 *
 * @author Zhengzhong Liu
 */
public class Cloze {
    public static class ClozeDoc {
        public String docid;
        public String text;
        public List<Span> sentences;
        public List<ClozeEventMention> events;
        public List<ClozeEntity> entities;
        public List<CorefCluster> eventCorefClusters;
    }

    public static class Span {
        public int begin;
        public int end;
    }

    public static class CorefCluster {
        public List<Integer> elementIds;
    }

    public static class ClozeEntity {
        public int entityId;
        public double[] entityFeatures;
        public String[] featureNames;
        public String representEntityHead;
        public String entityType;
    }

    public static class ClozeEventMention {
        public String predicate;
        public String predicatePhrase;
        public String verbForm;
        public String node;
        public String context;
        public int sentenceId;
        public int predicateStart;
        public int predicateEnd;
        public String frame;
        public List<ClozeArgument> arguments;
        public String eventType;
        public boolean fromGC;

        public int eventId;

        public static class ClozeArgument {
            public String feName;
            public String dep;
            public String propbankRole;
            public String goldRole;
            public String context;
            public String text;
            public String argumentPhrase;
            public String ner;
            public int entityId;
            public int sentenceId;
            public String node;

            public int argStart;
            public int argEnd;
            public boolean isImplicit;
            public boolean isIncorporated;
            public boolean isSucceeding;

            public String source;
        }
    }
}
