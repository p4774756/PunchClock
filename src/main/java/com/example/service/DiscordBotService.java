package com.example.service;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 專責處理 Discord Bot (JDA) 控制與指令互動
 */
public class DiscordBotService extends ListenerAdapter {

    @FunctionalInterface
    public interface ScheduleHandler {
        boolean schedule(String dateStr, String timeStr, String url, String buttonId);
    }

    private JDA jda;
    private Runnable checkInTask;
    private Supplier<String> statusSupplier;
    private Runnable cancelTask;
    private ScheduleHandler scheduleHandler;
    private Consumer<String> logger;

    /**
     * 啟動 Discord Bot
     *
     * @param token           Discord Bot Token
     * @param logger          日誌 Callback
     * @param checkInTask     觸發打卡任務 Callback
     * @param statusSupplier  取得當前狀態 Callback
     * @param cancelTask      取消排程任務 Callback
     * @param scheduleHandler 遠端設定排程 Callback
     */
    public synchronized boolean startBot(String token,
                                         Consumer<String> logger,
                                         Runnable checkInTask,
                                         Supplier<String> statusSupplier,
                                         Runnable cancelTask,
                                         ScheduleHandler scheduleHandler) {
        if (jda != null) {
            stopBot();
        }

        this.logger = logger;
        this.checkInTask = checkInTask;
        this.statusSupplier = statusSupplier;
        this.cancelTask = cancelTask;
        this.scheduleHandler = scheduleHandler;

        log("🤖 正在連線啟動 Discord Bot...");
        try {
            jda = JDABuilder.createDefault(token)
                    .addEventListeners(this)
                    .build()
                    .awaitReady();

            List<net.dv8tion.jda.api.interactions.commands.build.CommandData> commandList = java.util.List.of(
                    Commands.slash("checkin", "🚀 立即觸發自動打卡任務"),
                    Commands.slash("schedule", "📆 遠端設定定時打卡排程")
                            .addOption(OptionType.STRING, "time", "打卡時間 (格式 HH:mm，例如 09:30 或 18:00)", true)
                            .addOption(OptionType.STRING, "date", "打卡日期 (格式 YYYY-MM-DD，選填，預設為今日或明日)", false)
                            .addOption(OptionType.STRING, "url", "目標打卡網址 (選填)", false)
                            .addOption(OptionType.STRING, "button_id", "打卡按鈕 ID/Selector (選填)", false),
                    Commands.slash("status", "📊 查詢目前排程與系統狀態"),
                    Commands.slash("cancel", "🛑 取消目前的定時排程"),
                    Commands.slash("help", "📖 顯示 Bot 所有可用的指令說明與指南")
            );

            // 1. 全域註冊 Global Commands
            jda.updateCommands().addCommands(commandList).queue();

            // 2. 伺服器同步 Guild Commands (讓指令在 Discord 聊天視窗中「秒速」更新顯示，無需等待全域 1 小時快取)
            for (net.dv8tion.jda.api.entities.Guild guild : jda.getGuilds()) {
                guild.updateCommands().addCommands(commandList).queue();
            }

            log("✅ Discord Bot 啟動成功！已同步最新指令至伺服器。");
            return true;
        } catch (Exception ex) {
            log("❌ Discord Bot 啟動失敗：" + ex.getMessage());
            jda = null;
            return false;
        }
    }

    /**
     * 停止 Discord Bot
     */
    public synchronized void stopBot() {
        if (jda != null) {
            try {
                jda.shutdownNow();
                log("🛑 Discord Bot 已關閉。");
            } catch (Exception e) {
                log("⚠️ 關閉 Discord Bot 時發生例外：" + e.getMessage());
            } finally {
                jda = null;
            }
        }
    }

    public boolean isBotRunning() {
        return jda != null;
    }

    /**
     * 發送訊息到 Discord 頻道 (選用)
     */
    public void sendMessageToChannel(String channelId, String message) {
        if (jda != null && channelId != null && !channelId.isBlank()) {
            try {
                var channel = jda.getTextChannelById(channelId);
                if (channel != null) {
                    channel.sendMessage(message).queue();
                } else {
                    log("⚠️ 找不到指定 ID 的 Discord 頻道：" + channelId);
                }
            } catch (Exception e) {
                log("❌ 發送 Discord 頻道訊息失敗：" + e.getMessage());
            }
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "checkin":
                event.reply("🚀 已收到 Discord 指令！正在為您觸發自動打卡任務...").queue();
                log("🤖 [Discord Bot] 收到 /checkin 指令，準備執行打卡。");
                if (checkInTask != null) {
                    new Thread(checkInTask).start();
                }
                break;

            case "schedule":
                var timeOpt = event.getOption("time");
                var dateOpt = event.getOption("date");
                var urlOpt = event.getOption("url");
                var buttonIdOpt = event.getOption("button_id");

                String timeStr = (timeOpt != null) ? timeOpt.getAsString().trim() : "";
                String dateStr = (dateOpt != null) ? dateOpt.getAsString().trim() : null;
                String url = (urlOpt != null) ? urlOpt.getAsString().trim() : null;
                String buttonId = (buttonIdOpt != null) ? buttonIdOpt.getAsString().trim() : null;

                if (scheduleHandler != null) {
                    boolean success = scheduleHandler.schedule(dateStr, timeStr, url, buttonId);
                    if (success) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("✅ **遠端排程設定成功！**\n");
                        if (dateStr != null) sb.append("• 日期：").append(dateStr).append("\n");
                        sb.append("• 時間：").append(timeStr).append("\n");
                        if (url != null) sb.append("• 網址：").append(url).append("\n");
                        if (buttonId != null) sb.append("• 按鈕 ID：").append(buttonId).append("\n");
                        event.reply(sb.toString()).queue();
                        log("🤖 [Discord Bot] 遠端排程設定成功！時間：" + timeStr);
                    } else {
                        event.reply("❌ **排程設定失敗**：請確認時間格式 (HH:mm) 或設定的時間是否已過去。").queue();
                        log("🤖 [Discord Bot] 遠端排程設定失敗。");
                    }
                } else {
                    event.reply("❌ 系統尚未設定排程處理器。").queue();
                }
                break;

            case "status":
                String statusMsg = (statusSupplier != null) ? statusSupplier.get() : "系統運作中。";
                event.reply("📊 **系統狀態**：\n" + statusMsg).queue();
                log("🤖 [Discord Bot] 收到 /status 指令，已回應當前狀態。");
                break;

            case "cancel":
                if (cancelTask != null) {
                    cancelTask.run();
                }
                event.reply("🛑 已成功發送指令取消定時排程！").queue();
                log("🤖 [Discord Bot] 收到 /cancel 指令，已發送取消排程請求。");
                break;

            case "help":
                String helpText = "📖 **clickClick Discord Bot 指令指南**\n\n"
                        + "• `/checkin` — 🚀 立即觸發自動打卡任務\n"
                        + "• `/schedule time:HH:mm [date:YYYY-MM-DD] [url:...] [button_id:...]` — 📆 遠端設定定時打卡排程\n"
                        + "• `/status`  — 📊 查詢目前排程與系統狀態\n"
                        + "• `/cancel`  — 🛑 取消目前的定時排程\n"
                        + "• `/help`    — 📖 顯示此指令說明清單";
                event.reply(helpText).queue();
                log("🤖 [Discord Bot] 收到 /help 指令，已回應指南清單。");
                break;

            default:
                event.reply("未知的指令。").setEphemeral(true).queue();
                break;
        }
    }

    private void log(String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }
}

