package com.example;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ChatProtocolPeerImageTest {

    @Test
    public void joinsAndSplitsSeveralImages() {
        String field = ChatProtocol.joinPeerImages(Arrays.asList("imgA", "imgB"));
        assertEquals("imgA,imgB", field);
        assertEquals(Arrays.asList("imgA", "imgB"), ChatProtocol.splitPeerImages(field));
        assertEquals(Collections.emptyList(), ChatProtocol.splitPeerImages(""));
        assertEquals(Collections.singletonList("imgA"), ChatProtocol.splitPeerImages("imgA"));
    }

    @Test
    public void rejectsBrokenFieldsAndOverLimit() {
        assertNull(ChatProtocol.splitPeerImages("imgA,,imgB"));
        assertNull(ChatProtocol.splitPeerImages("has/slash"));
        assertNull(ChatProtocol.splitPeerImages("A,B,C,D,E,F,G"));

        List<String> existing = Arrays.asList("aa", "bb");
        assertNull(ChatProtocol.rejectExtraPeerImage(existing, "cc"));
        assertEquals("一則訊息最多 " + ChatProtocol.MAX_PEER_IMAGES + " 張圖片",
                ChatProtocol.rejectExtraPeerImage(Arrays.asList("a", "b", "c", "d", "e", "f"), "g"));
        String big = "A".repeat(ChatProtocol.MAX_PEER_IMAGE_CHARS);
        assertEquals("這幾張圖加起來超過大小上限，這張沒有附上",
                ChatProtocol.rejectExtraPeerImage(Collections.singletonList("A"), big));
    }
}
