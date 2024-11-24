package com.konsec.intellij;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.Comment;
import com.intellij.tasks.CustomTaskState;
import com.intellij.tasks.Task;
import com.intellij.tasks.impl.RequestFailedException;
import com.intellij.tasks.impl.SimpleComment;
import com.intellij.tasks.impl.gson.TaskGsonUtil;
import com.intellij.tasks.impl.httpclient.NewBaseRepositoryImpl;
import com.intellij.tasks.impl.httpclient.TaskResponseUtil;
import com.intellij.util.containers.ContainerUtil;
import com.konsec.intellij.model.OneDevComment;
import com.konsec.intellij.model.OneDevProject;
import com.konsec.intellij.model.OneDevTask;
import com.konsec.intellij.model.OneDevTaskCreateData;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OneDevRepository extends NewBaseRepositoryImpl {
    public static final Gson gson = TaskGsonUtil.createDefaultBuilder().create();

    public static final int MAX_COUNT = 100;

    public static final CustomTaskState STATE_OPEN = new CustomTaskState("Open", "Open");
    public static final CustomTaskState STATE_CLOSED = new CustomTaskState("Closed", "Closed");

    private static final TypeToken<List<OneDevTask>> LIST_OF_TASKS_TYPE = new TypeToken<>() {
    };
    private static final TypeToken<List<OneDevComment>> LIST_OF_COMMENTS_TYPE = new TypeToken<>() {
    };
    private static final TypeToken<List<OneDevProject>> LIST_OF_PROJECTS_TYPE = new TypeToken<>() {
    };

    private HttpClient httpClient;

    private boolean assigned = false;

    public OneDevRepository(String url, String token, HttpClient httpClient) {
        this();

        setUrl(url);
        setPassword(token);
        this.httpClient = httpClient;

        init();
    }

    public OneDevRepository() {
        super(new OneDevRepositoryType());
        init();
    }

    public OneDevRepository(OneDevRepository other) {
        super(other);
        setPassword(other.getPassword());
        assigned = other.assigned;
        init();
    }

    private void init() {
        setPreferredOpenTaskState(STATE_OPEN);
        setPreferredCloseTaskState(STATE_CLOSED);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof OneDevRepository other)) {
            return false;
        }
        return Objects.equals(getPassword(), other.getPassword()) &&
                Objects.equals(getUrl(), other.getUrl()) &&
                Objects.equals(assigned, other.assigned);
    }

    @NotNull
    @Override
    public OneDevRepository clone() {
        return new OneDevRepository(this);
    }

    @Override
    protected @NotNull HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = super.getHttpClient();
        }
        return httpClient;
    }

    @Nullable
    @Override
    public CancellableConnection createCancellableConnection() {
        return new HttpTestConnection(new HttpGet()) {
            @Override
            protected void doTest() throws Exception {
                myCurrentRequest = initProjectsRequest(0, 1);
                addAuthHeader(myCurrentRequest);
                super.doTest();
            }
        };
    }

    @Nullable
    @Override
    public Task findTask(@NotNull String s) throws IOException {
        var found = findIssues(s, 0, 1, true);
        return found.length > 0 ? found[0] : null;
    }

    @Override
    public Task[] getIssues(@Nullable String query, int offset, int limit, boolean withClosed, @NotNull ProgressIndicator cancelled) throws Exception {
        return findIssues(query, offset, limit, withClosed);
    }

    @Nullable
    @Override
    public String extractId(@NotNull String taskName) {
        Matcher matcher = Pattern.compile("(d+)").matcher(taskName);
        return matcher.find() ? matcher.group(1) : null;
    }

    @NotNull
    @Override
    public Set<CustomTaskState> getAvailableTaskStates(@NotNull Task task) {
        Set<CustomTaskState> result = new HashSet<>();
        result.add(STATE_OPEN);
        result.add(STATE_CLOSED);
        return result;
    }

    @Override
    public void setTaskState(@NotNull Task task, @NotNull CustomTaskState state) throws Exception {
        var endpointUrl = getRestApiUrl("issues", task.getId(), "state-transitions");
        var req = new HttpPost(endpointUrl);
        req.setEntity(new StringEntity(gson.toJson(new StateTransitionData(state.getId()))));
        req.addHeader("Content-Type", "application/json");
        addAuthHeader(req);

        var resp = getHttpClient().execute(req);
        throwOnError(resp);
    }

    @Override
    protected int getFeatures() {
        return STATE_UPDATING + BASIC_HTTP_AUTHORIZATION + NATIVE_SEARCH;
    }

    @Override
    public boolean isConfigured() {
        if (!super.isConfigured()) {
            return false;
        }
        if (StringUtil.isEmpty(getPassword())) {
            return false;
        }
        return true;
    }

    private Task[] findIssues(String query, int offset, int limit, boolean withClosed) throws IOException {
        offset = Math.max(offset, 0);
        if (limit <= 0) {
            limit = MAX_COUNT;
        }
        limit = Math.min(limit, MAX_COUNT);

        URI endpointUrl;
        try {
            if (StringUtil.isEmpty(query)) {
                query = withClosed ? "" : "\"State\" is \"Open\"";
            } else {
                query = " ~ \"" + query.replace("\"", "")  + "\" ~";
            }
            endpointUrl = (new URIBuilder(getRestApiUrl("issues")))
                    .addParameter("query", query)
                    .addParameter("offset", String.valueOf(offset))
                    .addParameter("count", String.valueOf(limit))
                    .build();
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        var req = new HttpGet(endpointUrl);
        addAuthHeader(req);

        List<OneDevTask> tasks = (List<OneDevTask>) getHttpClient().execute(req, new TaskResponseUtil.GsonMultipleObjectsDeserializer(gson, LIST_OF_TASKS_TYPE));
        return ContainerUtil.map2Array(tasks, OneDevTaskImpl.class, (task) -> new OneDevTaskImpl(this, task));
    }

    public List<OneDevProject> loadProjects() throws IOException {
        URI endpointUrl;
        try {
            endpointUrl = (new URIBuilder(getRestApiUrl("projects")))
                    .addParameter("offset", String.valueOf(0))
                    .addParameter("count", String.valueOf(MAX_COUNT))
                    .build();
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        var req = new HttpGet(endpointUrl);
        addAuthHeader(req);

        return (List<OneDevProject>) getHttpClient().execute(req, new TaskResponseUtil.GsonMultipleObjectsDeserializer(gson, LIST_OF_PROJECTS_TYPE));
    }

    public int createProject(OneDevProject project) throws IOException {
        var endpointUrl = getRestApiUrl("projects");
        return createEntity(endpointUrl, project);
    }

    public int createTask(OneDevTaskCreateData task) throws IOException {
        var endpointUrl = getRestApiUrl("issues");
        return createEntity(endpointUrl, task);
    }

    public int createTaskComment(OneDevComment task) throws IOException {
        var endpointUrl = getRestApiUrl("issue-comments");
        return createEntity(endpointUrl, task);
    }

    private int createEntity(String endpointUrl, Object payload) throws IOException {
        var req = new HttpPost(endpointUrl);
        req.setEntity(new StringEntity(gson.toJson(payload)));
        req.addHeader("Content-Type", "application/json");
        addAuthHeader(req);

        var resp = getHttpClient().execute(req);
        throwOnError(resp);
        return Integer.parseInt(EntityUtils.toString(resp.getEntity()));
    }

    private String getUserName(int userId) {
        return String.valueOf(userId);
    }

    Comment[] getComments(OneDevTaskImpl task) throws IOException {
        var endpointUrl = getRestApiUrl("issues", task.getId(), "comments");
        var req = new HttpGet(endpointUrl);
        addAuthHeader(req);

        List<OneDevComment> comments = (List<OneDevComment>) getHttpClient().execute(req, new TaskResponseUtil.GsonMultipleObjectsDeserializer(gson, LIST_OF_COMMENTS_TYPE));
        return ContainerUtil.map2Array(comments, Comment.class, (comment) -> new SimpleComment(comment.date, getUserName(comment.userId), comment.content));
    }

    @Override
    @NotNull
    public String getRestApiPathPrefix() {
        return "/~api/";
    }

    @Override
    protected @Nullable HttpRequestInterceptor createRequestInterceptor() {
        return (request, context) -> addAuthHeader(request);
    }

    private void throwOnError(HttpResponse response) {
        StatusLine statusLine = response.getStatusLine();
        if (statusLine != null && statusLine.getStatusCode() != 200) {
            throw RequestFailedException.forStatusCode(statusLine.getStatusCode(), statusLine.getReasonPhrase());
        }
    }

    private void addAuthHeader(HttpRequest request) {
        var usernamePassword = ("user:" + getPassword()).getBytes(StandardCharsets.UTF_8);
        request.addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString(usernamePassword));
    }

    private HttpGet initProjectsRequest(int offset, int count) throws URISyntaxException {
        var endpointUrl = (new URIBuilder(getRestApiUrl("projects")))
                .addParameter("offset", String.valueOf(offset))
                .addParameter("count", String.valueOf(count))
                .build();
        return new HttpGet(endpointUrl);
    }

    public static class StateTransitionData {
        public final String state;

        StateTransitionData(String state) {
            this.state = state;
        }
    }
}
