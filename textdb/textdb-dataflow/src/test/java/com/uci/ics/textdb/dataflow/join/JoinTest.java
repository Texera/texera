package com.uci.ics.textdb.dataflow.join;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import edu.uci.ics.textdb.api.common.Attribute;
import edu.uci.ics.textdb.api.common.FieldType;
import edu.uci.ics.textdb.api.common.IField;
import edu.uci.ics.textdb.api.common.IPredicate;
import edu.uci.ics.textdb.api.common.ITuple;
import edu.uci.ics.textdb.api.common.Schema;
import edu.uci.ics.textdb.api.dataflow.IOperator;
import edu.uci.ics.textdb.api.storage.IDataWriter;
import edu.uci.ics.textdb.common.constants.DataConstants;
import edu.uci.ics.textdb.common.constants.SchemaConstants;
import edu.uci.ics.textdb.common.field.DataTuple;
import edu.uci.ics.textdb.common.field.IntegerField;
import edu.uci.ics.textdb.common.field.ListField;
import edu.uci.ics.textdb.common.field.Span;
import edu.uci.ics.textdb.common.field.StringField;
import edu.uci.ics.textdb.common.field.TextField;
import edu.uci.ics.textdb.dataflow.common.FuzzyTokenPredicate;
import edu.uci.ics.textdb.dataflow.common.JoinPredicate;
import edu.uci.ics.textdb.dataflow.common.KeywordPredicate;
import edu.uci.ics.textdb.dataflow.fuzzytokenmatcher.FuzzyTokenMatcher;
import edu.uci.ics.textdb.dataflow.join.Join;
import edu.uci.ics.textdb.dataflow.keywordmatch.KeywordMatcher;
import edu.uci.ics.textdb.dataflow.utils.TestUtils;
import edu.uci.ics.textdb.storage.DataStore;
import edu.uci.ics.textdb.storage.writer.DataWriter;
import junit.framework.Assert;

/**
 * 
 * @author sripadks
 *
 */
public class JoinTest {
    private KeywordMatcher keywordMatcherOuter;
	private KeywordMatcher keywordMatcherInner;
    private IDataWriter dataWriterForOuter;
    private DataStore dataStoreForOuter;
    private IDataWriter dataWriterForInner;
    private DataStore dataStoreForInner;
    private Analyzer analyzer;
    private Join join;
    List<ITuple> bookTuple1;
    List<ITuple> bookTuple2;
    List<Attribute> attributeList;
	
    // This method sets up some stuff before begining each test.
	@Before
	public void setup() throws Exception {
		analyzer = new StandardAnalyzer();
		
		final Attribute idAttr = new Attribute("id", FieldType.INTEGER);
		final Attribute authorAttr = new Attribute("author", FieldType.STRING);
		final Attribute titleAttr = new Attribute("title", FieldType.STRING);
		final Attribute pagesAttr = new Attribute("numberOfPages", FieldType.INTEGER);
		final Attribute reviewAttr = new Attribute("reviewOfBook", FieldType.TEXT);
		
		final Attribute[] bookAttr = { idAttr, authorAttr, titleAttr, pagesAttr, 
				reviewAttr};
		final Schema bookSchema = new Schema(bookAttr);
		
		IField[] book1 = { new IntegerField(52), new StringField("Mary Roach"), 
				   new StringField("Grunt: The Curious Science of Humans at War"), 
				   new IntegerField(288), new TextField("It takes a special kind "
						   + "of writer to make topics ranging from death to our "
						   + "gastrointestinal tract interesting (sometimes "
						   + "hilariously so), and pop science writer Mary Roach is "
						   + "always up to the task.") };
		
		IField[] book2 = { new IntegerField(62), new StringField("Siddhartha Mukherjee"),
				   new StringField("The Gene: An Intimate History"), new IntegerField(608),
				   new TextField("In 2010, Siddhartha Mukherjee was awarded the "
						   + "Pulitzer Prize for his book The Emperor of All "
						   + "Maladies, a “biography” of cancer.") };
		
		attributeList = Arrays.asList(bookAttr);
		bookTuple1 = new ArrayList<>(1);
		bookTuple1.add(new DataTuple(bookSchema, book1));
		bookTuple2 = new ArrayList<>(1);
		bookTuple2.add(new DataTuple(bookSchema, book2));
		
		dataStoreForOuter = new DataStore(DataConstants.INDEX_DIR, bookSchema);
		dataWriterForOuter = new DataWriter(dataStoreForOuter, analyzer);
		dataStoreForInner = new DataStore(DataConstants.INDEX_DIR, bookSchema);
		dataWriterForInner = new DataWriter(dataStoreForInner, analyzer);
		dataWriterForOuter.clearData();
		dataWriterForInner.clearData();
	}
	
	// A handy method to get join result. Called from each test case
	public List<ITuple> getJoinResults(IOperator outer, IOperator inner, 
			Attribute idAttribute, Attribute joinAttribute, 
			Integer threshold) throws Exception {
		IPredicate joinPredicate = new JoinPredicate(idAttribute, joinAttribute, 
				threshold);
		join = new Join(outer, inner, joinPredicate);
		join.open();
		
        List<ITuple> results = new ArrayList<>();
        ITuple nextTuple = null;
        
        while ((nextTuple = join.getNextTuple()) != null) {
            results.add(nextTuple);
        }
        
        return results;
	}
	
	// This method cleans up after each test.
	@After
	public void cleanUp() throws Exception {
		dataWriterForOuter.clearData();
		dataWriterForInner.clearData();
	}
	
	// This case tests for scenario when the IDs of the documents don't match.
	// Test result: The list of result returned is empty.
	@Test
	public void testIdsDontMatch() throws Exception {
		dataWriterForOuter.writeData(bookTuple1);
		dataWriterForInner.writeData(bookTuple2);
		
		String query = "special";
		IPredicate predicateOuter = new KeywordPredicate(query, attributeList, DataConstants.KeywordOperatorType.BASIC, analyzer, dataStoreForOuter);
        keywordMatcherOuter = new KeywordMatcher(predicateOuter);
        query = "cancer";
        IPredicate predicateInner = new KeywordPredicate(query, attributeList, DataConstants.KeywordOperatorType.BASIC, analyzer, dataStoreForInner);
        keywordMatcherInner = new KeywordMatcher(predicateInner);
        
        Attribute idAttr = attributeList.get(0);
        Attribute reviewAttr = attributeList.get(4);
        List<ITuple> resultList = getJoinResults(keywordMatcherOuter, keywordMatcherInner, idAttr, reviewAttr, 10);
        Assert.assertEquals(0, resultList.size());
	}
	
	// This case tests for the scenario when the IDs of the documents match, 
	// fields to join match and the difference of span of terms are within 
	// the given span threshold. 
	// Test result: The list contains tuple with all the fields and a span 
	// list consisting of the joined span. The joined span is made up of the 
	// field name, start and stop index (computed as <min(span1 spanStartIndex, 
	// span2 spanStartIndex), max(span1 spanEndIndex, span2 spanEndIndex)>) 
	// key (combination of span1 key and span2 key) and value (combination of 
	// span1 value and span2 value).
	@Test
	public void testIdsMatchFieldsMatchWithinSpan() throws Exception {
		dataWriterForOuter.writeData(bookTuple1);
		dataWriterForInner.writeData(bookTuple1);
		
		String query = "special";
		IPredicate predicateOuter = new KeywordPredicate(query, attributeList, DataConstants.KeywordOperatorType.BASIC, analyzer, dataStoreForOuter);
        keywordMatcherOuter = new KeywordMatcher(predicateOuter);
        query = "writer";
        IPredicate predicateInner = new KeywordPredicate(query, attributeList, DataConstants.KeywordOperatorType.BASIC, analyzer, dataStoreForInner);
        keywordMatcherInner = new KeywordMatcher(predicateInner);
        
        Attribute idAttr = attributeList.get(0);
        Attribute reviewAttr = attributeList.get(4);
        List<ITuple> resultList = getJoinResults(keywordMatcherOuter, keywordMatcherInner, idAttr, reviewAttr, 20);
        
        Attribute[] schemaAttributes = new Attribute[attributeList.size() + 1];
        for(int index = 0; index < schemaAttributes.length-1; index++) {
        	schemaAttributes[index] = attributeList.get(index);
        }
        schemaAttributes[schemaAttributes.length - 1] = SchemaConstants.SPAN_LIST_ATTRIBUTE;
        
        List<Span> spanList = new ArrayList<>();
        String reviewField = attributeList.get(4).getFieldName();
        Span span1 = new Span(reviewField, 11, 33, "foo", "bar");
        spanList.add(span1);
        
        IField[] book1 = { new IntegerField(52), new StringField("Mary Roach"), 
				   new StringField("Grunt: The Curious Science of Humans at War"), 
				   new IntegerField(288), new TextField("It takes a special kind "
						   + "of writer to make topics ranging from death to our "
						   + "gastrointestinal tract interesting (sometimes "
						   + "hilariously so), and pop science writer Mary Roach is "
						   + "always up to the task."), new ListField<>(spanList) };
        ITuple expectedTuple = new DataTuple(new Schema(schemaAttributes), book1);
        List<ITuple> expectedResult = new ArrayList<>(1);
        expectedResult.add(expectedTuple);
        
        boolean contains = TestUtils.containsAllResults(expectedResult, resultList);
        
        Assert.assertEquals(1, resultList.size());
        Assert.assertTrue(contains);
	}
	
	// This case tests for the scenario when the IDs match, field to join match 
	// but the difference of span of the terms to be joined are greater than the
	// threshold.
	// Test result: Join doesn't take place and an empty list is returned.
	@Test
	public void testIdsMatchFieldsMatchOutOfSpan() throws Exception {
		dataWriterForOuter.writeData(bookTuple1);
		dataWriterForInner.writeData(bookTuple1);
		
		String query = "special";
		IPredicate predicateOuter = new KeywordPredicate(query, attributeList, DataConstants.KeywordOperatorType.BASIC, analyzer, dataStoreForOuter);
        keywordMatcherOuter = new KeywordMatcher(predicateOuter);
        query = "topics";
        IPredicate predicateInner = new KeywordPredicate(query, attributeList, DataConstants.KeywordOperatorType.BASIC, analyzer, dataStoreForInner);
        keywordMatcherInner = new KeywordMatcher(predicateInner);
        
        Attribute idAttr = attributeList.get(0);
        Attribute reviewAttr = attributeList.get(4);
        List<ITuple> resultList = getJoinResults(keywordMatcherOuter, keywordMatcherInner, idAttr, reviewAttr, 20);
        Assert.assertEquals(0, resultList.size());
	}
	
	// This case tests for the scenario when one of the operators result list
	// is empty (i.e. when one of the operator is not able to find any suitable 
	// matches)
	// Test result: Join should return an empty list.
	@Test
	public void testOneOfTheOperatorResultIsEmpty() throws Exception {
		dataWriterForOuter.writeData(bookTuple1);
		dataWriterForInner.writeData(bookTuple1);
		
		String query = "special";
		IPredicate predicateOuter = new KeywordPredicate(query, attributeList, DataConstants.KeywordOperatorType.BASIC, analyzer, dataStoreForOuter);
        keywordMatcherOuter = new KeywordMatcher(predicateOuter);
        query = "book";
        IPredicate predicateInner = new KeywordPredicate(query, attributeList, DataConstants.KeywordOperatorType.BASIC, analyzer, dataStoreForInner);
        keywordMatcherInner = new KeywordMatcher(predicateInner);
        
        Attribute idAttr = attributeList.get(0);
        Attribute reviewAttr = attributeList.get(4);
        List<ITuple> resultList = getJoinResults(keywordMatcherOuter, keywordMatcherInner, idAttr, reviewAttr, 20);
        Assert.assertEquals(0, resultList.size());
	}
	
	// This case tests for the scenario when the IDs match, fields to be joined 
	// match, but one of the operators result list has no span. This can happen 
	// when one is using FuzzyTokenMatcher with "isSpanInformationAdded = false"
	// Test result: Join should return an empty list.
	@Test
	public void testOneOfTheOperatorResultContainsNoSpan() throws Exception {
		dataWriterForOuter.writeData(bookTuple1);
		dataWriterForInner.writeData(bookTuple1);
		
		String query = "special";
		IPredicate predicateOuter = new KeywordPredicate(query, attributeList, DataConstants.KeywordOperatorType.BASIC, analyzer, dataStoreForOuter);
        keywordMatcherOuter = new KeywordMatcher(predicateOuter);
        
        query = "this writer writes well";
        double thresholdRatio = 0.25;
        boolean isSpanInformationAdded = false;
        IPredicate fuzzyPredicateInner = new FuzzyTokenPredicate(query, attributeList, analyzer, dataStoreForInner, thresholdRatio, isSpanInformationAdded);
        FuzzyTokenMatcher fuzzyMatcherInner = new FuzzyTokenMatcher(fuzzyPredicateInner);
        
        Attribute idAttr = attributeList.get(0);
        Attribute reviewAttr = attributeList.get(4);
        List<ITuple> resultList = getJoinResults(keywordMatcherOuter, fuzzyMatcherInner, idAttr, reviewAttr, 20);
        Assert.assertEquals(0, resultList.size());
	}
	
	// This case tests for the scenario when the IDs match, fields to be joined 
	// match, but one of the spans to be joined is bigger than the other span 
	// and encompasses it and both |(span 1 spanStartIndex) - (span 2 spanStartIndex)|,
	// |(span 1 spanEndIndex) - (span 2 spanEndIndex)| are within threshold.
	// Test result: Bigger span should be returned.
	@Test
	public void testOneSpanEncompassesOtherAndDifferenceLessThanThreshold() throws Exception {
		dataWriterForOuter.writeData(bookTuple1);
		dataWriterForInner.writeData(bookTuple1);
		
		String query = "special";
		IPredicate predicateOuter = new KeywordPredicate(query, attributeList, DataConstants.KeywordOperatorType.BASIC, analyzer, dataStoreForOuter);
        keywordMatcherOuter = new KeywordMatcher(predicateOuter);
        query = "takes a special kind of writer";
        IPredicate predicateInner = new KeywordPredicate(query, attributeList, DataConstants.KeywordOperatorType.PHRASE, analyzer, dataStoreForInner);
        keywordMatcherInner = new KeywordMatcher(predicateInner);
        
        Attribute idAttr = attributeList.get(0);
        Attribute reviewAttr = attributeList.get(4);
        List<ITuple> resultList = getJoinResults(keywordMatcherOuter, keywordMatcherInner, idAttr, reviewAttr, 20);
        
        Attribute[] schemaAttributes = new Attribute[attributeList.size() + 1];
        for(int index = 0; index < schemaAttributes.length-1; index++) {
        	schemaAttributes[index] = attributeList.get(index);
        }
        schemaAttributes[schemaAttributes.length - 1] = SchemaConstants.SPAN_LIST_ATTRIBUTE;
        
        List<Span> spanList = new ArrayList<>();
        String reviewField = attributeList.get(4).getFieldName();
        Span span1 = new Span(reviewField, 3, 33, "foo", "bar");
        spanList.add(span1);
        
        IField[] book1 = { new IntegerField(52), new StringField("Mary Roach"), 
				   new StringField("Grunt: The Curious Science of Humans at War"), 
				   new IntegerField(288), new TextField("It takes a special kind "
						   + "of writer to make topics ranging from death to our "
						   + "gastrointestinal tract interesting (sometimes "
						   + "hilariously so), and pop science writer Mary Roach is "
						   + "always up to the task."), new ListField<>(spanList) };
        ITuple expectedTuple = new DataTuple(new Schema(schemaAttributes), book1);
        List<ITuple> expectedResult = new ArrayList<>(1);
        expectedResult.add(expectedTuple);
        
        boolean contains = TestUtils.containsAllResults(expectedResult, resultList);
        
        Assert.assertEquals(1, resultList.size());
        Assert.assertTrue(contains);
	}
	
	// This case tests for the scenario when the IDs match, fields to be joined 
	// match, but one of the spans to be joined is bigger than the other span
	// and encompasses it and |(span 1 spanStartIndex) - (span 2 spanStartIndex)| 
	// and/or |(span 1 spanEndIndex) - (span 2 spanEndIndex)| exceed threshold.
	// Test result: Join should return an empty list.
	@Test
	public void testOneSpanEncompassesOtherAndDifferenceGreaterThanThreshold() throws Exception {
		dataWriterForOuter.writeData(bookTuple1);
		dataWriterForInner.writeData(bookTuple1);
		
		String query = "special";
		IPredicate predicateOuter = new KeywordPredicate(query, attributeList, DataConstants.KeywordOperatorType.BASIC, analyzer, dataStoreForOuter);
        keywordMatcherOuter = new KeywordMatcher(predicateOuter);
        query = "takes a special kind of writer";
        IPredicate predicateInner = new KeywordPredicate(query, attributeList, DataConstants.KeywordOperatorType.PHRASE, analyzer, dataStoreForInner);
        keywordMatcherInner = new KeywordMatcher(predicateInner);
        
        Attribute idAttr = attributeList.get(0);
        Attribute reviewAttr = attributeList.get(4);
        List<ITuple> resultList = getJoinResults(keywordMatcherOuter, keywordMatcherInner, idAttr, reviewAttr, 10);
        Assert.assertEquals(0, resultList.size());
	}
	
	// This case tests for the scenario when the IDs match, fields to be joined 
	// match, but the spans to be joined have some overlap and both 
	// |(span 1 spanStartIndex) - (span 2 spanStartIndex)|, 
	// |(span 1 spanEndIndex) - (span 2 spanEndIndex)| are within threshold.
	// Test result: The list contains tuple with all the fields and a span 
	// list consisting of the joined span. The joined span is made up of the 
	// field name, start and stop index (computed as <min(span1 spanStartIndex, 
	// span2 spanStartIndex), max(span1 spanEndIndex, span2 spanEndIndex)>) 
	// key (combination of span1 key and span2 key) and value (combination of 
	// span1 value and span2 value).
	@Test
	public void testSpansOverlapAndWithinThreshold() throws Exception {
		dataWriterForOuter.writeData(bookTuple1);
		dataWriterForInner.writeData(bookTuple1);
		
		String query = "a special";
		IPredicate predicateOuter = new KeywordPredicate(query, attributeList, DataConstants.KeywordOperatorType.PHRASE, analyzer, dataStoreForOuter);
        keywordMatcherOuter = new KeywordMatcher(predicateOuter);
        query = "special kind";
        IPredicate predicateInner = new KeywordPredicate(query, attributeList, DataConstants.KeywordOperatorType.PHRASE, analyzer, dataStoreForInner);
        keywordMatcherInner = new KeywordMatcher(predicateInner);
        
        Attribute idAttr = attributeList.get(0);
        Attribute reviewAttr = attributeList.get(4);
        List<ITuple> resultList = getJoinResults(keywordMatcherOuter, keywordMatcherInner, idAttr, reviewAttr, 10);
        
        Attribute[] schemaAttributes = new Attribute[attributeList.size() + 1];
        for(int index = 0; index < schemaAttributes.length-1; index++) {
        	schemaAttributes[index] = attributeList.get(index);
        }
        schemaAttributes[schemaAttributes.length - 1] = SchemaConstants.SPAN_LIST_ATTRIBUTE;
        
        List<Span> spanList = new ArrayList<>();
        String reviewField = attributeList.get(4).getFieldName();
        Span span1 = new Span(reviewField, 9, 23, "foo", "bar");
        spanList.add(span1);
        
        IField[] book1 = { new IntegerField(52), new StringField("Mary Roach"), 
				   new StringField("Grunt: The Curious Science of Humans at War"), 
				   new IntegerField(288), new TextField("It takes a special kind "
						   + "of writer to make topics ranging from death to our "
						   + "gastrointestinal tract interesting (sometimes "
						   + "hilariously so), and pop science writer Mary Roach is "
						   + "always up to the task."), new ListField<>(spanList) };
        ITuple expectedTuple = new DataTuple(new Schema(schemaAttributes), book1);
        List<ITuple> expectedResult = new ArrayList<>(1);
        expectedResult.add(expectedTuple);
        
        boolean contains = TestUtils.containsAllResults(expectedResult, resultList);
        
        Assert.assertEquals(1, resultList.size());
        Assert.assertTrue(contains);
	}
	
	// This case tests for the scenario when the IDs match, fields to be joined 
	// match, but the spans to be joined have some overlap and 
	// |(span 1 spanStartIndex) - (span 2 spanStartIndex)| and/or 
	// |(span 1 spanEndIndex) - (span 2 spanEndIndex)| exceed threshold.
	// Test result: Join should return empty list.
	@Test
	public void testSpansOverlapAndExceedThreshold() throws Exception {
		dataWriterForOuter.writeData(bookTuple1);
		dataWriterForInner.writeData(bookTuple1);
		
		String query = "takes a special";
		IPredicate predicateOuter = new KeywordPredicate(query, attributeList, DataConstants.KeywordOperatorType.PHRASE, analyzer, dataStoreForOuter);
        keywordMatcherOuter = new KeywordMatcher(predicateOuter);
        query = "special kind of writer";
        IPredicate predicateInner = new KeywordPredicate(query, attributeList, DataConstants.KeywordOperatorType.PHRASE, analyzer, dataStoreForInner);
        keywordMatcherInner = new KeywordMatcher(predicateInner);
        
        Attribute idAttr = attributeList.get(0);
        Attribute reviewAttr = attributeList.get(4);
        List<ITuple> resultList = getJoinResults(keywordMatcherOuter, keywordMatcherInner, idAttr, reviewAttr, 10);
        Assert.assertEquals(0, resultList.size());
	}
	
	// This case tests for the scenario when the IDs match, fields to be joined 
	// match, but the spans to be joined are the same, i.e. both the terms are same.
	// Test result: Join should return same span and key and value in span should be
	// same.
	@Test
	public void testBothTheSpansAreSame() throws Exception {
		dataWriterForOuter.writeData(bookTuple1);
		dataWriterForInner.writeData(bookTuple1);
		
		String query = "special";
		IPredicate predicateOuter = new KeywordPredicate(query, attributeList, DataConstants.KeywordOperatorType.BASIC, analyzer, dataStoreForOuter);
        keywordMatcherOuter = new KeywordMatcher(predicateOuter);
        query = "special";
        IPredicate predicateInner = new KeywordPredicate(query, attributeList, DataConstants.KeywordOperatorType.BASIC, analyzer, dataStoreForInner);
        keywordMatcherInner = new KeywordMatcher(predicateInner);
        
        Attribute idAttr = attributeList.get(0);
        Attribute reviewAttr = attributeList.get(4);
        List<ITuple> resultList = getJoinResults(keywordMatcherOuter, keywordMatcherInner, idAttr, reviewAttr, 20);
        
        Attribute[] schemaAttributes = new Attribute[attributeList.size() + 1];
        for(int index = 0; index < schemaAttributes.length-1; index++) {
        	schemaAttributes[index] = attributeList.get(index);
        }
        schemaAttributes[schemaAttributes.length - 1] = SchemaConstants.SPAN_LIST_ATTRIBUTE;
        
        List<Span> spanList = new ArrayList<>();
        String reviewField = attributeList.get(4).getFieldName();
        Span span1 = new Span(reviewField, 11, 18, "foo", "bar");
        spanList.add(span1);
        
        IField[] book1 = { new IntegerField(52), new StringField("Mary Roach"), 
				   new StringField("Grunt: The Curious Science of Humans at War"), 
				   new IntegerField(288), new TextField("It takes a special kind "
						   + "of writer to make topics ranging from death to our "
						   + "gastrointestinal tract interesting (sometimes "
						   + "hilariously so), and pop science writer Mary Roach is "
						   + "always up to the task."), new ListField<>(spanList) };
        ITuple expectedTuple = new DataTuple(new Schema(schemaAttributes), book1);
        List<ITuple> expectedResult = new ArrayList<>(1);
        expectedResult.add(expectedTuple);
        
        boolean contains = TestUtils.containsAllResults(expectedResult, resultList);
        
        Assert.assertEquals(1, resultList.size());
        Assert.assertTrue(contains);
	}
}