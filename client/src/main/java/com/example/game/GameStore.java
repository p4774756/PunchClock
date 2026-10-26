package com.example.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** 虛擬點數存在 ~/.punchclock/games.json，跟排程與連線設定分開。 */
public final class GameStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path path;

    public GameStore() {
        this(defaultPath());
    }

    public GameStore(Path path) {
        this.path = path;
    }

    public GameWallet load() {
        if (!Files.exists(path)) {
            return GameWallet.fresh();
        }
        try {
            String json = Files.readString(path);
            Saved saved = GSON.fromJson(json, Saved.class);
            if (saved == null) {
                return GameWallet.fresh();
            }
            return GameWallet.restored(saved.balance, saved.records, saved.openGame, saved.openBet);
        } catch (RuntimeException | IOException ex) {
            return GameWallet.fresh();
        }
    }

    public void save(GameWallet wallet) {
        Saved saved = new Saved();
        saved.balance = wallet.balance();
        saved.openGame = wallet.openGame();
        saved.openBet = wallet.openBet();
        saved.records = new ArrayList<>(wallet.records());
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, GSON.toJson(saved));
        } catch (IOException ex) {
            throw new GameRuleException("點數暫時沒能寫入這台電腦");
        }
    }

    private static Path defaultPath() {
        String home = System.getProperty("user.home", ".");
        return Paths.get(home, ".punchclock", "games.json");
    }

    private static final class Saved {
        int balance = GameWallet.STARTING_BALANCE;
        String openGame;
        int openBet;
        List<GameRecord> records = new ArrayList<>();
    }
}
