package tech.ytsaurus.client.rows;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import tech.ytsaurus.client.rows.UnversionedRowset;
import tech.ytsaurus.core.tables.TableSchema;
import tech.ytsaurus.core.tables.ColumnValueType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LookupRowsResultTest {

    @Test
    public void testLookupRowsResultWithEmptyUnavailableKeys() {
        List<String> rowset = Arrays.asList("row1", "row2", "row3");
        List<Integer> unavailableKeys = Collections.emptyList();
        
        LookupRowsResult<List<String>> result = new LookupRowsResult<>(rowset, unavailableKeys);
        
        assertEquals("Rowset should be preserved", rowset, result.getRowset());
        assertEquals("Unavailable keys should be empty", unavailableKeys, result.getUnavailableKeyIndexes());
        assertFalse("Should not have unavailable keys", result.hasUnavailableKeys());
    }

    @Test
    public void testLookupRowsResultWithUnavailableKeys() {
        List<String> rowset = Arrays.asList("row1", "row3");
        List<Integer> unavailableKeys = Arrays.asList(1, 4);
        
        LookupRowsResult<List<String>> result = new LookupRowsResult<>(rowset, unavailableKeys);
        
        assertEquals("Rowset should be preserved", rowset, result.getRowset());
        assertEquals("Unavailable keys should be preserved", unavailableKeys, result.getUnavailableKeyIndexes());
        assertTrue("Should have unavailable keys", result.hasUnavailableKeys());
        assertEquals("Should have 2 unavailable keys", 2, result.getUnavailableKeyIndexes().size());
        assertEquals("First unavailable key should be 1", Integer.valueOf(1), result.getUnavailableKeyIndexes().get(0));
        assertEquals("Second unavailable key should be 4", Integer.valueOf(4), result.getUnavailableKeyIndexes().get(1));
    }

    @Test
    public void testLookupRowsResultWithSingleUnavailableKey() {
        List<String> rowset = Arrays.asList("row1", "row2");
        List<Integer> unavailableKeys = Arrays.asList(2);
        
        LookupRowsResult<List<String>> result = new LookupRowsResult<>(rowset, unavailableKeys);
        
        assertEquals("Rowset should be preserved", rowset, result.getRowset());
        assertEquals("Unavailable keys should be preserved", unavailableKeys, result.getUnavailableKeyIndexes());
        assertTrue("Should have unavailable keys", result.hasUnavailableKeys());
        assertEquals("Should have 1 unavailable key", 1, result.getUnavailableKeyIndexes().size());
        assertEquals("Unavailable key should be 2", Integer.valueOf(2), result.getUnavailableKeyIndexes().get(0));
    }

    @Test
    public void testLookupRowsResultWithDifferentRowsetType() {
        TableSchema schema = new TableSchema.Builder().addKey("key", ColumnValueType.STRING).build();
        UnversionedRowset rowset = new UnversionedRowset(schema, Collections.emptyList());
        List<Integer> unavailableKeys = Arrays.asList(0, 3, 5);
        
        LookupRowsResult<UnversionedRowset> result = new LookupRowsResult<>(rowset, unavailableKeys);
        
        assertEquals("Rowset should be preserved", rowset, result.getRowset());
        assertEquals("Unavailable keys should be preserved", unavailableKeys, result.getUnavailableKeyIndexes());
        assertTrue("Should have unavailable keys", result.hasUnavailableKeys());
        assertEquals("Should have 3 unavailable keys", 3, result.getUnavailableKeyIndexes().size());
    }

    @Test
    public void testToString() {
        List<String> rowset = Arrays.asList("row1", "row2");
        List<Integer> unavailableKeys = Arrays.asList(1, 3);
        
        LookupRowsResult<List<String>> result = new LookupRowsResult<>(rowset, unavailableKeys);
        
        String stringRepresentation = result.toString();
        assertTrue("String should contain 'LookupRowsResult'", stringRepresentation.contains("LookupRowsResult"));
        assertTrue("String should contain rowset info", stringRepresentation.contains("rowset="));
        assertTrue("String should contain unavailable keys info", stringRepresentation.contains("unavailableKeyIndexes="));
    }

    @Test(expected = NullPointerException.class)
    public void testLookupRowsResultWithNullRowset() {
        List<Integer> unavailableKeys = Collections.emptyList();
        new LookupRowsResult<List<String>>(null, unavailableKeys);
    }

    @Test(expected = NullPointerException.class)
    public void testLookupRowsResultWithNullUnavailableKeys() {
        List<String> rowset = Arrays.asList("row1", "row2");
        new LookupRowsResult<>(rowset, null);
    }
}