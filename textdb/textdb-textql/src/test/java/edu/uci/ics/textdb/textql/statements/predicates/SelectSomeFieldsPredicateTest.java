package edu.uci.ics.textdb.textql.statements.predicates;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import edu.uci.ics.textdb.common.constants.SchemaConstants;
import edu.uci.ics.textdb.web.request.beans.OperatorBean;
import edu.uci.ics.textdb.web.request.beans.ProjectionBean;

/**
 * This class contains test cases for the SelectSomeFieldsPredicate class.
 * The constructor, getters, setters and the generateOperatorBean methods are
 * tested.
 * 
 * @author Flavio Bayer
 *
 */
public class SelectSomeFieldsPredicateTest {
    
    /**
     * Test the class constructor, getter and the setter methods.
     * Call the constructor of the SelectSomeFieldsPredicate, test 
     * if the returned value by the getter is the same as used in 
     * the constructor and then test if the value is changed
     * when the setter method is invoked.
     */
    @Test
    public void testConstructorsGettersSetters(){
        List<String> projectedFields;

        projectedFields = Collections.emptyList();
        assertConstructorGettersSetters(projectedFields);
        
        projectedFields = Arrays.asList("a","b","c","d");
        assertConstructorGettersSetters(projectedFields);
        
        projectedFields = Arrays.asList("field1", "field2", "field0");
        assertConstructorGettersSetters(projectedFields);        

        projectedFields = Arrays.asList(SchemaConstants._ID, SchemaConstants.PAYLOAD, SchemaConstants.SPAN_LIST);
        assertConstructorGettersSetters(projectedFields);
    }
    
    /**
     * Assert the correctness of the Constructor, getter and setter methods.
     * @param projectedFields The list of projected fields to be tested.
     */
    private void assertConstructorGettersSetters(List<String> projectedFields){
        SelectSomeFieldsPredicate selectSomeFieldsPredicate;
        
        // Check constructor
        selectSomeFieldsPredicate = new SelectSomeFieldsPredicate(projectedFields);
        Assert.assertEquals(selectSomeFieldsPredicate.getProjectedFields(), projectedFields);
        
        // Check set projectedFields to null
        selectSomeFieldsPredicate.setProjectedFields(null);
        Assert.assertEquals(selectSomeFieldsPredicate.getProjectedFields(), null);
        
        // Check set projectedFields to the given list of fields
        selectSomeFieldsPredicate.setProjectedFields(projectedFields);
        Assert.assertEquals(selectSomeFieldsPredicate.getProjectedFields(), projectedFields);
    }

    /**
     * Test the generateOperatorBean method.
     * Build a SelectSomeFieldsPredicate, invoke the generateOperatorBean and check
     * whether a ProjectionBean with the right attributes is returned.
     * An empty list is used as the list of projected fields.
     */
    @Test
    public void testGenerateOperatorBean00() {
        String operatorId = "xxx";
        List<String> projectedFields = Collections.emptyList();
        SelectSomeFieldsPredicate selectSomeFieldsPredicate = new SelectSomeFieldsPredicate(projectedFields);
        
        OperatorBean computedProjectionBean = selectSomeFieldsPredicate.generateOperatorBean(operatorId);
        OperatorBean expectedProjectionBean = new ProjectionBean(operatorId, "Projection", "", null, null);
        
        Assert.assertEquals(expectedProjectionBean, computedProjectionBean);
    }
    
    /**
     * Test the generateOperatorBean method.
     * Build a SelectSomeFieldsPredicate, invoke the generateOperatorBean and check
     * whether a ProjectionBean with the right attributes is returned.
     * A list with some field names is used as the list of projected fields.
     */
    @Test
    public void testGenerateOperatorBean01() {
        String operatorId = "zwx";
        List<String> projectedFields = Arrays.asList("field0", "field1");
        SelectSomeFieldsPredicate selectSomeFieldsPredicate = new SelectSomeFieldsPredicate(projectedFields);
        
        OperatorBean computedProjectionBean = selectSomeFieldsPredicate.generateOperatorBean(operatorId);
        OperatorBean expectedProjectionBean = new ProjectionBean(operatorId, "Projection", "field0,field1", null, null);

        Assert.assertEquals(expectedProjectionBean, computedProjectionBean);        
    }

    /**
     * Test the generateOperatorBean method.
     * Build a SelectSomeFieldsPredicate, invoke the generateOperatorBean and check
     * whether a ProjectionBean with the right attributes is returned.
     * A list with some unordered field names is used as the list of projected fields.
     */
    @Test
    public void testGenerateOperatorBean02() {
        String operatorId = "op00";
        List<String> projectedFields = Arrays.asList("c", "a", "b");
        SelectSomeFieldsPredicate selectSomeFieldsPredicate = new SelectSomeFieldsPredicate(projectedFields);
        
        OperatorBean computedProjectionBean = selectSomeFieldsPredicate.generateOperatorBean(operatorId);
        OperatorBean expectedProjectionBean = new ProjectionBean(operatorId, "Projection", "c,a,b", null, null);
        
        Assert.assertEquals(expectedProjectionBean, computedProjectionBean);   
    }
    
}
