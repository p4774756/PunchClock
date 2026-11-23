package com.example.server.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** device-bindings.json */
public final class DeviceBindingStore {

    public static final String BOUND_BY_APP = "app-login";
    public static final String BOUND_BY_ADMIN = "admin-assign";

    public static final class Binding {
        public String clientId;
        public String username;
        public String displayName;
        public String boundAt;
        public String boundBy;
    }

    private static final class Document {
        List<Binding> bindings = new ArrayList<>();
    }

    private final JsonDocumentFile file;
    private final Gson gson;
    private Document document = new Document();
    private final Object lock = new Object();

    public DeviceBindingStore(Path directory) {
        this.gson = new GsonBuilder().disableHtmlEscaping().create();
        Path dir = directory == null ? AccountDataPaths.defaultDirectory() : directory;
        this.file = new JsonDocumentFile(
                dir.resolve("device-bindings.json"),
                json -> gson.fromJson(json, Document.class),
                doc -> gson.toJson(doc));
        synchronized (lock) {
            Document loaded = file.readOr(new Document());
            if (loaded.bindings == null) {
                loaded.bindings = new ArrayList<>();
            }
            document = loaded;
        }
    }

    public synchronized Optional<Binding> find(String clientId) {
        if (clientId == null || clientId.isEmpty()) {
            return Optional.empty();
        }
        for (Binding b : document.bindings) {
            if (clientId.equals(b.clientId)) {
                return Optional.of(b);
            }
        }
        return Optional.empty();
    }

    public String usernameFor(String clientId) {
        return find(clientId).map(b -> b.username).orElse(null);
    }

    public boolean isBound(String clientId) {
        String user = usernameFor(clientId);
        return user != null && !user.isEmpty();
    }

    public void bindAppLogin(String clientId, String username) {
        bind(clientId, username, BOUND_BY_APP);
    }

    public void adminAssign(String clientId, String username) {
        bind(clientId, username, BOUND_BY_ADMIN);
    }

    public void unbind(String clientId) {
        synchronized (lock) {
            document.bindings.removeIf(b -> clientId.equals(b.clientId));
            persistLocked();
        }
    }

    public void unbindAllForUser(String username) {
        if (username == null || username.isEmpty()) {
            return;
        }
        synchronized (lock) {
            for (Binding b : document.bindings) {
                if (username.equals(b.username)) {
                    b.username = "";
                }
            }
            persistLocked();
        }
    }

    public void setDisplayName(String clientId, String displayName) {
        synchronized (lock) {
            Binding b = findOrCreate(clientId);
            b.displayName = displayName == null ? "" : displayName.trim();
            persistLocked();
        }
    }

    public Map<String, Object> accountFieldsForApi(String clientId) {
        Map<String, Object> out = new LinkedHashMap<>();
        Optional<Binding> binding = find(clientId);
        if (binding.isEmpty() || binding.get().username == null || binding.get().username.isEmpty()) {
            out.put("accountUsername", "");
            out.put("unbound", true);
        } else {
            Binding b = binding.get();
            out.put("accountUsername", b.username);
            out.put("unbound", false);
            if (b.displayName != null && !b.displayName.isEmpty()) {
                out.put("displayName", b.displayName);
            }
        }
        return out;
    }

    public List<Binding> listBindingsCopy() {
        synchronized (lock) {
            return new ArrayList<>(document.bindings);
        }
    }

    public boolean hasBindingsForUser(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        for (Binding b : document.bindings) {
            if (username.equals(b.username)) {
                return true;
            }
        }
        return false;
    }

    private void bind(String clientId, String username, String boundBy) {
        synchronized (lock) {
            Binding b = findOrCreate(clientId);
            b.username = username;
            b.boundAt = Instant.now().toString();
            b.boundBy = boundBy;
            persistLocked();
        }
    }

    private Binding findOrCreate(String clientId) {
        Optional<Binding> existing = find(clientId);
        if (existing.isPresent()) {
            return existing.get();
        }
        Binding b = new Binding();
        b.clientId = clientId;
        document.bindings.add(b);
        return b;
    }

    private void persistLocked() {
        try {
            file.write(document);
        } catch (Exception ex) {
            throw new IllegalStateException("無法寫入 device-bindings.json", ex);
        }
    }
}