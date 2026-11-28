package com.example.server.store;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GithubCiChecksTest {

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    public void allCompletedSuccessNeutralSkippedPass() {
        assertTrue(GithubDevStatusClient.allChecksPassed(json(
                "{\"check_runs\":[{\"status\":\"completed\",\"conclusion\":\"success\"},"
                        + "{\"status\":\"completed\",\"conclusion\":\"neutral\"},"
                        + "{\"status\":\"completed\",\"conclusion\":\"skipped\"}]}")));
    }

    @Test
    public void pendingFailedOrEmptyDoNotPass() {
        assertFalse(GithubDevStatusClient.allChecksPassed(json(
                "{\"check_runs\":[{\"status\":\"completed\",\"conclusion\":\"success\"},"
                        + "{\"status\":\"in_progress\",\"conclusion\":null}]}")));
        assertFalse(GithubDevStatusClient.allChecksPassed(json(
                "{\"check_runs\":[{\"status\":\"completed\",\"conclusion\":\"failure\"}]}")));
        assertFalse(GithubDevStatusClient.allChecksPassed(json(
                "{\"check_runs\":[{\"status\":\"completed\",\"conclusion\":\"cancelled\"}]}")));
        assertFalse(GithubDevStatusClient.allChecksPassed(json("{\"check_runs\":[]}")));
        assertFalse(GithubDevStatusClient.allChecksPassed(json("{}")));
        assertFalse(GithubDevStatusClient.allChecksPassed(null));
    }

    @Test
    public void headShaReadFromPullObject() {
        assertEquals("abc123", GithubDevStatusClient.headSha(json("{\"head\":{\"sha\":\"abc123\"}}")));
        assertNull(GithubDevStatusClient.headSha(json("{\"head\":{}}")));
        assertNull(GithubDevStatusClient.headSha(json("{}")));
        assertNull(GithubDevStatusClient.headSha(null));
    }

    @Test
    public void pullRowWithCiPassedKeepsOtherFields() {
        DevStatusModels.PullRow base = new DevStatusModels.PullRow(1, "t", true, false, false, 1, 2, null, null,
                java.util.Collections.singleton(5), true);
        DevStatusModels.PullRow passed = base.withCiPassed(true);
        assertTrue(passed.ciPassed);
        assertTrue(passed.conflicting);
        assertEquals(1, passed.number);
        assertFalse(base.ciPassed);
    }
}
