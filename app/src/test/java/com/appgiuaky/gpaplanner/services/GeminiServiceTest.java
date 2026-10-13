package com.appgiuaky.gpaplanner.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.appgiuaky.gpaplanner.services.GeminiService.ChatMessage;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class GeminiServiceTest {

    /** Alternating user/advisor messages "0".."count-1", starting with the user. */
    private static List<ChatMessage> conversation(int count) {
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) messages.add(new ChatMessage(i % 2 == 0, String.valueOf(i)));
        return messages;
    }

    @Test
    public void recentHistoryKeepsShortConversation() {
        assertEquals(3, GeminiService.recentHistory(conversation(3), 20).size());
    }

    @Test
    public void recentHistoryKeepsOnlyLatestMessages() {
        List<ChatMessage> recent = GeminiService.recentHistory(conversation(25), 20);
        assertEquals(19, recent.size());
        assertEquals("6", recent.get(0).text);
        assertEquals("24", recent.get(recent.size() - 1).text);
    }

    @Test
    public void recentHistoryStartsWithUserMessage() {
        // The cut at 20 lands on advisor message "5", which must be dropped
        List<ChatMessage> recent = GeminiService.recentHistory(conversation(25), 20);
        assertTrue(recent.get(0).fromUser);
    }
}
