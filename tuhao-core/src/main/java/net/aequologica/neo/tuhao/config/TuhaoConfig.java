package net.aequologica.neo.tuhao.config;

import java.io.IOException;

import net.aequologica.neo.geppaequo.config.AbstractConfig;
import net.aequologica.neo.geppaequo.config.Config;

import org.weakref.jmx.Managed;

@Config(name = "tuhao")
public final class TuhaoConfig extends AbstractConfig {

    public TuhaoConfig() throws IOException {
        super();
    }

    @Managed
    public String getEncryptedGithubKey() {
        return get("encryptedGithubKey");
    }

    @Managed
    public void setEncryptedGithubKey(String encryptedGithubKey) {
        set("encryptedGithubKey", encryptedGithubKey);
    }
    
    @Managed
    public String getGithubId() {
        return get("githubId");
    }

    @Managed
    public void setGithubId(String githubId) {
        set("githubId", githubId);
    }
}
