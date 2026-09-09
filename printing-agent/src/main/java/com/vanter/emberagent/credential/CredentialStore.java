package com.vanter.emberagent.credential;

import com.vanter.emberagent.AgentCredential;
import java.util.Optional;

/** Where the agent keeps its API key + backend URL between runs. */
public interface CredentialStore {

    Optional<AgentCredential> load();

    void save(AgentCredential credential);

    void clear();

    /** true if the bytes on disk are OS-encrypted (DPAPI), false for the plaintext fallback. */
    boolean isEncrypted();
}
