package net.madjawa.pluginreloader;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.SimplePluginManager;
import org.bukkit.plugin.UnknownDependencyException;
import org.bukkit.plugin.java.JavaPlugin;

public class PluginReloader extends JavaPlugin
{
  static final Logger log = Logger.getLogger("Minecraft");

  public void onEnable()
  {
    log.info("[PluginReloader] Version 0.1 enabled (by MadJawa)");
  }

  public void onDisable()
  {
    log.info("[PluginReloader] Plugin disabled");
  }

  public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args)
  {
    if (cmd.getName().equalsIgnoreCase("plugin")) {
      if (args.length < 1) {
        return false;
      }

      String action = args[0];

      if ((!action.equalsIgnoreCase("load")) && (!action.equalsIgnoreCase("unload")) && (!action.equalsIgnoreCase("reload"))) {
        sender.sendMessage(ChatColor.GOLD + "Invalid action specified");

        return false;
      }

      if (!sender.hasPermission("pluginreloader." + action)) {
        sender.sendMessage(ChatColor.RED + "You do not have the permission to do this");

        return true;
      }

      if (args.length == 1) {
        sender.sendMessage(ChatColor.GOLD + "You must specify at least one plugin");

        return true;
      }

      for (int i = 1; i < args.length; i++) {
        String plName = args[i];
        try
        {
          if (action.equalsIgnoreCase("unload")) {
            unloadPlugin(plName);

            sender.sendMessage(ChatColor.GRAY + "Unloaded " + ChatColor.RED + plName + ChatColor.GRAY + " successfully!");
          } else if (action.equalsIgnoreCase("load")) {
            loadPlugin(plName);

            sender.sendMessage(ChatColor.GRAY + "Loaded " + ChatColor.GREEN + plName + ChatColor.GRAY + " successfully!");
          } else if (action.equalsIgnoreCase("reload")) {
            unloadPlugin(plName);
            loadPlugin(plName);

            sender.sendMessage(ChatColor.GRAY + "Reloaded " + ChatColor.GREEN + plName + ChatColor.GRAY + " successfully!");
          }
        } catch (Exception e) {
          sender.sendMessage(ChatColor.GRAY + "Error with " + ChatColor.RED + plName + ChatColor.GRAY + ": " + ChatColor.GOLD + getExceptionMessage(e) + ChatColor.GRAY + " (check console for more details)");
        }
      }

      return true;
    }

    return false;
  }

  private static String getExceptionMessage(Throwable e)
  {
    if (e.getCause() != null) {
      String msg = getExceptionMessage(e.getCause());

      if (!msg.equalsIgnoreCase(e.getClass().getName())) {
        return msg;
      }
    }

    if (e.getLocalizedMessage() != null)
      return e.getLocalizedMessage();
    if (e.getMessage() != null)
      return e.getMessage();
    if (e.getClass().getCanonicalName() != null) {
      return e.getClass().getCanonicalName();
    }
    return e.getClass().getName();
  }

  private void unloadPlugin(String pluginName)
    throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException
  {
    PluginManager manager = getServer().getPluginManager();

    SimplePluginManager spm = (SimplePluginManager)manager;

    List plugins = null;
    Map lookupNames = null;
    Map listeners = null;
    SimpleCommandMap commandMap = null;
    Map knownCommands = null;

    if (spm != null)
    {
      Field pluginsField = spm.getClass().getDeclaredField("plugins");
      Field lookupNamesField = spm.getClass().getDeclaredField("lookupNames");
      Field listenersField = spm.getClass().getDeclaredField("listeners");
      Field commandMapField = spm.getClass().getDeclaredField("commandMap");

      pluginsField.setAccessible(true);
      lookupNamesField.setAccessible(true);
      listenersField.setAccessible(true);
      commandMapField.setAccessible(true);

      plugins = (List)pluginsField.get(spm);
      lookupNames = (Map)lookupNamesField.get(spm);
      listeners = (Map)listenersField.get(spm);
      commandMap = (SimpleCommandMap)commandMapField.get(spm);

      Field knownCommandsField = commandMap.getClass().getDeclaredField("knownCommands");

      knownCommandsField.setAccessible(true);

      knownCommands = (Map)knownCommandsField.get(commandMap);
    }

    for (Plugin pl : manager.getPlugins()) {
      if (!pl.getDescription().getName().equalsIgnoreCase(pluginName))
        continue;
      manager.disablePlugin(pl);

      if ((plugins != null) && (plugins.contains(pl))) {
        plugins.remove(pl);
      }

      if ((lookupNames != null) && (lookupNames.containsKey(pluginName))) {
        lookupNames.remove(pluginName);
      }

      if (listeners != null)
        for (SortedSet set : listeners.values())
          for (it = set.iterator(); it.hasNext(); ) {
            RegisteredListener value = (RegisteredListener)it.next();

            if (value.getPlugin() == pl)
              it.remove();
          }
      Iterator it;
      Iterator it;
      if (commandMap != null) {
        for (it = knownCommands.entrySet().iterator(); it.hasNext(); ) {
          Map.Entry entry = (Map.Entry)it.next();

          if ((entry.getValue() instanceof PluginCommand)) {
            PluginCommand c = (PluginCommand)entry.getValue();

            if (c.getPlugin() == pl) {
              c.unregister(commandMap);

              it.remove();
            }
          }
        }
      }
      try
      {
        List permissionlist = pl.getDescription().getPermissions();
        Iterator p = permissionlist.iterator();
        while (p.hasNext())
          manager.removePermission(p.next().toString());
      }
      catch (NoSuchMethodError e) {
        log.info("[PluginReloader] " + pluginName + " has no permissions to unload.");
      }
    }
  }

  private void loadPlugin(String pluginName)
    throws InvalidPluginException, InvalidDescriptionException, UnknownDependencyException
  {
    PluginManager manager = getServer().getPluginManager();

    Plugin plugin = manager.loadPlugin(new File("plugins", pluginName + ".jar"));

    if (plugin == null) {
      return;
    }

    manager.enablePlugin(plugin);
    try
    {
      List permissionlist = plugin.getDescription().getPermissions();
      Iterator p = permissionlist.iterator();
      while (p.hasNext())
        manager.removePermission(p.next().toString());
    }
    catch (NoSuchMethodError e) {
      log.info("[PluginReloader] " + pluginName + " has no permissions to load.");
    }
  }
}