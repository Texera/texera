package edu.uci.ics.textdb.api.common;		
 
import java.util.List;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
 		
public class SchemaTest {		
	private Schema schema;		
    private String fieldName1 = "sampleField_1";		
    private FieldType type1 = FieldType.STRING;		
    private String fieldName2 = "sampleField_2";		
    private FieldType type2 = FieldType.STRING;		
 		
    private Attribute[] attributes = 		
        {new Attribute(fieldName1, type1), new Attribute(fieldName2, type2)};;		
     		
    @Before		
    public void setUp(){		
        schema = new Schema(attributes);		
    }		
     		
    @Test		
    public void testGetAttributes(){		
         List<Attribute> attributes = schema.getAttributes();		
         Assert.assertEquals(this.attributes.length, attributes.size());		
         for(Attribute attribute : this.attributes){		
             Assert.assertTrue(attributes.contains(attribute));		
         }		
    }		
     		
    @Test		
    public void testGetIndex(){		
         		
         int expectedIndex1 = 0;		
         int expectedIndex2 = 1;		
         int retrievedIndex1 = schema.getIndex(fieldName1);		
         int retrievedIndex2 = schema.getIndex(fieldName2.toUpperCase());		
         Assert.assertEquals(expectedIndex1, retrievedIndex1);		
         Assert.assertEquals(expectedIndex2, retrievedIndex2);		
         		
    }		
     		
    @Test(expected = UnsupportedOperationException.class)		
    public void testAddingNewAttribute(){ //Should fail due to immutability		
         List<Attribute> attributes  = schema.getAttributes();		
         attributes.add(new Attribute("sampleField_3", FieldType.STRING));		
    }		
     		
    @Test(expected = UnsupportedOperationException.class)		
    public void testRemovingAttribute(){ //Should fail due to immutability		
         List<Attribute> attributes  = schema.getAttributes();		
         attributes.remove(0);		
    }		
     		
    @Test(expected = UnsupportedOperationException.class)		
    public void testAddingInBetween(){ //Should fail due to immutability		
         List<Attribute> attributes  = schema.getAttributes();		
         attributes.add(0, new Attribute("sampleField_3", FieldType.STRING));
        
    }		
 }