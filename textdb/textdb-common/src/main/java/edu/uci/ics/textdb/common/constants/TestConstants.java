/**
 * 
 */
package edu.uci.ics.textdb.common.constants;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;

import edu.uci.ics.textdb.api.common.Attribute;
import edu.uci.ics.textdb.api.common.FieldType;
import edu.uci.ics.textdb.api.common.IField;
import edu.uci.ics.textdb.api.common.ITuple;
import edu.uci.ics.textdb.common.field.DataTuple;
import edu.uci.ics.textdb.common.field.DateField;
import edu.uci.ics.textdb.common.field.DoubleField;
import edu.uci.ics.textdb.common.field.IntegerField;
import edu.uci.ics.textdb.common.field.StringField;

/**
 * @author sandeepreddy602
 * Including this class in src/main/java since it is required by other projects
 * Keeping it in src/test/java doesn't make it available to the other projects
 */
public class TestConstants {
    // Sample Fields
    public static final String FIRST_NAME = "firstName";
    public static final String LAST_NAME = "lastName";
    public static final String AGE = "age";
    public static final String HEIGHT = "height";
    public static final String DATE_OF_BIRTH = "dateOfBirth";
    
    
    public static final Attribute FIRST_NAME_ATTR = new Attribute(FIRST_NAME, FieldType.STRING);
    public static final Attribute LAST_NAME_ATTR = new Attribute(LAST_NAME, FieldType.STRING);
    public static final Attribute AGE_ATTR = new Attribute(AGE, FieldType.INTEGER);
    public static final Attribute HEIGHT_ATTR = new Attribute(HEIGHT, FieldType.DOUBLE);
    public static final Attribute DATE_OF_BIRTH_ATTR = new Attribute(DATE_OF_BIRTH, FieldType.DATE);

    // Sample Schema
    public static final List<Attribute> SAMPLE_SCHEMA_PEOPLE = Arrays.asList(
            FIRST_NAME_ATTR, LAST_NAME_ATTR, AGE_ATTR, HEIGHT_ATTR, DATE_OF_BIRTH_ATTR);
    
    public static List<ITuple> getSamplePeopleTuples() throws ParseException{
        
        IField[] fields1 = {new StringField("bruce"), new StringField("lee"), new IntegerField(46), 
                new DoubleField(5.50), new DateField(new SimpleDateFormat("MM-dd-yyyy").parse("01-14-1970"))};
        IField[] fields2 = {new StringField("tom"), new StringField("cruise"), new IntegerField(45), 
                new DoubleField(5.95), new DateField(new SimpleDateFormat("MM-dd-yyyy").parse("01-13-1971"))};
        IField[] fields3 = {new StringField("brad"), new StringField("pitt"), new IntegerField(44), 
                new DoubleField(6.10), new DateField(new SimpleDateFormat("MM-dd-yyyy").parse("01-12-1972"))};
        IField[] fields4 = {new StringField("george"), new StringField("clooney"), new IntegerField(43), 
                new DoubleField(6.06), new DateField(new SimpleDateFormat("MM-dd-yyyy").parse("01-13-1973"))};
        IField[] fields5 = {new StringField("christian"), new StringField("bale"), new IntegerField(42), 
                new DoubleField(5.99), new DateField(new SimpleDateFormat("MM-dd-yyyy").parse("01-13-1974"))};
        
        
        ITuple tuple1 = new DataTuple(SAMPLE_SCHEMA_PEOPLE, fields1);
        ITuple tuple2 = new DataTuple(SAMPLE_SCHEMA_PEOPLE, fields2);
        ITuple tuple3 = new DataTuple(SAMPLE_SCHEMA_PEOPLE, fields3);
        ITuple tuple4 = new DataTuple(SAMPLE_SCHEMA_PEOPLE, fields4);
        ITuple tuple5 = new DataTuple(SAMPLE_SCHEMA_PEOPLE, fields5);
        
        return Arrays.asList(tuple1, tuple2, tuple3, tuple4, tuple5);
                
    }
}
