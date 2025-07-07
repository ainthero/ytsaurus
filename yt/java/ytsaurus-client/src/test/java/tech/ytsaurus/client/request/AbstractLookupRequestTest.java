package tech.ytsaurus.client.request;

import org.junit.Test;
import tech.ytsaurus.core.tables.ColumnValueType;
import tech.ytsaurus.core.tables.TableSchema;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AbstractLookupRequestTest {

    private final String path = "//home/test-table";
    private final TableSchema schema = new TableSchema.Builder()
            .addKey("key", ColumnValueType.STRING)
            .build();

    @Test
    public void testEnablePartialResultDefaultValue() {
        LookupRowsRequest request = LookupRowsRequest.builder()
                .setPath(path)
                .setSchema(schema.toLookup())
                .build();

        assertFalse("EnablePartialResult should default to false", request.getEnablePartialResult());
    }

    @Test
    public void testEnablePartialResultSetToTrue() {
        LookupRowsRequest request = LookupRowsRequest.builder()
                .setPath(path)
                .setSchema(schema.toLookup())
                .setEnablePartialResult(true)
                .build();

        assertTrue("EnablePartialResult should be true when set", request.getEnablePartialResult());
    }

    @Test
    public void testEnablePartialResultSetToFalse() {
        LookupRowsRequest request = LookupRowsRequest.builder()
                .setPath(path)
                .setSchema(schema.toLookup())
                .setEnablePartialResult(false)
                .build();

        assertFalse("EnablePartialResult should be false when explicitly set", request.getEnablePartialResult());
    }

    @Test
    public void testEnablePartialResultInArgumentsLogString() {
        LookupRowsRequest request = LookupRowsRequest.builder()
                .setPath(path)
                .setSchema(schema.toLookup())
                .setEnablePartialResult(true)
                .build();

        String logString = request.getArgumentsLogString();
        assertTrue("Log string should contain EnablePartialResult: true", 
                   logString.contains("EnablePartialResult: true"));
    }

    @Test
    public void testEnablePartialResultWithKeepMissingRows() {
        LookupRowsRequest request = LookupRowsRequest.builder()
                .setPath(path)
                .setSchema(schema.toLookup())
                .setEnablePartialResult(true)
                .setKeepMissingRows(true)
                .build();

        assertTrue("EnablePartialResult should be true", request.getEnablePartialResult());
        assertTrue("KeepMissingRows should be true", request.getKeepMissingRows());
    }

    @Test
    public void testBuilderGetterMethods() {
        LookupRowsRequest.Builder builder = LookupRowsRequest.builder()
                .setPath(path)
                .setSchema(schema.toLookup())
                .setEnablePartialResult(true);

        assertTrue("Builder should return EnablePartialResult value", 
                   builder.getEnablePartialResult());
    }

    @Test
    public void testToBuilderPreservesEnablePartialResult() {
        LookupRowsRequest original = LookupRowsRequest.builder()
                .setPath(path)
                .setSchema(schema.toLookup())
                .setEnablePartialResult(true)
                .build();

        LookupRowsRequest rebuilt = original.toBuilder().build();

        assertEquals("Rebuilt request should preserve EnablePartialResult", 
                     original.getEnablePartialResult(), rebuilt.getEnablePartialResult());
    }
}