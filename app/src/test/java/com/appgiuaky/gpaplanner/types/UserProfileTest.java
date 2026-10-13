package com.appgiuaky.gpaplanner.types;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UserProfileTest {

    @Test
    public void displayNameUsesAccountName() {
        assertEquals("Nguyễn Văn A", new UserProfile("Nguyễn Văn A", "a@gmail.com").displayName());
    }

    @Test
    public void displayNameTrimsName() {
        assertEquals("Bình", new UserProfile("  Bình ", "b@gmail.com").displayName());
    }

    @Test
    public void displayNameFallsBackToEmailWhenNameMissing() {
        assertEquals("c@gmail.com", new UserProfile(null, "c@gmail.com").displayName());
    }

    @Test
    public void displayNameFallsBackToEmailWhenNameBlank() {
        assertEquals("d@gmail.com", new UserProfile("   ", "d@gmail.com").displayName());
    }
}
