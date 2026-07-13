package com.konsec.intellij.settings;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;

/**
 * A named, application-level snapshot of OneDev server connection settings so that the same
 * server does not have to be re-entered for every project.
 * <p>
 * Only non-secret fields live here. The API token / password and the mTLS certificate password
 * are kept in the IDE {@code PasswordSafe} (see {@link OneDevServerProfiles}), never in this
 * serialized state.
 */
@Tag("profile")
public class OneDevServerProfile {

    @Attribute
    public String name;
    @Attribute
    public String url;
    @Attribute
    public String username;
    @Attribute
    public boolean useAccessToken;
    @Attribute
    public boolean useMutualTls;
    @Attribute
    public String mutualTlsCertificatePath;
    @Attribute
    public String searchQuery;

    // Required for XML deserialization
    public OneDevServerProfile() {
    }
}
