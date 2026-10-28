package com.shivemn.ccp.replay;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a CSV file one record at a time. Handles quoted fields, commas and line breaks inside quotes,
 * and doubled quotes ("") as an escaped quote. Streams the file, so a multi-gigabyte file is fine.
 */
public class CsvRecordReader implements Closeable {

    private final BufferedReader reader;

    public CsvRecordReader(BufferedReader reader) {
        this.reader = reader;
    }

    /** The next record's fields, or null at the end of the file. */
    public List<String> next() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return null;
        }
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        while (true) {
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (inQuotes) {
                    if (c == '"') {
                        if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                            field.append('"');
                            i++;
                        } else {
                            inQuotes = false;
                        }
                    } else {
                        field.append(c);
                    }
                } else if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(field.toString());
                    field.setLength(0);
                } else {
                    field.append(c);
                }
            }
            if (!inQuotes) {
                break;
            }
            // The quoted field continues on the next line.
            String more = reader.readLine();
            if (more == null) {
                break;
            }
            field.append('\n');
            line = more;
        }
        fields.add(field.toString());
        return fields;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
