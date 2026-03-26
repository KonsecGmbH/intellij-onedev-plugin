package com.konsec.intellij;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Parses the OneDev binary build log streaming protocol.
 * <p>
 * Protocol per frame:
 * <ul>
 *   <li>Read 4-byte big-endian int n</li>
 *   <li>n == 0: heartbeat, continue</li>
 *   <li>n &lt; 0: read |n| bytes as UTF-8 status string</li>
 *   <li>n &gt; 0: read n bytes as JSON log entry {"date": ms, "messages": [{"style": "...", "text": "..."}]}</li>
 * </ul>
 */
public class OneDevBuildLogReader {

    public interface LogCallback {
        void onLogEntry(Date date, List<LogMessage> messages);
        void onStatusChange(String status);
        void onComplete();
        void onError(Exception e);
    }

    public static class LogMessage {
        public final String style;
        public final String text;

        public LogMessage(String style, String text) {
            this.style = style;
            this.text = text;
        }
    }

    public static void read(InputStream inputStream, LogCallback callback) {
        try (var dis = new DataInputStream(inputStream)) {
            while (true) {
                int n;
                try {
                    n = dis.readInt();
                } catch (EOFException e) {
                    callback.onComplete();
                    return;
                }

                if (n == 0) {
                    // Heartbeat — continue
                } else if (n < 0) {
                    int length = -n;
                    byte[] bytes = new byte[length];
                    dis.readFully(bytes);
                    callback.onStatusChange(new String(bytes, StandardCharsets.UTF_8));
                } else {
                    byte[] bytes = new byte[n];
                    dis.readFully(bytes);
                    String json = new String(bytes, StandardCharsets.UTF_8);

                    JsonObject entry = JsonParser.parseString(json).getAsJsonObject();
                    Date date = new Date(entry.get("date").getAsLong());

                    List<LogMessage> messages = new ArrayList<>();
                    JsonArray messagesArray = entry.getAsJsonArray("messages");
                    for (int i = 0; i < messagesArray.size(); i++) {
                        JsonObject msg = messagesArray.get(i).getAsJsonObject();
                        String style = msg.has("style") && !msg.get("style").isJsonNull()
                                ? msg.get("style").getAsString() : null;
                        String text = msg.get("text").getAsString();
                        messages.add(new LogMessage(style, text));
                    }
                    callback.onLogEntry(date, messages);
                }
            }
        } catch (Exception e) {
            callback.onError(e);
        }
    }
}
