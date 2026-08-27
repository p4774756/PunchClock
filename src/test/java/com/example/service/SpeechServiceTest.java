package com.example.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SpeechServiceTest {

    @Test
    public void extractsEnglishFromQuoteLine() {
        assertEquals("How you doin'?", SpeechService.toSpeakableEnglish("Joey: \"How you doin'?\""));
        assertEquals("Unagi.", SpeechService.toSpeakableEnglish("Ross: \"Unagi.\""));
        assertEquals("Plain line", SpeechService.toSpeakableEnglish("Plain line"));
    }
}
