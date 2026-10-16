package com.appgiuaky.gpaplanner.types;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CourseTest {

    @Test
    public void creditsMustBeWholeNumberInRange() {
        assertFalse(Course.isValidCredits(0));
        assertTrue(Course.isValidCredits(1));
        assertTrue(Course.isValidCredits(20));
        assertFalse(Course.isValidCredits(21));
        assertFalse(Course.isValidCredits(3.7));
    }
}
