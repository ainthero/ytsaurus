package tech.ytsaurus.client;

import org.junit.Test;
import tech.ytsaurus.client.request.LookupRowsRequest;
import tech.ytsaurus.client.request.MultiLookupRowsRequest;
import tech.ytsaurus.client.request.MultiLookupRowsSubrequest;
import tech.ytsaurus.client.rows.LookupRowsResult;
import tech.ytsaurus.client.rows.UnversionedRow;
import tech.ytsaurus.client.rows.UnversionedRowset;
import tech.ytsaurus.core.rows.YTreeMapNodeSerializer;
import tech.ytsaurus.core.tables.ColumnValueType;
import tech.ytsaurus.core.tables.TableSchema;
import tech.ytsaurus.ysontree.YTree;
import tech.ytsaurus.ysontree.YTreeMapNode;
import tech.ytsaurus.ysontree.YTreeNode;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EnablePartialResultIntegrationTest {

    private final String path = "//home/test-table";
    private final TableSchema schema = new TableSchema.Builder()
            .addKey("key", ColumnValueType.STRING)
            .build();

    @Test
    public void testLookupRowsWithResultReturnsCorrectData() {
        MockYTsaurusClient mockClient = new MockYTsaurusClient("test");
        
        // Mock data with partial results
        List<YTreeNode> expectedRowset = Arrays.asList(
                YTree.mapBuilder().key("key").value("value1").buildMap(),
                YTree.mapBuilder().key("key").value("value3").buildMap()
        );
        List<Integer> expectedUnavailableKeys = Arrays.asList(1, 4);
        LookupRowsResult<List<YTreeNode>> expectedResult = 
            new LookupRowsResult<>(expectedRowset, expectedUnavailableKeys);

        mockClient.mockMethod("lookupRowsWithResult", () -> CompletableFuture.completedFuture(expectedResult));

        LookupRowsRequest request = LookupRowsRequest.builder()
                .setPath(path)
                .setSchema(schema.toLookup())
                .setEnablePartialResult(true)
                .addFilter("key1")
                .addFilter("key2")
                .addFilter("key3")
                .addFilter("key4")
                .addFilter("key5")
                .build();

        CompletableFuture<LookupRowsResult<List<YTreeMapNode>>> result = 
            mockClient.lookupRowsWithResult(request, new YTreeMapNodeSerializer());

        LookupRowsResult<List<YTreeMapNode>> actualResult = result.join();
        assertEquals("Should return correct rowset", expectedRowset, actualResult.getRowset());
        assertEquals("Should return correct unavailable keys", expectedUnavailableKeys, 
                     actualResult.getUnavailableKeyIndexes());
        assertTrue("Should have unavailable keys", actualResult.hasUnavailableKeys());
        assertEquals("Should have 2 unavailable keys", 2, actualResult.getUnavailableKeyIndexes().size());
    }

    @Test
    public void testLookupRowsWithResultWithoutPartialResult() {
        MockYTsaurusClient mockClient = new MockYTsaurusClient("test");
        
        // Mock data without partial results  
        List<YTreeNode> expectedRowset = Arrays.asList(
                YTree.mapBuilder().key("key").value("value1").buildMap(),
                YTree.mapBuilder().key("key").value("value2").buildMap(),
                YTree.mapBuilder().key("key").value("value3").buildMap()
        );
        LookupRowsResult<List<YTreeNode>> expectedResult = 
            new LookupRowsResult<>(expectedRowset, Arrays.asList());

        mockClient.mockMethod("lookupRowsWithResult", () -> CompletableFuture.completedFuture(expectedResult));

        LookupRowsRequest request = LookupRowsRequest.builder()
                .setPath(path)
                .setSchema(schema.toLookup())
                .setEnablePartialResult(false)
                .addFilter("key1")
                .addFilter("key2")
                .addFilter("key3")
                .build();

        CompletableFuture<LookupRowsResult<List<YTreeMapNode>>> result = 
            mockClient.lookupRowsWithResult(request, new YTreeMapNodeSerializer());

        LookupRowsResult<List<YTreeMapNode>> actualResult = result.join();
        assertEquals("Should return correct rowset", expectedRowset, actualResult.getRowset());
        assertTrue("Should have empty unavailable keys", actualResult.getUnavailableKeyIndexes().isEmpty());
        assertFalse("Should not have unavailable keys", actualResult.hasUnavailableKeys());
    }

    @Test
    public void testLookupRowsWithResultUnversionedRowset() {
        MockYTsaurusClient mockClient = new MockYTsaurusClient("test");
        
        // Mock UnversionedRowset result
        UnversionedRowset expectedRowset = new UnversionedRowset(schema, Arrays.asList());
        List<Integer> expectedUnavailableKeys = Arrays.asList(0, 2);
        LookupRowsResult<UnversionedRowset> expectedResult = 
            new LookupRowsResult<>(expectedRowset, expectedUnavailableKeys);

        mockClient.mockMethod("lookupRowsWithResult", () -> CompletableFuture.completedFuture(expectedResult));

        LookupRowsRequest request = LookupRowsRequest.builder()
                .setPath(path)
                .setSchema(schema.toLookup())
                .setEnablePartialResult(true)
                .addFilter("key1")
                .addFilter("key2")
                .addFilter("key3")
                .build();

        CompletableFuture<LookupRowsResult<UnversionedRowset>> result = 
            mockClient.lookupRowsWithResult(request);

        LookupRowsResult<UnversionedRowset> actualResult = result.join();
        assertEquals("Should return correct rowset", expectedRowset, actualResult.getRowset());
        assertEquals("Should return correct unavailable keys", expectedUnavailableKeys, 
                     actualResult.getUnavailableKeyIndexes());
        assertTrue("Should have unavailable keys", actualResult.hasUnavailableKeys());
    }

    @Test
    public void testMultiLookupRowsWithResult() {
        MockYTsaurusClient mockClient = new MockYTsaurusClient("test");
        
        // Mock multi lookup data with partial results
        List<LookupRowsResult<List<YTreeNode>>> expectedResults = Arrays.asList(
                new LookupRowsResult<>(
                        Arrays.asList(YTree.mapBuilder().key("key").value("value1").buildMap()),
                        Arrays.asList(1)
                ),
                new LookupRowsResult<>(
                        Arrays.asList(
                                YTree.mapBuilder().key("key").value("value2").buildMap(),
                                YTree.mapBuilder().key("key").value("value3").buildMap()
                        ),
                        Arrays.asList()
                )
        );

        mockClient.mockMethod("multiLookupRowsWithResult", () -> CompletableFuture.completedFuture(expectedResults));

        MultiLookupRowsRequest request = new MultiLookupRowsRequest().toBuilder()
                .addSubrequest(MultiLookupRowsSubrequest.builder()
                        .setPath(path)
                        .setSchema(schema.toLookup())
                        .setEnablePartialResult(true)
                        .addFilter("key1")
                        .addFilter("key2")
                        .build())
                .addSubrequest(MultiLookupRowsSubrequest.builder()
                        .setPath(path)
                        .setSchema(schema.toLookup())
                        .setEnablePartialResult(true)
                        .addFilter("key3")
                        .addFilter("key4")
                        .build())
                .build();

        CompletableFuture<List<LookupRowsResult<List<YTreeMapNode>>>> result = 
            mockClient.multiLookupRowsWithResult(request, new YTreeMapNodeSerializer());

        List<LookupRowsResult<List<YTreeMapNode>>> actualResults = result.join();
        assertEquals("Should return correct number of results", 2, actualResults.size());
        
        // Check first subrequest result
        LookupRowsResult<List<YTreeMapNode>> firstResult = actualResults.get(0);
        assertEquals("First result should have 1 row", 1, firstResult.getRowset().size());
        assertEquals("First result should have 1 unavailable key", 1, 
                     firstResult.getUnavailableKeyIndexes().size());
        assertEquals("First result unavailable key should be 1", Integer.valueOf(1), 
                     firstResult.getUnavailableKeyIndexes().get(0));
        assertTrue("First result should have unavailable keys", firstResult.hasUnavailableKeys());
        
        // Check second subrequest result
        LookupRowsResult<List<YTreeMapNode>> secondResult = actualResults.get(1);
        assertEquals("Second result should have 2 rows", 2, secondResult.getRowset().size());
        assertTrue("Second result should have no unavailable keys", 
                   secondResult.getUnavailableKeyIndexes().isEmpty());
        assertFalse("Second result should not have unavailable keys", secondResult.hasUnavailableKeys());
    }
}