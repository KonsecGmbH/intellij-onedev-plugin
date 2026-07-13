package com.konsec.intellij.settings;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightPlatform4TestCase;
import com.intellij.util.xmlb.XmlSerializer;
import com.konsec.intellij.OneDevRepository;
import org.jdom.Element;
import org.junit.Assert;
import org.junit.Test;

public class OneDevServerProfilesTest extends LightPlatform4TestCase {

    private static final String TOKEN = "SUPER_SECRET_TOKEN";
    private static final String PASSWORD = "SUPER_SECRET_PASSWORD";
    private static final String MTLS_PW = "MTLS_SECRET_PW";

    private OneDevServerProfiles profiles;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        profiles = OneDevServerProfiles.getInstance();
        // App-level service persists across tests; start from a clean slate.
        for (OneDevServerProfile p : profiles.getProfiles()) {
            profiles.deleteProfile(p.name);
        }
    }

    private static OneDevRepository buildRepository(boolean useAccessToken, boolean useMutualTls) {
        OneDevRepository repo = new OneDevRepository();
        repo.setUrl("https://onedev.example.com");
        repo.setSearchQuery("\"State\" is \"Open\"");
        repo.setUseAccessToken(useAccessToken);
        if (useAccessToken) {
            repo.setPassword(TOKEN);
        } else {
            repo.setUsername("alice");
            repo.setPassword(PASSWORD);
        }
        if (useMutualTls) {
            repo.setUseMutualTls(true);
            repo.setMutualTlsCertificatePath("/path/to/client.pfx");
            repo.setMutualTlsCertificatePassword(MTLS_PW);
        }
        return repo;
    }

    @Test
    public void testSaveApplyRoundTrip() {
        for (boolean useAccessToken : new boolean[]{true, false}) {
            for (boolean useMutualTls : new boolean[]{true, false}) {
                OneDevRepository original = buildRepository(useAccessToken, useMutualTls);
                profiles.saveProfile("srv", original);

                OneDevRepository loaded = new OneDevRepository();
                profiles.applyTo("srv", loaded);

                Assert.assertEquals(original.getUrl(), loaded.getUrl());
                Assert.assertEquals(StringUtil.notNullize(original.getUsername()), StringUtil.notNullize(loaded.getUsername()));
                Assert.assertEquals(original.isUseAccessToken(), loaded.isUseAccessToken());
                Assert.assertEquals(original.getSearchQuery(), loaded.getSearchQuery());
                Assert.assertEquals(original.isUseMutualTls(), loaded.isUseMutualTls());
                Assert.assertEquals(StringUtil.notNullize(original.getMutualTlsCertificatePath()),
                        StringUtil.notNullize(loaded.getMutualTlsCertificatePath()));
                // Secrets round-trip through PasswordSafe
                Assert.assertEquals(original.getPassword(), loaded.getPassword());
                Assert.assertEquals(StringUtil.notNullize(original.getMutualTlsCertificatePassword()),
                        StringUtil.notNullize(loaded.getMutualTlsCertificatePassword()));

                profiles.deleteProfile("srv");
            }
        }
    }

    @Test
    public void testStateSerializationHasNoSecrets() {
        profiles.saveProfile("srv", buildRepository(true, true));

        Element element = XmlSerializer.serialize(profiles.getState());
        String xml = JDOMUtil.writeElement(element);

        // Non-secret fields survive
        OneDevServerProfiles.State deserialized = XmlSerializer.deserialize(element, OneDevServerProfiles.State.class);
        Assert.assertEquals(1, deserialized.profiles.size());
        OneDevServerProfile profile = deserialized.profiles.get(0);
        Assert.assertEquals("srv", profile.name);
        Assert.assertEquals("https://onedev.example.com", profile.url);
        Assert.assertTrue(profile.useMutualTls);
        Assert.assertEquals("/path/to/client.pfx", profile.mutualTlsCertificatePath);

        // Secrets must never be written to the app-level XML state
        Assert.assertFalse("token leaked into XML", xml.contains(TOKEN));
        Assert.assertFalse("mTLS password leaked into XML", xml.contains(MTLS_PW));
    }

    @Test
    public void testDeleteRemovesProfileAndSecret() {
        profiles.saveProfile("srv", buildRepository(true, false));
        Assert.assertNotNull(profiles.findProfile("srv"));

        profiles.deleteProfile("srv");

        Assert.assertNull(profiles.findProfile("srv"));
        // Secret is cleared: applying the (now missing) profile leaves the token empty
        OneDevRepository loaded = new OneDevRepository();
        profiles.applyTo("srv", loaded);
        Assert.assertTrue(StringUtil.isEmpty(loaded.getPassword()));
    }
}
