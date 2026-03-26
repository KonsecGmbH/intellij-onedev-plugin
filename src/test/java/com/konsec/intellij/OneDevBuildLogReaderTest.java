package com.konsec.intellij;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class OneDevBuildLogReaderTest {

    // --- helpers ---

    private static byte[] statusFrame(String status) throws IOException {
        byte[] bytes = status.getBytes(StandardCharsets.UTF_8);
        var baos = new ByteArrayOutputStream();
        var dos = new DataOutputStream(baos);
        dos.writeInt(-bytes.length);
        dos.write(bytes);
        return baos.toByteArray();
    }

    private static byte[] heartbeatFrame() throws IOException {
        var baos = new ByteArrayOutputStream();
        new DataOutputStream(baos).writeInt(0);
        return baos.toByteArray();
    }

    private static byte[] logFrame(long date, String style, String text) throws IOException {
        String json = "{\"date\":" + date + ",\"messages\":[{\"style\":\"" + style + "\",\"text\":\"" + text + "\"}]}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        var baos = new ByteArrayOutputStream();
        var dos = new DataOutputStream(baos);
        dos.writeInt(bytes.length);
        dos.write(bytes);
        return baos.toByteArray();
    }

    private static byte[] concat(byte[]... parts) throws IOException {
        var baos = new ByteArrayOutputStream();
        for (var p : parts) baos.write(p);
        return baos.toByteArray();
    }

    // --- tests ---

    @Test
    public void testEmptyStream_callsOnComplete() throws IOException {
        var completed = new boolean[]{false};

        OneDevBuildLogReader.read(new ByteArrayInputStream(new byte[0]), new OneDevBuildLogReader.LogCallback() {
            @Override
            public void onLogEntry(Date date, List<OneDevBuildLogReader.LogMessage> messages) {
                Assert.fail("Unexpected log entry");
            }

            @Override
            public void onStatusChange(String status) {
                Assert.fail("Unexpected status");
            }

            @Override
            public void onComplete() {
                completed[0] = true;
            }

            @Override
            public void onError(Exception e) {
                Assert.fail("Unexpected error: " + e.getMessage());
            }
        });

        Assert.assertTrue(completed[0]);
    }

    @Test
    public void testHeartbeat_ignored() throws IOException {
        var completed = new boolean[]{false};

        OneDevBuildLogReader.read(new ByteArrayInputStream(heartbeatFrame()), new OneDevBuildLogReader.LogCallback() {
            @Override
            public void onLogEntry(Date date, List<OneDevBuildLogReader.LogMessage> messages) {
                Assert.fail("Unexpected log entry");
            }

            @Override
            public void onStatusChange(String status) {
                Assert.fail("Unexpected status");
            }

            @Override
            public void onComplete() {
                completed[0] = true;
            }

            @Override
            public void onError(Exception e) {
                Assert.fail("Unexpected error: " + e.getMessage());
            }
        });

        Assert.assertTrue(completed[0]);
    }

    @Test
    public void testStatusEntry_parsedCorrectly() throws IOException {
        var statuses = new ArrayList<String>();

        OneDevBuildLogReader.read(new ByteArrayInputStream(statusFrame("RUNNING")),
                new OneDevBuildLogReader.LogCallback() {
                    @Override
                    public void onLogEntry(Date date, List<OneDevBuildLogReader.LogMessage> messages) {
                        Assert.fail("Unexpected log entry");
                    }

                    @Override
                    public void onStatusChange(String status) {
                        statuses.add(status);
                    }

                    @Override
                    public void onComplete() {}

                    @Override
                    public void onError(Exception e) {
                        Assert.fail("Unexpected error: " + e.getMessage());
                    }
                });

        Assert.assertEquals(1, statuses.size());
        Assert.assertEquals("RUNNING", statuses.get(0));
    }

    @Test
    public void testLogEntry_parsedCorrectly() throws IOException {
        long now = 1_700_000_000_000L;
        var entries = new ArrayList<OneDevBuildLogReader.LogMessage>();

        OneDevBuildLogReader.read(new ByteArrayInputStream(logFrame(now, "error", "build failed")),
                new OneDevBuildLogReader.LogCallback() {
                    @Override
                    public void onLogEntry(Date date, List<OneDevBuildLogReader.LogMessage> messages) {
                        Assert.assertEquals(now, date.getTime());
                        entries.addAll(messages);
                    }

                    @Override
                    public void onStatusChange(String status) {
                        Assert.fail("Unexpected status");
                    }

                    @Override
                    public void onComplete() {}

                    @Override
                    public void onError(Exception e) {
                        Assert.fail("Unexpected error: " + e.getMessage());
                    }
                });

        Assert.assertEquals(1, entries.size());
        Assert.assertEquals("error", entries.get(0).style);
        Assert.assertEquals("build failed", entries.get(0).text);
    }

    @Test
    public void testCombinedStream_allFramesParsed() throws IOException {
        long now = 1_700_000_000_000L;
        var data = concat(
                heartbeatFrame(),
                statusFrame("RUNNING"),
                logFrame(now, "info", "step 1"),
                heartbeatFrame(),
                logFrame(now + 1000, "error", "step 2 failed"),
                statusFrame("FAILED")
        );

        var statuses = new ArrayList<String>();
        var logMessages = new ArrayList<OneDevBuildLogReader.LogMessage>();

        OneDevBuildLogReader.read(new ByteArrayInputStream(data), new OneDevBuildLogReader.LogCallback() {
            @Override
            public void onLogEntry(Date date, List<OneDevBuildLogReader.LogMessage> messages) {
                logMessages.addAll(messages);
            }

            @Override
            public void onStatusChange(String status) {
                statuses.add(status);
            }

            @Override
            public void onComplete() {}

            @Override
            public void onError(Exception e) {
                Assert.fail("Unexpected error: " + e.getMessage());
            }
        });

        Assert.assertEquals(List.of("RUNNING", "FAILED"), statuses);
        Assert.assertEquals(2, logMessages.size());
        Assert.assertEquals("step 1", logMessages.get(0).text);
        Assert.assertEquals("step 2 failed", logMessages.get(1).text);
        Assert.assertEquals("error", logMessages.get(1).style);
    }
}
