package com.appgiuaky.gpaplanner.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class DataFileTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private File target() {
        return new File(folder.getRoot(), "academic.json");
    }

    @Test
    public void readMissingFileReturnsNull() throws Exception {
        assertNull(new DataFile(target()).read());
    }

    @Test
    public void writeThenReadRoundTripsUtf8() throws Exception {
        DataFile file = new DataFile(target());
        file.write("{\"name\":\"Giải tích 1\"}");
        assertEquals("{\"name\":\"Giải tích 1\"}", file.read());
    }

    @Test
    public void writeReplacesExistingContentAndLeavesNoTempFile() throws Exception {
        DataFile file = new DataFile(target());
        file.write("a much longer first version");
        file.write("short");
        assertEquals("short", file.read());
        assertFalse(new File(target().getPath() + ".tmp").exists());
    }

    @Test
    public void quarantineMovesFileAside() throws Exception {
        DataFile file = new DataFile(target());
        file.write("not json");
        File moved = file.quarantine();
        assertTrue(moved.exists());
        assertNull(file.read());
        assertEquals("not json", new DataFile(moved).read());
    }
}
