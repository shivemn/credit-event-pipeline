package com.shivemn.ccp.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvRecordReaderTest {

    private static CsvRecordReader reader(String text) {
        return new CsvRecordReader(new BufferedReader(new StringReader(text)));
    }

    @Test
    void readsPlainRecords() throws IOException {
        CsvRecordReader csv = reader("a,b,c\n1,2,3\n");
        assertEquals(List.of("a", "b", "c"), csv.next());
        assertEquals(List.of("1", "2", "3"), csv.next());
        assertNull(csv.next());
    }

    @Test
    void keepsCommasQuotesAndLineBreaksInsideQuotedFields() throws IOException {
        CsvRecordReader csv = reader("1,\"hello, world\",\"say \"\"hi\"\"\",\"two\nlines\",end\n2,x\n");
        assertEquals(List.of("1", "hello, world", "say \"hi\"", "two\nlines", "end"), csv.next());
        assertEquals(List.of("2", "x"), csv.next());
        assertNull(csv.next());
    }

    @Test
    void keepsEmptyFields() throws IOException {
        assertEquals(List.of("a", "", "c", ""), reader("a,,c,\n").next());
    }
}
