package com.seosean.zombiesexplorer;

import com.seosean.zombiesexplorer.config.ZombiesExplorerGuiConfig;
import com.seosean.zombiesexplorer.utils.DelayedTask;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import com.seosean.zombiesexplorer.utils.NotificationRenderer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod(modid = ZombiesExplorer.MODID, version = ZombiesExplorer.VERSION,
        acceptedMinecraftVersions = "[1.8.9]",
        guiFactory = "com.seosean.zombiesexplorer.config.ZombiesExplorerGuiFactory",
        dependencies = "required-after:showspawntime@[2.0,)"
)
public class ZombiesExplorer {
    public static final String MODID = "zombiesexplorer";
    public static final String VERSION = "1.7";
    public static ZombiesExplorer INSTANCE;
    public SpawnPatternNotice spawnPatternNotice;
    private Configuration config;
    private Logger logger;

    // Mob counter components
    private MobCounterHandler mobCounterHandler;
    private MobCounterGui mobCounterGui;

    // Track keybind states to handle multiple presses
    private final Map<KeyBinding, Boolean> keyWasPressed = new HashMap<>();

    // Track keybind changes to save them
    private boolean keybindsNeedSaving = false;

    public static final String EMOJI_REGEX = "(?:[\uD83C\uDF00-\uD83D\uDDFF]|[\uD83E\uDD00-\uD83E\uDDFF]|[\uD83D\uDE00-\uD83D\uDE4F]|[\uD83D\uDE80-\uD83D\uDEFF]|[\u2600-\u26FF]\uFE0F?|[\u2700-\u27BF]\uFE0F?|\u24C2\uFE0F?|[\uD83C\uDDE6-\uD83C\uDDFF]{1,2}|[\uD83C\uDD70\uD83C\uDD71\uD83C\uDD7E\uD83C\uDD7F\uD83C\uDD8E\uD83C\uDD91-\uD83C\uDD9A]\uFE0F?|[\u0023\u002A\u0030-\u0039]\uFE0F?\u20E3|[\u2194-\u2199\u21A9-\u21AA]\uFE0F?|[\u2B05-\u2B07\u2B1B\u2B1C\u2B50\u2B55]\uFE0F?|[\u2934\u2935]\uFE0F?|[\u3030\u303D]\uFE0F?|[\u3297\u3299]\uFE0F?|[\uD83C\uDE01\uD83C\uDE02\uD83C\uDE1A\uD83C\uDE2F\uD83C\uDE32-\uD83C\uDE3A\uD83C\uDE50\uD83C\uDE51]\uFE0F?|[\u203C\u2049]\uFE0F?|[\u25AA\u25AB\u25B6\u25C0\u25FB-\u25FE]\uFE0F?|[\u00A9\u00AE]\uFE0F?|[\u2122\u2139]\uFE0F?|\uD83C\uDC04\uFE0F?|\uD83C\uDCCF\uFE0F?|[\u231A\u231B\u2328\u23CF\u23E9-\u23F3\u23F8-\u23FA]\uFE0F?)";

    public static final String COLOR_REGEX = "§[a-zA-Z0-9]";

    // Constants for keybind config category
    private static final String KEYBIND_CATEGORY = "keybinds";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        config = new Configuration(event.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event)
    {
        this.ConfigLoad();
        INSTANCE = this;
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(NotificationRenderer.getInstance()); // Register notification renderer
        MinecraftForge.EVENT_BUS.register(spawnPatternNotice = new SpawnPatternNotice());
        MinecraftForge.EVENT_BUS.register(new PlayerVisibilityHandler()); // Register the player visibility handler
        MinecraftForge.EVENT_BUS.register(new BossHighlightHandler()); // Register the boss highlight handler
        
        // Create MobHighlightHandler and register it
        MobHighlightHandler mobHighlightHandler = new MobHighlightHandler();
        MinecraftForge.EVENT_BUS.register(mobHighlightHandler);
        
        // Initialize and register mob counter system
        mobCounterHandler = new MobCounterHandler();
        mobCounterHandler.setMobHighlightHandler(mobHighlightHandler); // Link for wave syncing
        mobCounterGui = new MobCounterGui(mobCounterHandler);
        MinecraftForge.EVENT_BUS.register(mobCounterHandler);
        MinecraftForge.EVENT_BUS.register(mobCounterGui);

        // Initialize keybinds with values from config
        initKeybinds();

        // Initialize keybind state tracking
        keyWasPressed.put(keyToggleConfig, false);
        keyWasPressed.put(keyToggleWhiteOutlines, false);
        keyWasPressed.put(keyTogglePlayerVisibility, false);
        keyWasPressed.put(keyToggleBossHighlight, false);
        keyWasPressed.put(keyToggleMobChams, false);
        keyWasPressed.put(keyToggleMobESP, false);
        keyWasPressed.put(keyToggleMobCounter, false);

        if (!ZombiesExplorer.isShowSpawnTimeInstalled()) {
            logger.error("To use ZombiesExplorer, you must install ShowSpawnTime 2.0.");
            MinecraftForge.EVENT_BUS.unregister(spawnPatternNotice);
            ENABLED = false;
        }
    }

    // Initialize keybinds
    private void initKeybinds() {
        // Load keybind values from config
        int configKey = config.getInt("keyToggleConfig", KEYBIND_CATEGORY, Keyboard.KEY_K, 0, 256, "Key to open config");
        int whiteOutlinesKey = config.getInt("keyToggleWhiteOutlines", KEYBIND_CATEGORY, Keyboard.KEY_O, 0, 256, "Key to toggle white outlines");
        int playerVisKey = config.getInt("keyTogglePlayerVisibility", KEYBIND_CATEGORY, Keyboard.KEY_P, 0, 256, "Key to toggle player visibility");
        int bossHighlightKey = config.getInt("keyToggleBossHighlight", KEYBIND_CATEGORY, Keyboard.KEY_B, 0, 256, "Key to toggle boss highlight");
        int mobChamsKey = config.getInt("keyToggleMobChams", KEYBIND_CATEGORY, Keyboard.KEY_C, 0, 256, "Key to toggle mob chams");
        int mobESPKey = config.getInt("keyToggleMobESP", KEYBIND_CATEGORY, Keyboard.KEY_E, 0, 256, "Key to toggle mob ESP");
        int mobCounterKey = config.getInt("keyToggleMobCounter", KEYBIND_CATEGORY, Keyboard.KEY_M, 0, 256, "Key to toggle mob counter");

        // Create keybindings with these values
        keyToggleConfig = new KeyBinding("Config", configKey, "Zombies Explorer");
        keyToggleWhiteOutlines = new KeyBinding("Toggle White Outlines", whiteOutlinesKey, "Zombies Explorer");
        keyTogglePlayerVisibility = new KeyBinding("Toggle Player Visibility", playerVisKey, "Zombies Explorer");
        keyToggleBossHighlight = new KeyBinding("Toggle Boss Highlight", bossHighlightKey, "Zombies Explorer");
        keyToggleMobChams = new KeyBinding("Toggle Mob Chams", mobChamsKey, "Zombies Explorer");
        keyToggleMobESP = new KeyBinding("Toggle Mob ESP", mobESPKey, "Zombies Explorer");
        keyToggleMobCounter = new KeyBinding("Toggle Mob Counter", mobCounterKey, "Zombies Explorer");

        // Register the keybindings
        ClientRegistry.registerKeyBinding(keyToggleConfig);
        ClientRegistry.registerKeyBinding(keyToggleWhiteOutlines);
        ClientRegistry.registerKeyBinding(keyTogglePlayerVisibility);
        ClientRegistry.registerKeyBinding(keyToggleBossHighlight);
        ClientRegistry.registerKeyBinding(keyToggleMobChams);
        ClientRegistry.registerKeyBinding(keyToggleMobESP);
        ClientRegistry.registerKeyBinding(keyToggleMobCounter);
    }

    public static boolean isShowSpawnTimeInstalled(){
        List<ModContainer> mods = Loader.instance().getActiveModList();
        for (ModContainer mod : mods) {
            if ("showspawntime".equals(mod.getModId())) {
                return true;
            }
        }
        return false;
    }

    public static boolean ENABLED = true;
    public static ZombiesExplorer getInstance() {
        return INSTANCE;
    }

    public static boolean PowerupDetector;
    public static boolean BadHeadShotDetector;
    public static int PowerupPredictor;
    public static boolean NameTag;
    public static boolean BadHeadShotOnLine;
    public static boolean MobSpawnOrder;
    public static boolean WhiteOutlineAllMobs;
    public static boolean HideOtherPlayers; // Option for showing other players (now true=show, false=hide)
    public static boolean BossHighlight; // Option for boss highlighting
    public static boolean MobChams; // New option for mob chams
    public static boolean MobESP; // New option for mob ESP
    public static boolean MobCounter; // New option for mob counter

    public void ConfigLoad() {
        config.load();
        logger.info("Started loading config. ");

        String commentPowerupDetector;
        String commandBadHeadShotDetector;
        String commandPowerupPredictor;
        String commentNameTag;
        String commentBadHeadShotOnLine;
        String commentMobSpawnOrder;
        String commentWhiteOutline;
        String commentHideOtherPlayers;
        String commentBossHighlight; // New comment for boss highlighting
        String commentMobChams; // New comment for mob chams
        String commentMobESP; // New comment for mob ESP
        String commentMobCounter; // New comment for mob counter

        commentPowerupDetector = "Powerup Detector";
        PowerupDetector = config.get(Configuration.CATEGORY_GENERAL, "Powerup Detector", true, commentPowerupDetector).getBoolean();

        commandBadHeadShotDetector = "Bad HeadShot Detector";
        BadHeadShotDetector = config.get(Configuration.CATEGORY_GENERAL, "Bad HeadShot Detector", true, commandBadHeadShotDetector).getBoolean();

        commandPowerupPredictor = "The amount of marks you want to apply for possible powerup zombie";
        PowerupPredictor = config.get(Configuration.CATEGORY_GENERAL, "Powerup Predictor", 1, commandPowerupPredictor, 0, 3).getInt();

        commentNameTag = "Show mark name tag for certain zombies, this is not an X-Ray feature.";
        NameTag = config.get(Configuration.CATEGORY_GENERAL, "Name Tag", true, commentNameTag).getBoolean();

        commentBadHeadShotOnLine = "Marks all mobs on the line connected to you and bad headshot mob.";
        BadHeadShotOnLine = config.get(Configuration.CATEGORY_GENERAL, "Bad HeadShot OnLine", true, commentBadHeadShotOnLine).getBoolean();

        commentMobSpawnOrder = "Display the spawning order of certain types of mob.";
        MobSpawnOrder = config.get(Configuration.CATEGORY_GENERAL, "Mob Spawn Order", true, commentMobSpawnOrder).getBoolean();

        commentWhiteOutline = "Show white outlines on all zombie mobs";
        WhiteOutlineAllMobs = config.get(Configuration.CATEGORY_GENERAL, "White Outline All Mobs", false, commentWhiteOutline).getBoolean();

        commentHideOtherPlayers = "Hide other players to improve visibility";
        HideOtherPlayers = config.get(Configuration.CATEGORY_GENERAL, "Hide Other Players", false, commentHideOtherPlayers).getBoolean();

        commentBossHighlight = "Highlight bosses with red boxes and tracers";
        BossHighlight = config.get(Configuration.CATEGORY_GENERAL, "Boss Highlight", true, commentBossHighlight).getBoolean();

        commentMobChams = "Make mobs visible through walls";
        MobChams = config.get(Configuration.CATEGORY_GENERAL, "Mob Chams", false, commentMobChams).getBoolean();

        commentMobESP = "Show white hitboxes around mobs";
        MobESP = config.get(Configuration.CATEGORY_GENERAL, "Mob ESP", false, commentMobESP).getBoolean();

        commentMobCounter = "Show real-time mob counter overlay";
        MobCounter = config.get(Configuration.CATEGORY_GENERAL, "Mob Counter", false, commentMobCounter).getBoolean();

        config.save();
        logger.info("Finished loading config. ");
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.modID.equals(ZombiesExplorer.MODID)) {
            config.save();
            ConfigLoad();
        }
    }

    public Configuration getConfig() {
        return config;
    }

    public Logger logger()
    {
        return logger;
    }

    public SpawnPatternNotice getSpawnPatternNotice() {
        return spawnPatternNotice;
    }

    public KeyBinding keyToggleConfig;
    public KeyBinding keyToggleWhiteOutlines;
    public KeyBinding keyTogglePlayerVisibility;
    public KeyBinding keyToggleBossHighlight;
    public KeyBinding keyToggleMobChams;
    public KeyBinding keyToggleMobESP;
    public KeyBinding keyToggleMobCounter;

    @SubscribeEvent
    public void toggleGUI(InputEvent.KeyInputEvent event) {
        // Check each keybinding independently to allow for multiple key presses
        checkKeyBinding(keyToggleConfig);
        checkKeyBinding(keyToggleWhiteOutlines);
        checkKeyBinding(keyTogglePlayerVisibility);
        checkKeyBinding(keyToggleBossHighlight);
        checkKeyBinding(keyToggleMobChams);
        checkKeyBinding(keyToggleMobESP);
        checkKeyBinding(keyToggleMobCounter);
    }

    // New helper method to check and handle individual keybinds
    private void checkKeyBinding(KeyBinding key) {
        boolean isPressed = key.isPressed();

        // Only process the key press once (when it changes from not-pressed to pressed)
        if (isPressed && !keyWasPressed.get(key)) {
            // Config UI needs special handling due to GUI screen
            if (key == keyToggleConfig && Minecraft.getMinecraft().currentScreen == null) {
                new DelayedTask() {
                    @Override
                    public void run() {
                        Minecraft.getMinecraft().displayGuiScreen(new ZombiesExplorerGuiConfig(null));
                    }
                }.runTaskLater(2);
            }
            // Toggle for white outlines
            else if (key == keyToggleWhiteOutlines) {
                WhiteOutlineAllMobs = !WhiteOutlineAllMobs;
                String status = WhiteOutlineAllMobs ? "§aON" : "§4OFF";
                NotificationRenderer.getInstance().displayNotification("§eToggled White Outlines: " + status, 1000);
                config.get(Configuration.CATEGORY_GENERAL, "White Outline All Mobs", false).set(WhiteOutlineAllMobs);
                config.save();
            }
            // Toggle for player visibility - keeping original notification format
            else if (key == keyTogglePlayerVisibility) {
                HideOtherPlayers = !HideOtherPlayers;
                String status = HideOtherPlayers ? "§aON" : "§4OFF";
                NotificationRenderer.getInstance().displayNotification("§eToggled Player Visibility: " + status, 1000);
                config.get(Configuration.CATEGORY_GENERAL, "Hide Other Players", false).set(HideOtherPlayers);
                config.save();
            }
            // Toggle for boss highlighting
            else if (key == keyToggleBossHighlight) {
                BossHighlight = !BossHighlight;
                String status = BossHighlight ? "§aON" : "§4OFF";
                NotificationRenderer.getInstance().displayNotification("§eToggled Boss Highlight: " + status, 1000);
                config.get(Configuration.CATEGORY_GENERAL, "Boss Highlight", true).set(BossHighlight);
                config.save();
            }
            // Toggle for mob chams
            else if (key == keyToggleMobChams) {
                MobChams = !MobChams;
                String status = MobChams ? "§aON" : "§4OFF";
                NotificationRenderer.getInstance().displayNotification("§eToggled Chams: " + status, 1000);
                config.get(Configuration.CATEGORY_GENERAL, "Mob Chams", false).set(MobChams);
                config.save();
            }
            // Toggle for mob ESP
            else if (key == keyToggleMobESP) {
                MobESP = !MobESP;
                String status = MobESP ? "§aON" : "§4OFF";
                NotificationRenderer.getInstance().displayNotification("§eToggled ESP: " + status, 1000);
                config.get(Configuration.CATEGORY_GENERAL, "Mob ESP", false).set(MobESP);
                config.save();
            }
            // Toggle for mob counter
            else if (key == keyToggleMobCounter) {
                MobCounter = !MobCounter;
                String status = MobCounter ? "§aON" : "§4OFF";
                NotificationRenderer.getInstance().displayNotification("§eToggled Mob Counter: " + status, 1000);
                config.get(Configuration.CATEGORY_GENERAL, "Mob Counter", false).set(MobCounter);
                config.save();
            }
        }

        // Update the pressed state for next check
        keyWasPressed.put(key, isPressed);
    }

    // Add periodic check for keybind changes
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ENABLED) {
            return;
        }

        // Check if any keybinds have been changed
        checkKeybindChanges();
    }

    // Monitor for keybind changes
    private void checkKeybindChanges() {
        boolean anyChanged = false;

        // Check if any keybind has changed from its saved value
        if (keyToggleConfig.getKeyCode() != config.getInt("keyToggleConfig", KEYBIND_CATEGORY, Keyboard.KEY_K, 0, 256, "")) {
            config.get(KEYBIND_CATEGORY, "keyToggleConfig", Keyboard.KEY_K).set(keyToggleConfig.getKeyCode());
            anyChanged = true;
        }

        if (keyToggleWhiteOutlines.getKeyCode() != config.getInt("keyToggleWhiteOutlines", KEYBIND_CATEGORY, Keyboard.KEY_O, 0, 256, "")) {
            config.get(KEYBIND_CATEGORY, "keyToggleWhiteOutlines", Keyboard.KEY_O).set(keyToggleWhiteOutlines.getKeyCode());
            anyChanged = true;
        }

        if (keyTogglePlayerVisibility.getKeyCode() != config.getInt("keyTogglePlayerVisibility", KEYBIND_CATEGORY, Keyboard.KEY_P, 0, 256, "")) {
            config.get(KEYBIND_CATEGORY, "keyTogglePlayerVisibility", Keyboard.KEY_P).set(keyTogglePlayerVisibility.getKeyCode());
            anyChanged = true;
        }

        if (keyToggleBossHighlight.getKeyCode() != config.getInt("keyToggleBossHighlight", KEYBIND_CATEGORY, Keyboard.KEY_B, 0, 256, "")) {
            config.get(KEYBIND_CATEGORY, "keyToggleBossHighlight", Keyboard.KEY_B).set(keyToggleBossHighlight.getKeyCode());
            anyChanged = true;
        }

        if (keyToggleMobChams.getKeyCode() != config.getInt("keyToggleMobChams", KEYBIND_CATEGORY, Keyboard.KEY_C, 0, 256, "")) {
            config.get(KEYBIND_CATEGORY, "keyToggleMobChams", Keyboard.KEY_C).set(keyToggleMobChams.getKeyCode());
            anyChanged = true;
        }

        if (keyToggleMobESP.getKeyCode() != config.getInt("keyToggleMobESP", KEYBIND_CATEGORY, Keyboard.KEY_E, 0, 256, "")) {
            config.get(KEYBIND_CATEGORY, "keyToggleMobESP", Keyboard.KEY_E).set(keyToggleMobESP.getKeyCode());
            anyChanged = true;
        }

        if (keyToggleMobCounter.getKeyCode() != config.getInt("keyToggleMobCounter", KEYBIND_CATEGORY, Keyboard.KEY_M, 0, 256, "")) {
            config.get(KEYBIND_CATEGORY, "keyToggleMobCounter", Keyboard.KEY_M).set(keyToggleMobCounter.getKeyCode());
            anyChanged = true;
        }

        // If any keybind changed, save the config
        if (anyChanged) {
            config.save();
        }
    }

    public enum RenderType {
        POWERUP_PREDICT,
        POWERUP_ENSURED,
        BAD_HEADSHOT,
        DERIVED_BAD_HEADSHOT,
    }
}
