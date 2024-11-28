package com.konsec.intellij;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.util.xmlb.annotations.Tag;
import com.konsec.intellij.model.*;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Tag("OneDev")
public class OneDevRepository extends NewBaseRepositoryImpl {
    private static final Logger LOG = Logger.getInstance(OneDevRepository.class);

    public static final Gson gson = TaskGsonUtil.createDefaultBuilder().create();

    public static final int MAX_COUNT = 100;
    public static final int MAX_PROJECTS_TO_LOAD = 500;

    public static final CustomTaskState DEFAULT_STATE_OPEN = new CustomTaskState("Open", "Open");
    public static final CustomTaskState DEFAULT_STATE_CLOSED = new CustomTaskState("Closed", "Closed");

    private static final TypeToken<List<OneDevTask>> LIST_OF_TASKS_TYPE = new TypeToken<>() {
    };
    private static final TypeToken<List<OneDevComment>> LIST_OF_COMMENTS_TYPE = new TypeToken<>() {
    };
    private static final TypeToken<List<OneDevProject>> LIST_OF_PROJECTS_TYPE = new TypeToken<>() {
    };

    private final Map<Integer, OneDevProject> cachedProjects = new ConcurrentHashMap<>();
    private final List<OneDevIssueSettings.StateSpec> cachedStates = new ArrayList<>();

    private boolean myUseAccessToken;
    private String searchQuery;
    private boolean myUseMutualTls;
    private String mutualTlsCertificatePath;
    private String mutualTlsCertificatePassword;

    private HttpClient httpClient;

    public OneDevRepository() {
        super(new OneDevRepositoryType());
        init();
    }

    public OneDevRepository(OneDevRepository other) {
        super(other);
        setRepositoryType(other.getRepositoryType());

        setUseAccessToken(other.isUseAccessToken());
        setSearchQuery(other.getSearchQuery());
        setUseMutualTls(other.isUseMutualTls());
        setMutualTlsCertificatePassword(other.getMutualTlsCertificatePassword());
        setMutualTlsCertificatePath(other.getMutualTlsCertificatePath());

        init();
    }

    private void init() {
        setPreferredOpenTaskState(DEFAULT_STATE_OPEN);
        // TODO: dynamic
        setPreferredCloseTaskState(DEFAULT_STATE_CLOSED);
    }

    public boolean isUseAccessToken() {
        return myUseAccessToken;
    }

    public void setUseAccessToken(boolean useAccessToken) {
        this.myUseAccessToken = useAccessToken;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }

    public boolean isUseMutualTls() {
        return myUseMutualTls;
    }

    public void setUseMutualTls(boolean useMutualTls) {
        this.myUseMutualTls = useMutualTls;
        this.httpClient = null;
    }

    public String getMutualTlsCertificatePath() {
        return mutualTlsCertificatePath;
    }

    public void setMutualTlsCertificatePath(String mutualTlsCertificatePath) {
        this.mutualTlsCertificatePath = mutualTlsCertificatePath;
        this.httpClient = null;
    }

    public String getMutualTlsCertificatePassword() {
        return mutualTlsCertificatePassword;
    }

    public void setMutualTlsCertificatePassword(String mutualTlsCertificatePassword) {
        this.mutualTlsCertificatePassword = mutualTlsCertificatePassword;
        this.httpClient = null;
    }

    @Override
    public boolean isConfigured() {
        // URL is always required
        if (!super.isConfigured()) {
            return false;
        }
        // Password (token) too
        if (StringUtil.isEmpty(getPassword())) {
            return false;
        }
        // Username only if access token is not used
        if (!isUseAccessToken() && StringUtil.isEmpty(getUsername())) {
            return false;
        }
        // P12 password and path are required for mTLS
        if (isUseMutualTls()) {
            return !StringUtil.isEmpty(getMutualTlsCertificatePassword()) && !StringUtil.isEmpty(getMutualTlsCertificatePath());
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof OneDevRepository other)) {
            return false;
        }
        return Objects.equals(getUrl(), other.getUrl()) &&
                Objects.equals(getUsername(), other.getUsername()) &&
                Objects.equals(getPassword(), other.getPassword()) &&
                Objects.equals(isUseAccessToken(), other.isUseAccessToken()) &&
                Objects.equals(getSearchQuery(), other.getSearchQuery()) &&
                Objects.equals(isUseMutualTls(), other.isUseMutualTls()) &&
                Objects.equals(getMutualTlsCertificatePassword(), other.getMutualTlsCertificatePassword()) &&
                Objects.equals(getMutualTlsCertificatePath(), other.getMutualTlsCertificatePath());
    }

    @NotNull
    @Override
    public OneDevRepository clone() {
        return new OneDevRepository(this);
    }

    @Override
    protected @NotNull HttpClient getHttpClient() {
        if (httpClient == null) {
            if (!myUseMutualTls) {
                httpClient = super.getHttpClient();
            } else {
                HttpClientBuilder builder = HttpClients.custom()
                        .setDefaultRequestConfig(createRequestConfig())
                        .setSSLSocketFactory(createMutualTlsSocketFactory());
                httpClient = builder.build();
            }
        }
        return httpClient;
    }

    private SSLConnectionSocketFactory createMutualTlsSocketFactory() {
        try {
            KeyStore trustStore = KeyStore.getInstance("pkcs12");
            var password = getMutualTlsCertificatePassword().toCharArray();
            try (InputStream fis = new FileInputStream(getMutualTlsCertificatePath())) {
                trustStore.load(fis, password);
            }
            var alias = trustStore.aliases().nextElement();
            var sslContext = SSLContexts.custom()
                    // mTLS
                    .loadKeyMaterial(trustStore, password, (map, socket) -> alias)
                    // Trust any server certificate
                    .loadTrustMaterial(new TrustAllStrategy())
                    .build();

            // Do not verify hostname
            return new SSLConnectionSocketFactory(sslContext, new NoopHostnameVerifier());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
    public Task[] getIssues(@Nullable String query, int offset, int limit, boolean withClosed, @NotNull ProgressIndicator cancelled) throws IOException {
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
        var states = loadTaskStates();

        if (getPreferredOpenTaskState() == null) {
            setPreferredOpenTaskState(states.get(0));
        }
        if (getPreferredCloseTaskState() == null) {
            for (var i = states.size() - 1; i > 0; i--) {
                if (isStateClosed(states.get(i).getId())) {
                    setPreferredCloseTaskState(states.get(i));
                    break;
                }
            }
        }
        return new HashSet<>(states);
    }

    @Override
    public void setTaskState(@NotNull Task task, @NotNull CustomTaskState state) throws IOException {
        var endpointUrl = getRestApiUrl("issues", task.getNumber(), "state-transitions");
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

    private Task[] findIssues(String query, int offset, int limit, boolean withClosed) throws IOException {
        offset = Math.max(offset, 0);
        if (limit <= 0) {
            limit = MAX_COUNT;
        }
        limit = Math.min(limit, MAX_COUNT);

        // Which query to use?
        if (!StringUtil.isEmpty(getSearchQuery())) {
            query = getSearchQuery();
        } else {
            if (StringUtil.isEmpty(query)) {
                var closedState = getPreferredCloseTaskState().getId();
                query = withClosed ? "" : "\"State\" is not \"" + closedState + "\"";
            } else {
                query = " ~ \"" + query.replace("\"", "")  + "\" ~";
            }
        }

        URI endpointUrl;
        try {
            endpointUrl = (new URIBuilder(getRestApiUrl("issues")))
                    .addParameter("query", query)
                    .addParameter("offset", String.valueOf(offset))
                    .addParameter("count", String.valueOf(limit))
                    // For task types
                    .addParameter("withFields", String.valueOf(true))
                    .build();
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        var req = new HttpGet(endpointUrl);
        addAuthHeader(req);

        List<OneDevTask> tasks = getHttpClient().execute(req, new TaskResponseUtil.GsonMultipleObjectsDeserializer<>(gson, LIST_OF_TASKS_TYPE));
        if (!withClosed) {
            tasks = tasks.stream().filter(t -> !isStateClosed(t.state)).collect(Collectors.toList());
        }
        return ContainerUtil.map2Array(tasks, OneDevTaskImpl.class, (task) -> new OneDevTaskImpl(this, task, getProject(task.projectId)));
    }

    public boolean isStateClosed(String stateName) {
        var preferred = getPreferredCloseTaskState();
        if (preferred != null && preferred.getId().equals(stateName)) {
            return true;
        }

        // TODO: more complex rules?

        return false;
    }

    private List<CustomTaskState> loadTaskStates() {
        synchronized (cachedStates) {
            if (cachedStates.isEmpty()) {
                try {
                    var settings = getIssueSettings();
                    cachedStates.addAll(settings.stateSpecs);
                } catch (IOException e) {
                    LOG.warn("Could not load task states", e);
                }
            }

            List<CustomTaskState> ret = new ArrayList<>();
            cachedStates.forEach(state -> ret.add(new CustomTaskState(state.name, state.name)));
            if (ret.isEmpty()) {
                ret.add(DEFAULT_STATE_OPEN);
                ret.add(DEFAULT_STATE_CLOSED);
            }
            return ret;
        }
    }

    private OneDevProject getProject(int projectId) {
        var project = cachedProjects.get(projectId);
        if (project == null) {
            for (int i = 0; i < MAX_PROJECTS_TO_LOAD / MAX_COUNT; i++) {
                try {
                    var projects = loadProjects(i * MAX_COUNT);
                    projects.forEach(proj -> cachedProjects.put(proj.id, proj));
                    if (projects.size() < MAX_COUNT) {
                        break;
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        project = cachedProjects.get(projectId);
        if (project == null) {
            // Create placeholder project
            project = new OneDevProject();
            project.id = projectId;
            project.name = String.valueOf(projectId);
        }
        return project;
    }

    public List<OneDevProject> loadProjects() throws IOException {
        return loadProjects(0);
    }

    private List<OneDevProject> loadProjects(int offset) throws IOException {
        URI endpointUrl;
        try {
            endpointUrl = (new URIBuilder(getRestApiUrl("projects")))
                    .addParameter("offset", String.valueOf(offset))
                    .addParameter("count", String.valueOf(MAX_COUNT))
                    .build();
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        var req = new HttpGet(endpointUrl);
        addAuthHeader(req);

        return getHttpClient().execute(req, new TaskResponseUtil.GsonMultipleObjectsDeserializer<>(gson, LIST_OF_PROJECTS_TYPE));
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
        var endpointUrl = getRestApiUrl("issues", task.getNumber(), "comments");
        var req = new HttpGet(endpointUrl);
        addAuthHeader(req);

        List<OneDevComment> comments = getHttpClient().execute(req, new TaskResponseUtil.GsonMultipleObjectsDeserializer<>(gson, LIST_OF_COMMENTS_TYPE));
        return ContainerUtil.map2Array(comments, Comment.class, (comment) -> new SimpleComment(comment.date, getUserName(comment.userId), comment.content));
    }

    private OneDevIssueSettings getIssueSettings() throws IOException {
        var endpointUrl = getRestApiUrl("settings", "issue");
        var req = new HttpGet(endpointUrl);
        addAuthHeader(req);

        return getHttpClient().execute(req, new TaskResponseUtil.GsonSingleObjectDeserializer<>(gson, OneDevIssueSettings.class));
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
        // 'user' is a placeholder when access token is used
        String basicAuthUsername = isUseAccessToken() ? "user" : getUsername();
        var usernamePassword = (basicAuthUsername + ":" + getPassword()).getBytes(StandardCharsets.UTF_8);
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
