package com.konsec.intellij;

import com.google.gson.JsonParser;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.testFramework.LightPlatform4TestCase;
import com.intellij.util.xmlb.XmlSerializer;
import com.konsec.intellij.model.OneDevComment;
import com.konsec.intellij.model.OneDevProject;
import com.konsec.intellij.model.OneDevTaskCreateData;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;

import static com.konsec.intellij.OneDevRepository.gson;

public class OneDevRepositoryTest extends LightPlatform4TestCase {

    private static String URL;
    private static String MTLS_URL;
    private static String TOKEN;
    private static String USERNAME;
    private static String PASSWORD;

    private OneDevRepository repository;

    private TaskManagerImpl myTaskManager;

    private static String getenv(String key, String defaultValue) {
        var value = System.getenv(key);
        return value != null && !value.isEmpty() ? value : defaultValue;
    }

    private static void setUpOneDev() throws IOException, InterruptedException {
        if (URL != null && TOKEN != null) {
            return;
        }

        URL = getenv("ONEDEV_URL", "http://127.0.0.1:6610/");
        MTLS_URL = getenv("ONEDEV_MTLS_URL", "https://127.0.0.1:8443/");
        USERNAME = getenv("ONEDEV_USERNAME", "test");
        PASSWORD = getenv("ONEDEV_PASSWORD", "test");

        var token = getenv("ONEDEV_TOKEN", null);
        if (token == null) {

            for (int i = 0; i < 100; i++) {
                try {
                    token = issueAccessToken(USERNAME, PASSWORD);
                    break;
                } catch (SocketException e) {
                    // Ignore, docker container is staring
                    Thread.sleep(1000);
                }
            }
        }
        TOKEN = token;
    }

    private static String issueAccessToken(String username, String password) throws IOException {
        var accessToken = new AccessTokenDto();

        var usernamePassword = (username + ":" + password).getBytes(StandardCharsets.UTF_8);

        // Issue token
        var endpointUrl = URL + "~api/access-tokens";
        var req = new HttpPost(endpointUrl);
        req.setEntity(new StringEntity(gson.toJson(accessToken)));
        req.addHeader("Content-Type", "application/json");
        req.addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(usernamePassword));
        var httpClient = HttpClientBuilder.create().build();
        var resp = httpClient.execute(req);
        var tokenId = Long.parseLong(EntityUtils.toString(resp.getEntity()));

        // Get token value
        endpointUrl = URL + "~api/users/1/access-tokens";
        var getReq = new HttpGet(endpointUrl);
        getReq.addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(usernamePassword));
        resp = httpClient.execute(getReq);
        var respJson = EntityUtils.toString(resp.getEntity());
        var tree = JsonParser.parseString(respJson);
        var tokenArray = tree.getAsJsonArray();
        for (int i = 0; i < tokenArray.size(); i++) {
            var tokenObject = tokenArray.get(i).getAsJsonObject();
            if (tokenId == tokenObject.getAsJsonPrimitive("id").getAsLong()) {
                return tokenObject.getAsJsonPrimitive("value").getAsString();
            }
        }

        throw new IllegalStateException(tree.toString());
    }

    private void initTestProject() throws IOException {
        OneDevProject project = new OneDevProject();
        project.name = "test";
        repository.createProject(project);
    }

    private void initTestIssues(OneDevProject project) throws IOException {
        OneDevTaskCreateData task = new OneDevTaskCreateData();
        task.projectId = project.id;
        task.title = "Issue 1";
        task.description = "Issue 1 Description";
        int taskId = repository.createTask(task);

        OneDevComment comment = new OneDevComment();
        comment.content = "Test Comment";
        comment.userId = 1;
        comment.issueId = taskId;
        repository.createTaskComment(comment);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();

        myTaskManager = (TaskManagerImpl) TaskManager.getManager(getProject());
        myTaskManager.prepareForNextTest();

        setUpOneDev();
    }

    private OneDevRepository initRepository(boolean useAccessToken, boolean useMutualTls) {
        repository = new OneDevRepository();
        repository.setUseAccessToken(useAccessToken);
        if (useMutualTls) {
            repository.setUseMutualTls(true);
            repository.setMutualTlsCertificatePassword("test");
            repository.setMutualTlsCertificatePath(new File("src/test/docker/certs/client.pfx").getAbsolutePath());
            repository.setUrl(MTLS_URL);
        } else {
            repository.setUrl(URL);
        }
        if (useAccessToken) {
            repository.setPassword(TOKEN);
        } else {
            repository.setUsername(USERNAME);
            repository.setPassword(PASSWORD);
        }
        return repository;
    }

    private Optional<Exception> verifyConnection() {
        var ex = repository.createCancellableConnection().call();
        return Optional.ofNullable(ex);
    }

    @Test
    public void testConnectionMutualTls() {
        initRepository(false, true);
        Assert.assertTrue(repository.isUseMutualTls());
        Assert.assertTrue(repository.getUrl().startsWith("https://"));

        Exception error = verifyConnection().orElse(null);
        if (error != null) {
            error.printStackTrace();
        }
        Assert.assertNull(error);
    }

    @Test
    public void testConnectionUsernamePassword() {
        initRepository(false, false);

        Exception error = verifyConnection().orElse(null);
        if (error != null) {
            error.printStackTrace();
        }
        Assert.assertNull(error);
    }

    @Test
    public void testConnectionUsernamePasswordInvalid() {
        initRepository(false, false);
        repository.setPassword(repository.getPassword() + "1");

        Exception error = verifyConnection().orElse(null);
        Assert.assertNotNull(error);
    }

    @Test
    public void testConnectionToken() {
        initRepository(true, false);

        Exception error = verifyConnection().orElse(null);
        if (error != null) {
            error.printStackTrace();
        }
        Assert.assertNull(error);
    }

    @Test
    public void testConnectionTokenInvalid() {
        initRepository(true, false);
        repository.setPassword(repository.getPassword() + "1");

        Exception error = verifyConnection().orElse(null);
        Assert.assertNotNull(error);
    }

    @Test
    public void testOneDevApiOperations() throws IOException {
        initRepository(true, false);
        var progress = new AbstractProgressIndicatorBase();

        var projects = repository.loadProjects();
        if (projects.isEmpty()) {
            initTestProject();
        }
        projects = repository.loadProjects();

        var issues = repository.getIssues(null, 0, 100, false, progress);
        if (issues.length == 0) {
            initTestIssues(projects.get(0));
        }

        // Get issues
        issues = repository.getIssues(null, 0, 100, true, progress);
        Assert.assertTrue(issues.length > 0);

        // Get issue comments
        var totalComments = 0;
        for (var issue : issues) {
            totalComments += issue.getComments().length;
        }
        Assert.assertTrue(totalComments > 0);

        var issue = issues[0];
        // Filter by closed
        var issueWasOpen = !issue.isClosed();
        var foundBefore = Arrays.stream(repository.getIssues(null, 0, 100, false, progress))
                .filter(t -> t.getNumber().equals(issue.getNumber()))
                .findFirst();
        Assert.assertEquals(issueWasOpen, foundBefore.isPresent());

        // Set task state
        repository.setTaskState(issue, issue.isClosed() ? OneDevRepository.DEFAULT_STATE_OPEN : OneDevRepository.DEFAULT_STATE_CLOSED);

        // Filter by closed after state change
        var foundAfter = Arrays.stream(repository.getIssues(null, 0, 100, false, progress))
                .filter(t -> t.getNumber().equals(issue.getNumber()))
                .findFirst();
        Assert.assertEquals(!issueWasOpen, foundAfter.isPresent());

        // Find task
        var foundTask = repository.findTask(issue.getSummary());
        Assert.assertNotNull(foundTask);
    }

    @Test
    public void testSerialization() throws IOException, JDOMException {
        for (var mutualTls : new boolean[]{true, false}) {
            for (var token : new boolean[]{true, false}) {
                var repo = initRepository(token, mutualTls);
                myTaskManager.setRepositories(Collections.singletonList(repo));

                TaskManagerImpl.Config config = myTaskManager.getState();
                Element element = XmlSerializer.serialize(config);
                Element element1 = JDOMUtil.load(JDOMUtil.writeElement(element));
                TaskManagerImpl.Config deserialize = XmlSerializer.deserialize(element1, TaskManagerImpl.Config.class);
                myTaskManager.loadState(deserialize);

                var loadedRepo =  myTaskManager.getAllRepositories();
                Assert.assertEquals(1, loadedRepo.length);
                // Not same
                Assert.assertNotSame(repo, loadedRepo[0]);
                // But equal
                Assert.assertEquals(repo, loadedRepo[0]);
            }
        }
    }
}
