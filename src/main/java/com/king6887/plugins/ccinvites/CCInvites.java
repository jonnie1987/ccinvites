package com.king6887.plugins.ccinvites;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.sql.*;

import java.util.Calendar;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 *
 * @author King6887
 */
public class CCInvites extends JavaPlugin {

    Logger log;
    Connection conn;
    double weekly;
    Boolean debug;

    public void onEnable() {
        log = this.getLogger();

        //Register events
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new CCIPlayerListener(this), this);

        YamlConfiguration c = new YamlConfiguration();
        try {
            c.load(new File(this.getDataFolder(), "settings.yml"));
        } catch (IOException ioe) {
            //file not found
            log.warning("Config file not found. Creating it.");
            createConfig(c);
        } catch (InvalidConfigurationException ice) {
            //file found, invalid format.
            log.severe("Config file is invalid, please check format.");
        }

        //Get invite settings from config
        weekly = c.getDouble("Invites.Weekly_Invites");
        debug = c.getBoolean("Invites.Debug");

        if (c.getString("Storage.Method").equals("sqlite")) {
            try {
                log.info("Using sqlite");

                //Create database table schema if it doesn't exist
                conn = DriverManager.getConnection("jdbc:sqlite:" + this.getDataFolder() + "/" + this.getName() + ".db");
                Statement stat = conn.createStatement();
                stat.executeUpdate("create table if not exists players (name PRIMARY KEY, joined, invites INTEGER, usedinvites INTEGER, extrainvites INTEGER, notes, status);");
                stat.executeUpdate("create table if not exists invited (player, invitee);");


            } catch (SQLException ex) {
                log.severe("SQLite database exception");
                ex.printStackTrace();
            }

        } else if (c.getString("Storage.Method").equals("mysql")) {
            try {
                log.info("Using mysql");

                //Get mysql settings from config
                String host = c.getString("Storage.Mysql_host");
                int port = c.getInt("Storage.Mysql_port");
                String user = c.getString("Storage.Mysql_user");
                String pass = c.getString("Storage.Mysql_pass");
                String db = c.getString("Storage.Mysql_database");

                //Create database table schema if it doesn't exist
                String constring = "jdbc:mysql://" + host + ":" + port + "/" + db + "?user=" + user + "&password=" + pass;
                log.info("Connection string: " + constring);
                conn = DriverManager.getConnection(constring);
                Statement stat = conn.createStatement();
                stat.executeUpdate("CREATE TABLE IF NOT EXISTS players ("
                        + "name VARCHAR(25) NOT NULL, "
                        + "joined DOUBLE NULL, "
                        + "invites INT NULL DEFAULT 0, "
                        + "usedinvites INT NULL DEFAULT 0, "
                        + "extrainvites INT NULL DEFAULT 0, "
                        + "notes BLOB NULL, "
                        + "status VARCHAR(50) NULL, "
                        + "PRIMARY KEY (`name`));");
                stat.executeUpdate("CREATE TABLE IF NOT EXISTS invited ("
                        + "player VARCHAR(25) NOT NULL, "
                        + "invitee VARCHAR(25) NOT NULL);");
            } catch (SQLException ex) {
                ex.printStackTrace();
            }

        } else {
            log.severe("Unknown storage type specified: " + c.getString("Storage.Method"));
        }

        log.info("Started");
    }

    public void onDisable() {
        try {
            conn.close();
        } catch (SQLException ex) {
            log.warning("Error closing Database connection");
            ex.printStackTrace();
        }
        log.info("Disabled");
    }

    private void createConfig(YamlConfiguration config) {
        config.createSection("Storage");
        config.createSection("Invites");

        ConfigurationSection str = config.getConfigurationSection("Storage");
        ConfigurationSection inv = config.getConfigurationSection("Invites");

        //Add default settings for data storage
        str.set("Method", "sqlite");
        str.set("Mysql_host", "localhost");
        str.set("Mysql_port", 3306);
        str.set("Mysql_user", "ccinvites");
        str.set("Mysql_pass", "ccinvites");
        str.set("Mysql_database", "ccinvites");

        //Add default settings for invites
        inv.set("Weekly_Invites", 0.5);
        inv.set("Debug", true); // TODO: change this to false
        try {
            config.save(new File(this.getDataFolder(), "settings.yml"));
        } catch (IOException e) {
            log.severe("Error saving file");
            e.printStackTrace();
        }
    }

    /**
     *
     * @param sender The person who typed the command.
     * @param command The command to execute.
     * @param label The alias used to execute the command.
     * @param args All other arguments to the command.
     * @return Result of command.
     */
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("invite")) {
            //Handle invite command
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.AQUA + "This command can only be used by a player");
                return true;
            }
            Player player = (Player) sender;
            if (args.length < 1) {
                return false;
            } else {
                if(player.hasPermission("ccinvites.invite")){
                    String note = "";
                    if (args.length > 1) {
                        for (int i = 1; i < args.length; i++) {
                            if (i != 1) {
                                note += " ";
                            }
                            note += args[i];
                        }
                    }
                    this.invitePlayer(player, args[0], note);
                } else {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use that command.");
                    return true;
                }
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("myinvites")) {
            //Handle myinvites info command
            if (!(sender instanceof Player)) {
                if (args.length == 1) {
                    sender.sendMessage(this.inviteDetails(args[0]));
                    return true;
                } else {
                    return false;
                }
            }
            if(sender.hasPermission("ccinvites.myinvites")){
                String player;
                if (args.length > 0) {
                    if(sender.hasPermission("ccinvites.myinvites.others")){
                        player = args[0];
                    } else {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to check others status.");
                        return true;
                    }
                } else {
                    player = sender.getName();
                }
                sender.sendMessage(this.inviteDetails(player));
                return true;
            } else {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use that command.");
                return true;
            }
        } else { //Other calls will be cci
            //Check if there are arguments to cci, show help if not.
            if(args.length < 1){
                sender.sendMessage(this.displayHelp(sender));
                return true;
            } else {
                //Handle commands
                if (args[0].equalsIgnoreCase("help")) {
                    sender.sendMessage(this.displayHelp(sender));
                    return true;
                } else if (args[0].equalsIgnoreCase("update")) {
                    if (!(sender instanceof Player)) {
                        if (args.length == 2) {
                            this.updatePlayer(args[1]);
                            return true;
                        } else {
                            return false;
                        }
                    }
                    if(sender.hasPermission("ccinvites.admin.update")){
                        String player;
                        if (args.length > 1) {
                            player = args[1];
                        } else {
                            player = sender.getName();
                        }
                        this.updatePlayer(player);
                    } else {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to use that command.");
                    }
                    return true;
                } else if (args[0].equalsIgnoreCase("extra")) {
                    if (args.length != 3) {
                        return false;
                    }
                    if(sender.hasPermission("ccinvites.admin.extras")){
                        String player = args[1];
                        Integer extra = Integer.parseInt(args[2]);

                        if (this.addExtraInvites(player, extra)) {
                            sender.sendMessage(ChatColor.GREEN + player + " has had " + extra + " invites added.");
                        } else {
                            sender.sendMessage(ChatColor.RED + "There was an error adding extra invites to " + player);
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to use that command.");
                    }
                    return true;
                } else if (args[0].equalsIgnoreCase("importcsv")) {
                    if (args.length != 2) {
                        return false;
                    }
                    if(sender.hasPermission("ccinvites.admin.import")){
                        String filename = args[1];

                        this.importCSV(filename);
                    } else {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to use that command.");
                    }
                    return true;
                } else {
                    return false;
                }
            }
        }
    }

    /**
     *
     * @param player The player to recalculate.
     */
    protected void updatePlayer(String player) {
        try {
            PreparedStatement stat = conn.prepareStatement("select * from players where name = ?;");
            stat.setString(1, player.toLowerCase());
            ResultSet rs = stat.executeQuery();

            if (rs.next()) {
                //Get difference between join time and now
                Calendar playerjoined = Calendar.getInstance();
                playerjoined.setTimeInMillis(rs.getLong("joined")*1000);
                if (this.debug) {
                    log.info("joined: " + rs.getLong("joined")*1000);
                }
                Calendar now = Calendar.getInstance();
                if (this.debug) {
                    log.info("now: " + now.getTimeInMillis());
                }
                long timediff = now.getTimeInMillis() - playerjoined.getTimeInMillis();
                if (this.debug) {
                    log.info(player + " timediff " + timediff);
                }

                int numinvites = (int) Math.floor(timediff / (1000 * 60 * 60 * 24 * 7));
                numinvites = (int) (numinvites * weekly);

                stat = conn.prepareStatement("update players set invites = ?, status='Active' where name = ?;");
                stat.setInt(1, numinvites);
                stat.setString(2, player.toLowerCase());
                stat.executeUpdate();
                if (this.debug) {
                    log.info(player + " has been updated to " + numinvites + " invites.");
                }

            } else {
                //player has been added to whitelist outside of the plugin.
                if (getServer().getOfflinePlayer(player).isWhitelisted()) {
                    addPlayer(player, "Active", "");
                    if (this.debug) {
                        log.info("player added to database, already on whitelist");
                    }
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }

    }

    /**
     *
     * @param player The player that is doing the inviting.
     * @param invitee The player that is being invited.
     */
    protected void invitePlayer(Player player, String invitee, String note) {
        try {
            //Ensures invites have been calculted prior to checking available invites
            updatePlayer(player.getDisplayName());

            PreparedStatement stat = conn.prepareStatement("select * from players where name = ?;");
            stat.setString(1, player.getDisplayName().toLowerCase());
            ResultSet rs = stat.executeQuery();
            if (rs.next()) {

                int i = rs.getInt("invites");
                int ei = rs.getInt("extrainvites");
                int ui = rs.getInt("usedinvites");

                //Check if the players current number of invites is greater than 0
                int ai = i + ei - ui;
                if (ai > 0) {
                    //Check if player is already whitelisted
                    if (!getServer().getOfflinePlayer(invitee.toLowerCase()).isWhitelisted()) {
                        //add player to db
                        addPlayer(invitee, "Invited", note);
                        if (this.debug) {
                            log.info("Player added to database: " + invitee + " by " + player.getDisplayName());
                        }

                        //add invite pairing
                        stat = conn.prepareStatement("insert into invited values (?, ?);");
                        stat.setString(1, player.getDisplayName().toLowerCase());
                        stat.setString(2, invitee.toLowerCase());
                        stat.executeUpdate();

                        //update player invites
                        ui++;
                        ai--;
                        stat = conn.prepareStatement("update players set usedinvites = ? where name = ?;");
                        stat.setInt(1, ui);
                        stat.setString(2, player.getDisplayName().toLowerCase());
                        stat.executeUpdate();

                        //add newly invited player to the whitelist
                        getServer().getOfflinePlayer(invitee).setWhitelisted(true);
                        player.sendMessage(ChatColor.GREEN + invitee + " was added to the whitelist. You have " + ai + " invites left.");
                    } else {
                        player.sendMessage(ChatColor.RED + invitee + " is already on the whitelist.");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "You don't have enough invites to add " + invitee);
                }
            }
            rs.close();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    /**
     *
     * @param player The player name to add.
     * @param status The status to add the player in. Invited / Active usually.
     */
    protected void addPlayer(String player, String status, String note) {
        try {
            PreparedStatement stat = conn.prepareStatement("insert into players values (?, ?, 0, 0, 0, ?, ?);");

            //add new player to database
            Calendar cal = Calendar.getInstance();
            stat.setString(1, player.toLowerCase());
            stat.setLong(2, (Long) cal.getTimeInMillis()/1000);
            stat.setString(3, note);
            stat.setString(4, status);
            stat.executeUpdate();

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    /**
     *
     * @param player The target player.
     * @param extra The number of extra invites to add (can be negative).
     */
    protected Boolean addExtraInvites(String player, int extra) {
        try {
            PreparedStatement stat = conn.prepareStatement("select * from players where name = ?;");
            stat.setString(1, player);
            ResultSet rs = stat.executeQuery();

            if (rs.next()) {
                int ei = rs.getInt("extrainvites");
                int newei = ei + extra;
                stat = conn.prepareStatement("update players set extrainvites = ? where name = ?;");
                stat.setInt(1, extra);
                stat.setString(2, player);
                stat.executeUpdate();

                if (this.debug) {
                    log.info("Extra invites for " + player + " set from " + ei + " to " + newei);
                }
                return true;
            }
            return false;
        } catch (SQLException ex) {
            log.severe("Error adding invites for " + player);
            ex.printStackTrace();
            return false;
        }
    }

    /**
     *
     * @param player The target player.
     * @param note The notes you wish to add to the player.
     */
    protected void addNotes(String player, String note) {
        try {
            PreparedStatement stat = conn.prepareStatement("select * from players where name = ?;");
            stat.setString(1, player.toLowerCase());
            ResultSet rs = stat.executeQuery();

            if (rs.next()) {
                String cn = rs.getString("notes");
                String nn = cn + "/n" + note;
                stat = conn.prepareStatement("update players set notes = ? where name = ?;");
                stat.setString(1, nn);
                stat.setString(2, player.toLowerCase());
                stat.executeUpdate();

                if (this.debug) {
                    log.info("Notes updated for " + player);
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    protected String[] inviteDetails(String player) {
        updatePlayer(player);
        String[] msgs = new String[5];
        try {
            PreparedStatement stat = conn.prepareStatement("select * from players where name = ?;");
            stat.setString(1, player.toLowerCase());
            ResultSet rs = stat.executeQuery();
            if (rs.next()) {
                int ui = rs.getInt("usedinvites");
                int i = rs.getInt("invites");
                int ei = rs.getInt("extrainvites");
                int tai = i + ei - ui;

                msgs[0] = ChatColor.AQUA + "Invite details for " + ChatColor.WHITE + player;
                msgs[1] = ChatColor.GOLD + "Used Invites: " + ChatColor.RED + ui;
                msgs[2] = ChatColor.GOLD + "Invites: " + ChatColor.WHITE + i;
                msgs[3] = ChatColor.GOLD + "Extras allocated: " + ChatColor.BLUE + ei;
                msgs[4] = ChatColor.GOLD + "Total Available Invites: " + ChatColor.GREEN + tai;
            } else {
                msgs[0] = ChatColor.RED + "Player " + player + " was not found.";
            }
            return msgs;
        } catch (SQLException ex) {
            log.severe("Error retrieving " + player + "'s invite details.");
            msgs[0] = ChatColor.RED + "Error retrieving " + player + "'s invite details.";
            ex.printStackTrace();
            return msgs;
        }
    }

    protected void importCSV(String filename) {
        try {
            PreparedStatement stat = conn.prepareStatement("insert into players values (?, ?, ?, ?, ?, ?, ?);");
            PreparedStatement stat2 = conn.prepareStatement("insert into invited values (?, ?);");
            PreparedStatement statcheck = conn.prepareStatement("select * from players where name = ?;");
            BufferedReader CSVFile = new BufferedReader(new FileReader(this.getDataFolder() + "/" + filename));

            String dataRow = CSVFile.readLine();

            while (dataRow != null) {
                String[] fd = dataRow.split(",");

                //check if plyer is already present in database
                statcheck.setString(1, fd[0].toLowerCase());
                ResultSet rs = statcheck.executeQuery();
                if (!rs.next()) {
                    //Get time of player joining
                    Calendar cal = Calendar.getInstance();
                    String[] invitetime = fd[4].split("-");
                    cal.set(Integer.parseInt(invitetime[0]), Integer.parseInt(invitetime[1]), Integer.parseInt(invitetime[2]), 0, 0, 0);

                    stat.setString(1, fd[0].toLowerCase());
                    stat.setLong(2, (Long) cal.getTimeInMillis()/1000);
                    stat.setInt(3, 0);
                    stat.setInt(4, Integer.parseInt(fd[2]));

                    //Check array length since extra invites and notes can be null
                    if (fd.length == 7) {
                        stat.setInt(5, 0);
                        stat.setString(6, "");
                    } else if (fd.length == 8) {
                        if (fd[7].isEmpty()) {
                            stat.setInt(5, 0);
                        } else {
                            stat.setInt(5, Integer.parseInt(fd[7]));
                        }
                        stat.setString(6, "");
                    } else {
                        if (fd[7].isEmpty()) {
                            stat.setInt(5, 0);
                        } else {
                            stat.setInt(5, Integer.parseInt(fd[7]));
                        }

                        if (!fd[8].equals("n/a")) {
                            stat.setString(6, fd[8]);
                        } else {
                            stat.setString(6, "");
                        }
                    }
                    stat.setString(7, "Invited");
                    stat.executeUpdate();

                    //Add who the player was invited by (not pre existing players)
                    if (!fd[6].equalsIgnoreCase("Pre-existing")) {
                        stat2.setString(1, fd[6].toLowerCase());
                        stat2.setString(2, fd[0].toLowerCase());
                        stat2.executeUpdate();
                    }
                    //Add player to whitelist
                    getServer().getOfflinePlayer(fd[0]).setWhitelisted(true);
                    if (this.debug) {
                        log.info("Importing user: " + fd[0]);
                    }
                } else {
                    if (this.debug) {
                        log.info("User: " + fd[0] + " is already present.");
                    }
                }

                dataRow = CSVFile.readLine(); // Read next line of data.
            }
            // Close the file once all data has been read.
            CSVFile.close();
            log.info("Data import completed successfully");
        } catch (IOException ioe) {
            log.warning("File not found for CSV import: " + filename);
            ioe.printStackTrace();
        } catch (SQLException sqle) {
            log.warning("Database error while importing : " + filename);
            sqle.printStackTrace();
        }
    }

    protected String[] displayHelp(CommandSender sender){
        String[] helptext = new String[7];
        helptext[0] = ChatColor.GREEN + "-----" + ChatColor.WHITE + " CCInvites Help " +
                ChatColor.GREEN + "------------------------";
        if(sender.hasPermission("ccinvites.invite")){
            helptext[1] = ChatColor.GOLD + "/invite <player> [note]" + ChatColor.WHITE + " invite a player, with optional note";
        }
        if(sender.hasPermission("ccinvites.myinvites")){
            helptext[2] = ChatColor.GOLD + "/myinvites [player]" + ChatColor.WHITE + " shows your invite status";
        }
        helptext[3] = ChatColor.GOLD + "/cci help" + ChatColor.WHITE + " shows this help text";
        if(sender.hasPermission("ccinvites.admin")){
            helptext[4] = ChatColor.GOLD + "/cci update <player>" + ChatColor.WHITE + " force update player invite counts";
            helptext[5] = ChatColor.GOLD + "/cci extra <player> <amount>" + ChatColor.WHITE + " add extra invites to a player";
            helptext[6] = ChatColor.GOLD + "/cci importcsv <filename>" + ChatColor.WHITE + " Import csv file (must be in plugins folder)";
        }
        return helptext;
    }
}
