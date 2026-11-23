package com.example.server.web;

import com.example.server.store.DevMachineStore;
import com.example.server.store.DevStatusMapper;
import com.example.server.store.DevStatusModels.Document;
import com.example.server.store.DevStatusModels.Item;
import com.example.server.store.DevStatusModels.QueueEntry;
import com.example.server.store.DevStatusModels.Snapshot;
import com.example.server.store.DevStatusModels.TimelineEvent;
import com.example.server.store.DevStatusService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 公開開發進度。只回消毒後 JSON，不含正文／留言／作者／token。 */
public final class DevStatusRoutes {

    private final DevStatusService service;
    private final DevMachineStore machineStore;

    public DevStatusRoutes(DevStatusService service) {
        this(service, null);
    }

    public DevStatusRoutes(DevStatusService service, DevMachineStore machineStore) {
        this.service = service;
        this.machineStore = machineStore;
    }

    public void register(Javalin app) {
        app.get("/api/dev-status", this::show);
    }

    private void show(Context ctx) {
        long now = System.currentTimeMillis();
        DevStatusService.View view = service.get(now);
        Document document = documentForResponse(view, now);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("stale", view.stale);
        body.put("fetchedAt", view.fetchedAt);
        body.put("source", view.source);
        body.put("now", nowMap(document));
        body.put("timeline", timelineList(document.timeline));
        body.put("stats", statsMap(view));
        body.put("message", view.message);
        ctx.json(body);
    }

    private Document documentForResponse(DevStatusService.View view, long now) {
        if (machineStore == null) {
            return view.document;
        }
        Snapshot github = service.githubSnapshot(now);
        return DevStatusMapper.withFreshMachineRuns(
                view.document, github, service.repo(), now, machineStore.machineSnapshots());
    }

    private static Map<String, Object> nowMap(Document document) {
        Map<String, Object> now = new LinkedHashMap<>();
        List<Map<String, Object>> current = new ArrayList<>();
        for (Item item : document.now.current) {
            Map<String, Object> row = itemMap(item);
            if (row != null) {
                current.add(row);
            }
        }
        now.put("current", current);
        now.put("next", itemMap(document.now.next));
        List<Map<String, Object>> queue = new ArrayList<>();
        for (QueueEntry entry : document.now.queue) {
            Map<String, Object> row = itemMap(entry.item);
            if (row != null) {
                row.put("order", entry.order);
                queue.add(row);
            }
        }
        now.put("queue", queue);
        return now;
    }

    private static List<Map<String, Object>> timelineList(List<TimelineEvent> events) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TimelineEvent event : events) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("at", event.at);
            row.put("action", event.action);
            row.put("ref", event.ref);
            row.put("title", event.title);
            row.put("url", event.url);
            out.add(row);
        }
        return out;
    }

    private static Map<String, Object> statsMap(DevStatusService.View view) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("weekLabel", view.document.stats.weekLabel);
        stats.put("mergedThisWeek", view.document.stats.mergedThisWeek);
        stats.put("openIssues", view.document.stats.openIssues);
        return stats;
    }

    private static Map<String, Object> itemMap(Item item) {
        if (item == null) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("pair", item.pair);
        row.put("issue", item.issue);
        row.put("pr", item.pr);
        row.put("title", item.title);
        row.put("status", item.status);
        row.put("updatedAt", item.updatedAt);
        row.put("issueUrl", item.issueUrl);
        row.put("prUrl", item.prUrl);
        return row;
    }
}
