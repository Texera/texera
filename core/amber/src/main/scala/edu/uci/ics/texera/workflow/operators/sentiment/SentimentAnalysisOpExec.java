package edu.uci.ics.texera.workflow.operators.sentiment;


import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;
import edu.uci.ics.amber.engine.common.tuple.amber.TupleLike;
import edu.uci.ics.texera.workflow.common.operators.map.MapOpExec;
import edu.uci.ics.texera.workflow.common.tuple.Tuple;
import org.apache.avro.generic.GenericData;
import org.apache.commons.lang.ArrayUtils;
import scala.Function1;

import java.io.Serializable;
import java.util.*;

public class SentimentAnalysisOpExec extends MapOpExec {
    private final String attributeName;
    private final StanfordCoreNLPWrapper coreNlp;

    public SentimentAnalysisOpExec(String attributeName) {
        this.attributeName = attributeName;
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, parse, sentiment");
        coreNlp = new StanfordCoreNLPWrapper(props);
        this.setMapFunc((Function1<Tuple, TupleLike> & Serializable) this::sentimentAnalysis);
    }

    public TupleLike sentimentAnalysis(Tuple t) {
        String text = t.getField(attributeName).toString();
        Annotation documentAnnotation = new Annotation(text);
        coreNlp.get().annotate(documentAnnotation);

        Optional<CoreMap> longestSentence = documentAnnotation.get(CoreAnnotations.SentencesAnnotation.class)
                .stream()
                .max(Comparator.comparingInt(s -> s.toString().length()));

        int sentimentScore = longestSentence
                .map(sentence -> {
                    Tree tree = sentence.get(SentimentCoreAnnotations.SentimentAnnotatedTree.class);
                    return RNNCoreAnnotations.getPredictedClass(tree);
                })
                .orElse(0);

        int normalizedSentimentScore = Integer.compare(sentimentScore, 2);
        java.util.List<Object> tupleFields = Arrays.asList(t.fields());
        tupleFields.add(normalizedSentimentScore);

        return TupleLike.apply(tupleFields);
    }


}
