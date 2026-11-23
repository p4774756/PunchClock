package com.example.server.store;

import com.example.server.store.DevStatusModels.IssueRow;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SecretarySpeechTest {

    @Test
    public void extractsSecretaryLineFromBody() {
        String body = "秘書說：正在把工作狀態整理成公開 API\n\n## 目標\n";
        assertEquals("正在把工作狀態整理成公開 API", SecretarySpeech.extractFromIssueBody(body));
    }

    @Test
    public void fullWidthColonAccepted() {
        assertEquals("你好", SecretarySpeech.extractFromIssueBody("秘書說：你好"));
    }

    @Test
    public void activeFallbackWithoutLine() {
        IssueRow row = new IssueRow(1, "不應出現的標題", true, 0, 0, null, Collections.emptyList());
        assertEquals(SecretarySpeech.ACTIVE_FALLBACK, SecretarySpeech.phraseForActive(row));
    }

    @Test
    public void completedFallsBackToIssueTitle() {
        IssueRow row = new IssueRow(413, "排隊搬進秘書卡", true, 0, 0, null, Collections.emptyList());
        assertEquals("排隊搬進秘書卡", SecretarySpeech.phraseForCompleted(row));
    }

    @Test
    public void completedStripsLeadingProgressive() {
        IssueRow row = new IssueRow(5, "標題", true, 0, 0, null, Collections.emptyList(), "正在寫 API");
        assertEquals("寫 API", SecretarySpeech.phraseForCompleted(row));
    }

    @Test
    public void rejectsPathInPhrase() {
        assertNull(SecretarySpeech.sanitizePhrase("see /Users/tina/secret"));
    }
}