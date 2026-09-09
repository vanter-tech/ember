package com.vanter.emberagent;

/** The two things the agent needs to connect, persisted (encrypted on Windows) between runs. */
public record AgentCredential(String apiKey, String backendBaseUrl) {}
