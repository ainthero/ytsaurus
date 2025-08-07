package tech.ytsaurus.client;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import tech.ytsaurus.client.bus.BusConnector;
import tech.ytsaurus.client.bus.DefaultBusConnector;
import tech.ytsaurus.client.request.CreateNode;
import tech.ytsaurus.client.request.ModifyRowsRequest;
import tech.ytsaurus.client.request.MultiLookupRowsRequest;
import tech.ytsaurus.client.request.MultiLookupRowsSubrequest;
import tech.ytsaurus.client.rows.LookupRowsResult;
import tech.ytsaurus.client.rows.UnversionedRowset;
import tech.ytsaurus.client.rpc.Compression;
import tech.ytsaurus.client.rpc.RpcCompression;
import tech.ytsaurus.client.rpc.RpcOptions;
import tech.ytsaurus.client.rpc.YTsaurusClientAuth;
import tech.ytsaurus.core.cypress.CypressNodeType;
import tech.ytsaurus.core.cypress.YPath;
import tech.ytsaurus.core.tables.ColumnValueType;
import tech.ytsaurus.core.tables.TableSchema;
import tech.ytsaurus.rpcproxy.ETransactionType;
import tech.ytsaurus.ysontree.YTreeBuilder;
import tech.ytsaurus.ysontree.YTreeNode;

public class EnablePartialResultMultiLookupIntegrationTest extends YTsaurusClientTestBase {
    private YTsaurusClient yt;
    private String tablePath;
    private TableSchema schema;

    @Before
    public void setup() throws IOException {
        var ytFixture = createYtFixture();
        yt = ytFixture.yt;

        yt.waitProxies().join();

        // Create test table
        tablePath = "//tmp/test_enable_partial_result_" + UUID.randomUUID().toString();
        schema = new TableSchema.Builder()
                .addKey("key", ColumnValueType.STRING)
                .addValue("value", ColumnValueType.STRING)
                .build();

        Map<String, YTreeNode> attributes = new HashMap<>();
        attributes.put("dynamic", new YTreeBuilder().value(true).build());
        attributes.put("schema", schema.toYTree());

        yt.createNode(CreateNode.builder()
                .setPath(YPath.simple(tablePath))
                .setType(CypressNodeType.TABLE)
                .setAttributes(attributes)
                .build()
        ).join();
        yt.mountTable(tablePath).join();

        // Wait for table to be mounted
        while (true) {
            String state = yt.getNode(tablePath + "/@tablet_state").join().stringValue();
            if (state.equals("mounted")) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Insert test data
        ModifyRowsRequest req = ModifyRowsRequest.builder()
                .setPath(tablePath)
                .setSchema(schema)
                .addInsert(Arrays.asList("key1", "value1"))
                .addInsert(Arrays.asList("key2", "value2"))
                .addInsert(Arrays.asList("key3", "value3"))
                .build();

        ApiServiceTransactionOptions transactionOptions =
                new ApiServiceTransactionOptions(ETransactionType.TT_TABLET)
                        .setSticky(true);
        ApiServiceTransaction t = yt.startTransaction(transactionOptions).join();

        t.modifyRows(req).join();
        t.commit().join();
    }

    @Test
    public void testMultiLookupWithPartialResultEnabled() {
        // Test multi-lookup with EnablePartialResult = true
        MultiLookupRowsSubrequest subrequest1 = MultiLookupRowsSubrequest.builder()
                .setPath(tablePath)
                .setSchema(schema.toLookup())
                .setEnablePartialResult(true)
                .addFilter("key1")
                .addFilter("key2")
                .addFilter("nonexistent_key")
                .build();

        MultiLookupRowsSubrequest subrequest2 = MultiLookupRowsSubrequest.builder()
                .setPath(tablePath)
                .setSchema(schema.toLookup())
                .setEnablePartialResult(true)
                .addFilter("key3")
                .addFilter("another_nonexistent_key")
                .build();

        MultiLookupRowsRequest request = MultiLookupRowsRequest.builder()
                .addSubrequest(subrequest1)
                .addSubrequest(subrequest2)
                .build();

        List<LookupRowsResult<UnversionedRowset>> results = yt.multiLookupRowsWithPartialResult(request).join();

        Assert.assertEquals("Should have 2 results", 2, results.size());

        // First subrequest: key1, key2 should exist, nonexistent_key should not
        LookupRowsResult<UnversionedRowset> result1 = results.get(0);
        Assert.assertEquals("First result should have 2 rows", 2, result1.getRowset().getYTreeRows().size());
        Assert.assertTrue("First result should have unavailable keys", result1.hasUnavailableKeys());
        Assert.assertEquals("First result should have 1 unavailable key", 1, result1.getUnavailableKeyIndexes().size());
        Assert.assertEquals("Unavailable key should be at index 2", Integer.valueOf(2), result1.getUnavailableKeyIndexes().get(0));

        // Second subrequest: key3 should exist, another_nonexistent_key should not
        LookupRowsResult<UnversionedRowset> result2 = results.get(1);
        Assert.assertEquals("Second result should have 1 row", 1, result2.getRowset().getYTreeRows().size());
        Assert.assertTrue("Second result should have unavailable keys", result2.hasUnavailableKeys());
        Assert.assertEquals("Second result should have 1 unavailable key", 1, result2.getUnavailableKeyIndexes().size());
        Assert.assertEquals("Unavailable key should be at index 1", Integer.valueOf(1), result2.getUnavailableKeyIndexes().get(0));

        // Verify the actual row values
        String firstResultRows = result1.getRowset().getYTreeRows().toString();
        Assert.assertTrue("First result should contain key1 and key2", 
                firstResultRows.contains("key1") && firstResultRows.contains("key2"));

        String secondResultRows = result2.getRowset().getYTreeRows().toString();
        Assert.assertTrue("Second result should contain key3", secondResultRows.contains("key3"));
    }

    @Test
    public void testMultiLookupWithPartialResultDisabled() {
        // Test multi-lookup with EnablePartialResult = false (default behavior)
        MultiLookupRowsSubrequest subrequest = MultiLookupRowsSubrequest.builder()
                .setPath(tablePath)
                .setSchema(schema.toLookup())
                .setEnablePartialResult(false)
                .addFilter("key1")
                .addFilter("key2")
                .build();

        MultiLookupRowsRequest request = MultiLookupRowsRequest.builder()
                .addSubrequest(subrequest)
                .build();

        List<LookupRowsResult<UnversionedRowset>> results = yt.multiLookupRowsWithPartialResult(request).join();

        Assert.assertEquals("Should have 1 result", 1, results.size());

        LookupRowsResult<UnversionedRowset> result = results.get(0);
        Assert.assertEquals("Result should have 2 rows", 2, result.getRowset().getYTreeRows().size());
        Assert.assertFalse("Result should not have unavailable keys", result.hasUnavailableKeys());
        Assert.assertEquals("Result should have 0 unavailable keys", 0, result.getUnavailableKeyIndexes().size());
    }

    @Test
    public void testMultiLookupMixedPartialResultSettings() {
        // Test multi-lookup with mixed EnablePartialResult settings
        MultiLookupRowsSubrequest subrequestWithPartial = MultiLookupRowsSubrequest.builder()
                .setPath(tablePath)
                .setSchema(schema.toLookup())
                .setEnablePartialResult(true)
                .addFilter("key1")
                .addFilter("nonexistent_key")
                .build();

        MultiLookupRowsSubrequest subrequestWithoutPartial = MultiLookupRowsSubrequest.builder()
                .setPath(tablePath)
                .setSchema(schema.toLookup())
                .setEnablePartialResult(false)
                .addFilter("key2")
                .addFilter("key3")
                .build();

        MultiLookupRowsRequest request = MultiLookupRowsRequest.builder()
                .addSubrequest(subrequestWithPartial)
                .addSubrequest(subrequestWithoutPartial)
                .build();

        List<LookupRowsResult<UnversionedRowset>> results = yt.multiLookupRowsWithPartialResult(request).join();

        Assert.assertEquals("Should have 2 results", 2, results.size());

        // First subrequest with partial result enabled
        LookupRowsResult<UnversionedRowset> resultWithPartial = results.get(0);
        Assert.assertEquals("First result should have 1 row", 1, resultWithPartial.getRowset().getYTreeRows().size());
        Assert.assertTrue("First result should have unavailable keys", resultWithPartial.hasUnavailableKeys());
        Assert.assertEquals("First result should have 1 unavailable key", 1, resultWithPartial.getUnavailableKeyIndexes().size());

        // Second subrequest with partial result disabled
        LookupRowsResult<UnversionedRowset> resultWithoutPartial = results.get(1);
        Assert.assertEquals("Second result should have 2 rows", 2, resultWithoutPartial.getRowset().getYTreeRows().size());
        Assert.assertFalse("Second result should not have unavailable keys", resultWithoutPartial.hasUnavailableKeys());
        Assert.assertEquals("Second result should have 0 unavailable keys", 0, resultWithoutPartial.getUnavailableKeyIndexes().size());
    }
}