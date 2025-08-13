package tech.ytsaurus.client;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import tech.ytsaurus.client.request.CreateNode;
import tech.ytsaurus.client.request.ModifyRowsRequest;
import tech.ytsaurus.client.request.MountTable;
import tech.ytsaurus.client.request.MultiLookupRowsRequest;
import tech.ytsaurus.client.request.MultiLookupRowsSubrequest;
import tech.ytsaurus.client.request.RemoveNode;
import tech.ytsaurus.client.request.ReshardTable;
import tech.ytsaurus.client.request.UnmountTable;
import tech.ytsaurus.client.request.TabletRangeOptions;
import tech.ytsaurus.client.request.StartTransaction;
import tech.ytsaurus.client.rows.LookupRowsResult;
import tech.ytsaurus.client.rows.UnversionedRowset;
import tech.ytsaurus.client.rpc.YTsaurusClientAuth;
import tech.ytsaurus.core.cypress.CypressNodeType;
import tech.ytsaurus.core.cypress.YPath;
import tech.ytsaurus.core.tables.ColumnValueType;
import tech.ytsaurus.core.tables.TableSchema;
import tech.ytsaurus.ysontree.YTreeBuilder;
import tech.ytsaurus.ysontree.YTreeNode;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * External-cluster integration test that verifies enablePartialResult behavior
 * against moon.yt.vk.team under //home/dev/maksim.krivoshapko/.
 *
 * Run with env:
 * - RUN_EXTERNAL_YT=1
 * - YT_USER=<user>
 * - YT_TOKEN=<secret>
 */
public class EnablePartialResultRemoteClusterTest {
    private static final String DEFAULT_CLUSTER = "moon.yt.vk.team";
    private static final String BASE_DIR = "//home/dev/maksim.krivoshapko";

    private YTsaurusClient yt;
    private String tablePath;
    private TableSchema schema;

    @Before
    public void setUp() {
        Assume.assumeTrue("Set RUN_EXTERNAL_YT=1 to run this test against a real cluster",
                "1".equals(System.getenv("RUN_EXTERNAL_YT")));

        String cluster = System.getenv().getOrDefault("YT_PROXY", DEFAULT_CLUSTER);
        String token = System.getenv("YT_TOKEN");
        String user = System.getenv("YT_USER");

        YTsaurusClientAuth.Builder authBuilder = YTsaurusClientAuth.builder();
        if (user != null && !user.isEmpty()) {
            authBuilder.setUser(user);
        }
        if (token != null && !token.isEmpty()) {
            authBuilder.setToken(token);
        }

        yt = YTsaurusClient.builder()
                .setCluster(cluster)
                .setAuth(authBuilder.build())
                .build();

        yt.waitProxies().join();

        tablePath = BASE_DIR + "/test_enable_partial_result_" + UUID.randomUUID();
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
        yt.mountTableAndWaitTablets(MountTable.builder().setPath(tablePath).setTimeout(Duration.ofSeconds(30)).build()).join();

        ModifyRowsRequest insert = ModifyRowsRequest.builder()
                .setPath(tablePath)
                .setSchema(schema)
                .addInsert(Arrays.asList("key01", "value01"))
                .addInsert(Arrays.asList("key02", "value02"))
                .addInsert(Arrays.asList("key03", "value03"))
                .addInsert(Arrays.asList("key04", "value04"))
                .addInsert(Arrays.asList("key05", "value05"))
                .addInsert(Arrays.asList("key06", "value06"))
                .addInsert(Arrays.asList("key07", "value07"))
                .addInsert(Arrays.asList("key08", "value08"))
                .addInsert(Arrays.asList("key09", "value09"))
                .addInsert(Arrays.asList("key10", "value10"))
                .build();

        ApiServiceTransaction tx = yt.startTransaction(StartTransaction.tablet()).join();
        tx.modifyRows(insert).join();
        tx.commit().join();
    }

    @After
    public void tearDown() {
        if (yt != null && tablePath != null) {
            try {
                yt.removeNode(RemoveNode.builder().setPath(YPath.simple(tablePath)).setRecursive(true).setForce(true).build()).join();
            } catch (Exception ignored) {
            }
        }
        if (yt != null) {
            yt.close();
        }
    }

    @Test
    public void testMultiLookupWithPartialResultEnabled_external() {
        MultiLookupRowsSubrequest s1 = MultiLookupRowsSubrequest.builder()
                .setPath(tablePath)
                .setSchema(schema.toLookup())
                .setEnablePartialResult(true)
                .addFilter("key01")
                .addFilter("key02")
                .addFilter("nonexistent_key")
                .setTimeout(Duration.ofSeconds(2))
                .build();

        MultiLookupRowsSubrequest s2 = MultiLookupRowsSubrequest.builder()
                .setPath(tablePath)
                .setSchema(schema.toLookup())
                .setEnablePartialResult(true)
                .addFilter("key03")
                .addFilter("another_nonexistent_key")
                .setTimeout(Duration.ofSeconds(2))
                .build();

        MultiLookupRowsRequest req = MultiLookupRowsRequest.builder()
                .addSubrequest(s1)
                .addSubrequest(s2)
                .setTimeout(Duration.ofSeconds(3))
                .build();

        List<LookupRowsResult<UnversionedRowset>> results = yt.multiLookupRowsWithPartialResult(req).join();
        assertEquals(2, results.size());

        LookupRowsResult<UnversionedRowset> r1 = results.get(0);
        assertEquals(2, r1.getRowset().getYTreeRows().size());
        assertFalse(r1.hasUnavailableKeys());
        assertEquals(0, r1.getUnavailableKeyIndexes().size());

        LookupRowsResult<UnversionedRowset> r2 = results.get(1);
        assertEquals(1, r2.getRowset().getYTreeRows().size());
        assertFalse(r2.hasUnavailableKeys());
        assertEquals(0, r2.getUnavailableKeyIndexes().size());
    }

    @Test
    public void testMultiLookupWithUnavailableKeys_external() {
        Assume.assumeTrue("Set RUN_EXTERNAL_YT=1 to run this test against a real cluster",
                "1".equals(System.getenv("RUN_EXTERNAL_YT")));

        // Unmount whole table before resharding
        yt.unmountTableAndWaitTablets(UnmountTable.builder().setPath(tablePath).setTimeout(Duration.ofSeconds(40)).build()).join();

        // Reshard into 5 tablets: [start, "key02"), ["key02", "key04"), ["key04", "key06"), ["key06", "key08"), ["key08", end]
        yt.reshardTable(ReshardTable.builder()
                .setPath(tablePath)
                .setSchema(schema.toLookup())
                .addPivotKey(Arrays.asList())        // Start of tablet 0
                .addPivotKey(Arrays.asList("key02")) // Start of tablet 1
                .addPivotKey(Arrays.asList("key04")) // Start of tablet 2
                .addPivotKey(Arrays.asList("key06")) // Start of tablet 3
                .addPivotKey(Arrays.asList("key08")) // Start of tablet 4
                .setTimeout(Duration.ofSeconds(30))
                .build()
        ).join();

        // Mount back
        yt.mountTableAndWaitTablets(MountTable.builder().setPath(tablePath).setTimeout(Duration.ofSeconds(30)).build()).join();

        // Unmount tablet #3 so keys key06, key07 are unavailable
        yt.unmountTable(UnmountTable.builder()
                .setPath(tablePath)
                .setTabletRangeOptions(new TabletRangeOptions(3, 3))
                .setTimeout(Duration.ofSeconds(30))
                .build()
        ).join();
        // Wait specifically for tablet #3 to become unmounted (table overall state stays "mounted")
        waitTabletIndexStateWithTimeout(tablePath, 3, "unmounted", Duration.ofSeconds(30));

        MultiLookupRowsSubrequest sub = MultiLookupRowsSubrequest.builder()
                .setPath(tablePath)
                .setSchema(schema.toLookup())
                .setEnablePartialResult(true)
                .addFilter("key01") // tablet 0 (mounted)
                .addFilter("key03") // tablet 1 (mounted)
                .addFilter("key06") // tablet 3 (unmounted)
                .addFilter("key07") // tablet 3 (unmounted)
                .addFilter("key10") // tablet 4 (mounted)
                .setTimeout(Duration.ofSeconds(2))
                .build();

        MultiLookupRowsRequest req = MultiLookupRowsRequest.builder()
                .addSubrequest(sub)
                .setTimeout(Duration.ofSeconds(3))
                .build();

        // Execute lookup with enablePartialResult=true
        System.out.println("=== TEST INFO ===");
        System.out.println("Table path: " + tablePath);
        System.out.println("Tablet #3 is unmounted, tablets #0,1,2,4 are mounted");
        System.out.println("Lookup keys: key01(tablet0), key03(tablet1), key06(tablet3-unmounted), key07(tablet3-unmounted), key10(tablet4)");
        System.out.println("Expected: enablePartialResult should return unavailableKeyIndexes for key06,key07");
        System.out.println("=================");
        
        List<LookupRowsResult<UnversionedRowset>> res = yt.multiLookupRowsWithPartialResult(req).join();
        assertEquals(1, res.size());

        LookupRowsResult<UnversionedRowset> r = res.get(0);
        
        assertTrue("Should have unavailable keys", r.hasUnavailableKeys());
        assertTrue("Should contain unavailable key index 2 (key06)", r.getUnavailableKeyIndexes().contains(2));
        assertTrue("Should contain unavailable key index 3 (key07)", r.getUnavailableKeyIndexes().contains(3));
    }
    
    private void waitTabletIndexStateWithTimeout(String path, int tabletIndex, String desiredState, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            List<YTreeNode> tablets = yt.getNode(path + "/@tablets").join().asList();
            if (tabletIndex < tablets.size()) {
                Optional<YTreeNode> stateNode = tablets.get(tabletIndex).mapNode().get("state");
                String state = stateNode.map(YTreeNode::stringValue).orElse("");
                if (desiredState.equals(state)) {
                    return;
                }
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for tablet state");
            }
        }
        throw new AssertionError("Timeout waiting for tablet index " + tabletIndex + " to reach state '" + desiredState + "'");
    }
}


