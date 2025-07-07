package tech.ytsaurus.client.request;

import org.junit.Test;
import tech.ytsaurus.core.tables.ColumnValueType;
import tech.ytsaurus.core.tables.TableSchema;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MultiLookupRowsSubrequestTest {

    private final String path = "//home/test-table";
    private final TableSchema schema = new TableSchema.Builder()
            .addKey("key", ColumnValueType.STRING)
            .build();

    @Test
    public void testEnablePartialResultDefaultValue() {
        MultiLookupRowsSubrequest subrequest = MultiLookupRowsSubrequest.builder()
                .setPath(path)
                .setSchema(schema.toLookup())
                .build();

        assertFalse("EnablePartialResult should default to false", subrequest.getEnablePartialResult());
    }

    @Test
    public void testEnablePartialResultSetToTrue() {
        MultiLookupRowsSubrequest subrequest = MultiLookupRowsSubrequest.builder()
                .setPath(path)
                .setSchema(schema.toLookup())
                .setEnablePartialResult(true)
                .build();

        assertTrue("EnablePartialResult should be true when set", subrequest.getEnablePartialResult());
    }

    @Test
    public void testEnablePartialResultSetToFalse() {
        MultiLookupRowsSubrequest subrequest = MultiLookupRowsSubrequest.builder()
                .setPath(path)
                .setSchema(schema.toLookup())
                .setEnablePartialResult(false)
                .build();

        assertFalse("EnablePartialResult should be false when explicitly set", subrequest.getEnablePartialResult());
    }

    @Test
    public void testConstructorWithPathAndSchema() {
        MultiLookupRowsSubrequest subrequest = new MultiLookupRowsSubrequest(path, schema.toLookup());

        assertFalse("EnablePartialResult should default to false", subrequest.getEnablePartialResult());
        assertEquals("Path should be set correctly", path, subrequest.getPath());
    }

    @Test
    public void testToBuilderPreservesEnablePartialResult() {
        MultiLookupRowsSubrequest original = MultiLookupRowsSubrequest.builder()
                .setPath(path)
                .setSchema(schema.toLookup())
                .setEnablePartialResult(true)
                .addFilter("test-key")
                .build();

        MultiLookupRowsSubrequest rebuilt = original.toBuilder().build();

        assertEquals("Rebuilt subrequest should preserve EnablePartialResult", 
                     original.getEnablePartialResult(), rebuilt.getEnablePartialResult());
        assertEquals("Rebuilt subrequest should preserve path", 
                     original.getPath(), rebuilt.getPath());
    }

    @Test
    public void testBuilderGetterMethods() {
        MultiLookupRowsSubrequest.Builder builder = MultiLookupRowsSubrequest.builder()
                .setPath(path)
                .setSchema(schema.toLookup())
                .setEnablePartialResult(true);

        assertTrue("Builder should return EnablePartialResult value", 
                   builder.getEnablePartialResult());
    }

    @Test
    public void testEnablePartialResultWithOtherOptions() {
        MultiLookupRowsSubrequest subrequest = MultiLookupRowsSubrequest.builder()
                .setPath(path)
                .setSchema(schema.toLookup())
                .setEnablePartialResult(true)
                .setKeepMissingRows(true)
                .addLookupColumn("key")
                .addFilter("test-value")
                .build();

        assertTrue("EnablePartialResult should be true", subrequest.getEnablePartialResult());
        assertTrue("KeepMissingRows should be true", subrequest.getKeepMissingRows());
        assertEquals("Should have one lookup column", 1, subrequest.getLookupColumns().size());
        assertEquals("Lookup column should be 'key'", "key", subrequest.getLookupColumns().get(0));
    }
}