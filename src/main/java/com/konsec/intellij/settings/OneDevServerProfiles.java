package com.konsec.intellij.settings;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.XCollection;
import com.konsec.intellij.OneDevRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Application-level store of named OneDev server connection settings, so the same server does
 * not have to be re-entered for every project.
 * <p>
 * Mirrors the tasks plugin's own {@code RecentTaskRepositories}: an app-level
 * {@link Service}/{@link State} component reachable from any project. Non-secret fields are
 * persisted in the (machine-local) app config; the API token and mTLS certificate password are
 * kept in the IDE {@link PasswordSafe} keyed by profile name and never written to the XML state.
 */
@State(name = "OneDevServerProfiles", storages = @Storage(StoragePathMacros.NON_ROAMABLE_FILE))
@Service(Service.Level.APP)
public final class OneDevServerProfiles implements PersistentStateComponent<OneDevServerProfiles.State> {

    /** PasswordSafe account discriminators under a single per-profile service name. */
    private static final String ACCOUNT_TOKEN = "token";
    private static final String ACCOUNT_MTLS = "mtls";

    public static final class State {
        @XCollection(style = XCollection.Style.v2)
        public List<OneDevServerProfile> profiles = new ArrayList<>();
    }

    private State myState = new State();

    public static OneDevServerProfiles getInstance() {
        return ApplicationManager.getApplication().getService(OneDevServerProfiles.class);
    }

    @Override
    public @NotNull State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        myState = state;
    }

    /** @return an immutable snapshot of the stored profiles. */
    public List<OneDevServerProfile> getProfiles() {
        return new ArrayList<>(myState.profiles);
    }

    public @Nullable OneDevServerProfile findProfile(String name) {
        for (OneDevServerProfile profile : myState.profiles) {
            if (profile.name != null && profile.name.equals(name)) {
                return profile;
            }
        }
        return null;
    }

    /**
     * Capture the connection settings of {@code repo} under {@code name}, replacing any existing
     * profile with the same name. Secrets (token, mTLS password) go to PasswordSafe.
     */
    public void saveProfile(@NotNull String name, @NotNull OneDevRepository repo) {
        OneDevServerProfile profile = findProfile(name);
        if (profile == null) {
            profile = new OneDevServerProfile();
            myState.profiles.add(profile);
        }
        profile.name = name;
        profile.url = repo.getUrl();
        profile.username = repo.getUsername();
        profile.useAccessToken = repo.isUseAccessToken();
        profile.useMutualTls = repo.isUseMutualTls();
        profile.mutualTlsCertificatePath = repo.getMutualTlsCertificatePath();
        profile.searchQuery = repo.getSearchQuery();

        setSecret(name, ACCOUNT_TOKEN, repo.getPassword());
        setSecret(name, ACCOUNT_MTLS, repo.getMutualTlsCertificatePassword());
    }

    /** Populate {@code repo} from the named profile, including secrets read from PasswordSafe. */
    public void applyTo(@NotNull String name, @NotNull OneDevRepository repo) {
        OneDevServerProfile profile = findProfile(name);
        if (profile == null) {
            return;
        }
        repo.setUrl(StringUtil.notNullize(profile.url));
        repo.setUsername(StringUtil.notNullize(profile.username));
        repo.setUseAccessToken(profile.useAccessToken);
        repo.setUseMutualTls(profile.useMutualTls);
        repo.setMutualTlsCertificatePath(StringUtil.notNullize(profile.mutualTlsCertificatePath));
        repo.setSearchQuery(StringUtil.notNullize(profile.searchQuery));

        repo.setPassword(StringUtil.notNullize(getSecret(name, ACCOUNT_TOKEN)));
        repo.setMutualTlsCertificatePassword(StringUtil.notNullize(getSecret(name, ACCOUNT_MTLS)));
    }

    public void deleteProfile(@NotNull String name) {
        myState.profiles.removeIf(profile -> name.equals(profile.name));
        setSecret(name, ACCOUNT_TOKEN, null);
        setSecret(name, ACCOUNT_MTLS, null);
    }

    private static CredentialAttributes attributes(String name, String account) {
        return new CredentialAttributes(CredentialAttributesKt.generateServiceName("OneDev Profile", name), account);
    }

    private static void setSecret(String name, String account, @Nullable String secret) {
        CredentialAttributes attributes = attributes(name, account);
        if (StringUtil.isEmpty(secret)) {
            PasswordSafe.getInstance().set(attributes, null);
        } else {
            PasswordSafe.getInstance().set(attributes, new Credentials(account, secret));
        }
    }

    private static @Nullable String getSecret(String name, String account) {
        Credentials credentials = PasswordSafe.getInstance().get(attributes(name, account));
        return credentials == null ? null : credentials.getPasswordAsString();
    }
}
