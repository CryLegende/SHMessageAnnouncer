package org.Cry_Legende.sHMessageAnnouncer.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.Cry_Legende.sHMessageAnnouncer.SHMessageAnnouncer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Une seule rotation d'annonces pour tout le réseau. Le meneur (tâche {@code shmessageannouncer} de SHNetwork)
 * fait tourner le minuteur, choisit l'annonce et fait avancer l'ordre ; chaque serveur, le meneur compris,
 * l'affiche à ses propres joueurs, en lisant ses propres placeholders.
 *
 * <pre>
 * canal   serveur -> autres   {event: announce, id, index?}   annonce à tous les joueurs, évaluée sur chaque serveur
 *                             {event: shared, id, papi}       /shmessageannouncer announce avec un joueur : le serveur de ce joueur
 *                                                             évalue l'annonce pour lui et la publie en text
 *                             {event: text, lines}            annonce déjà évaluée, envoyée telle quelle
 *                             {event: send, uuid, id}         annonce à un joueur
 *                             {event: state, running}         /shmessageannouncer start et /shmessageannouncer stop
 * </pre>
 *
 * {@code index} accompagne les annonces du minuteur : c'est le rang de la prochaine annonce de la liste. Chaque serveur
 * le retient, pour qu'un nouveau meneur reprenne l'ordre là où le précédent s'est arrêté. Les textes, le son et la
 * liste viennent du config.yml de chaque serveur : il doit être le même partout.
 */
public final class NetworkAnnouncer {

    private static final String TASK = "shmessageannouncer";

    private final SHMessageAnnouncer plugin;
    private final NetBus bus;
    private final String channel;

    private volatile NetBus.Leader leader;
    /** Annonces automatiques voulues sur le réseau. Lu et écrit par le fil principal. */
    private boolean running;
    /** Rang de la prochaine annonce de la liste, tel que le dernier meneur l'a publié. Fil principal. */
    private int nextIndex;
    /** Annonces reçues du réseau mais absentes de ce config.yml, déjà signalées dans la console. */
    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    public NetworkAnnouncer(SHMessageAnnouncer plugin, NetBus bus, String channel) {
        this.plugin = plugin;
        this.bus = bus;
        this.channel = channel;
    }

    // ------------------------------------------------------------------ cycle de vie

    public void enable() {
        bus.listen(channel, this::onMessage, true);
        leader = bus.leader(TASK, this::acquired, this::lost);
    }

    public void disable() {
        NetBus.Leader current = leader;
        if (current != null) {
            current.release();
        }
    }

    public boolean isLeader() {
        NetBus.Leader current = leader;
        return current != null && current.isLeader();
    }

    /** Ce serveur devient meneur : le minuteur repart, dans l'ordre où le précédent s'était arrêté. */
    private void acquired() {
        if (running) {
            plugin.startTask(nextIndex);
        }
    }

    /** Ce serveur n'est plus meneur : le minuteur s'arrête ici. */
    private void lost() {
        plugin.stopTask();
    }

    /** La configuration de ce serveur vient d'être (re)lue : l'état voulu suit {@code announcer_enabled}. */
    public void configLoaded(boolean enabled) {
        reported.clear();
        running = enabled;
        nextIndex = 0;
        if (running && isLeader()) {
            plugin.startTask(0);
        }
    }

    // ------------------------------------------------------------------ état commun

    public boolean isRunning() {
        return running;
    }

    /** {@code /shmessageannouncer start} et {@code /shmessageannouncer stop} : l'état change sur tous les serveurs. */
    public void setRunning(boolean value) {
        apply(value);
        JsonObject message = new JsonObject();
        message.addProperty("event", "state");
        message.addProperty("running", value);
        bus.publish(channel, message);
    }

    private void apply(boolean value) {
        running = value;
        plugin.stopTask();
        if (value) {
            nextIndex = 0;
            if (isLeader()) {
                plugin.startTask(0);
            }
        }
    }

    // ------------------------------------------------------------------ annonces

    /** Personne n'est connecté sur le réseau : le minuteur n'annonce rien et ne fait pas avancer l'ordre. */
    public boolean anyoneOnline() {
        return !Bukkit.getOnlinePlayers().isEmpty() || bus.online() > 0;
    }

    /**
     * Annonce du minuteur, choisie par le meneur : affichée ici et publiée pour les autres serveurs.
     *
     * @param index rang de la prochaine annonce de la liste, ou -1 en ordre aléatoire
     */
    public void publishTimer(String id, int index) {
        if (index >= 0) {
            nextIndex = index;
        }
        show(id);
        JsonObject message = new JsonObject();
        message.addProperty("event", "announce");
        message.addProperty("id", id);
        if (index >= 0) {
            message.addProperty("index", index);
        }
        bus.publish(channel, message);
    }

    /**
     * {@code /shmessageannouncer announce}, sans joueur ou avec un joueur de ce serveur : l'annonce part à tous les joueurs de tous
     * les serveurs, sans toucher à l'ordre. Avec un joueur, ses placeholders servent à toutes les lignes : elles sont
     * évaluées ici, puisque lui seul y est connu, et les autres serveurs reçoivent le texte fini.
     *
     * @param papi joueur de ce serveur dont les placeholders servent, ou null
     */
    public void announce(String id, Player papi) {
        if (papi != null) {
            share(id, papi);
            return;
        }
        show(id);
        JsonObject message = new JsonObject();
        message.addProperty("event", "announce");
        message.addProperty("id", id);
        bus.publish(channel, message);
    }

    /** {@code /shmessageannouncer announce} avec un joueur d'un autre serveur : c'est ce serveur-là qui évalue l'annonce. */
    public void announceFor(String id, UUID papi) {
        JsonObject message = new JsonObject();
        message.addProperty("event", "shared");
        message.addProperty("id", id);
        message.addProperty("papi", papi.toString());
        bus.publish(channel, message);
    }

    /** {@code /shmessageannouncer send} vers un joueur connecté sur le réseau : le serveur où il se trouve lui envoie l'annonce. */
    public void send(UUID target, String id) {
        deliver(target, id);
        JsonObject message = new JsonObject();
        message.addProperty("event", "send");
        message.addProperty("uuid", target.toString());
        message.addProperty("id", id);
        bus.publish(channel, message);
    }

    /** UUID d'un joueur connecté sur le réseau (ce serveur compris), ou null. */
    public UUID find(String name) {
        return bus.find(name);
    }

    /** Pseudos des joueurs de tout le réseau, pour la complétion des commandes. */
    public List<String> playerNames() {
        return bus.names();
    }

    // ------------------------------------------------------------------ réception

    /** Fil principal : message d'un autre serveur. */
    private void onMessage(JsonObject o) {
        try {
            String event = text(o, "event");
            if (event == null) {
                return;
            }
            switch (event) {
                case "announce" -> onAnnounce(o);
                case "shared" -> onShared(o);
                case "text" -> onText(o);
                case "send" -> onSend(o);
                case "state" -> {
                    if (o.has("running")) {
                        apply(o.get("running").getAsBoolean());
                    }
                }
                default -> {
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Message illisible sur " + channel + " : " + e);
        }
    }

    private void onAnnounce(JsonObject o) {
        String id = text(o, "id");
        if (id == null) {
            return;
        }
        if (o.has("index")) {
            nextIndex = o.get("index").getAsInt();
        }
        show(id);
    }

    private void onShared(JsonObject o) {
        String id = text(o, "id");
        String uuid = text(o, "papi");
        if (id == null || uuid == null) {
            return;
        }
        Player papi = Bukkit.getPlayer(UUID.fromString(uuid));
        if (papi != null) {
            share(id, papi);
        }
    }

    private void onText(JsonObject o) {
        if (!o.has("lines")) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (JsonElement line : o.getAsJsonArray("lines")) {
            lines.add(line.getAsString());
        }
        plugin.getAnnouncementManager().broadcastRendered(lines);
    }

    private void onSend(JsonObject o) {
        String id = text(o, "id");
        String uuid = text(o, "uuid");
        if (id == null || uuid == null) {
            return;
        }
        deliver(UUID.fromString(uuid), id);
    }

    /** Évalue l'annonce pour ce joueur, l'envoie aux joueurs de ce serveur et publie le texte fini pour les autres. */
    private void share(String id, Player papi) {
        List<String> lines = known(id) ? plugin.getAnnouncementManager().render(id, papi) : null;
        if (lines == null) {
            return;
        }
        plugin.getAnnouncementManager().broadcastRendered(lines);
        JsonArray array = new JsonArray();
        lines.forEach(array::add);
        JsonObject message = new JsonObject();
        message.addProperty("event", "text");
        message.add("lines", array);
        bus.publish(channel, message);
    }

    /** Envoie l'annonce au joueur s'il est sur ce serveur. */
    private void deliver(UUID uuid, String id) {
        Player target = Bukkit.getPlayer(uuid);
        if (target != null && known(id)) {
            plugin.getAnnouncementManager().sendAnnouncement(target, id, target);
        }
    }

    /** Affiche l'annonce à tous les joueurs de ce serveur, chacun avec ses placeholders. */
    private void show(String id) {
        if (known(id)) {
            plugin.getAnnouncementManager().broadcast(id, null);
        }
    }

    /** Vrai si l'annonce existe dans ce config.yml ; sinon la console le dit une fois (configurations différentes). */
    private boolean known(String id) {
        if (plugin.getAnnouncementManager().getAnnouncement(id) != null) {
            return true;
        }
        if (reported.add(id)) {
            plugin.getLogger().warning("Annonce '" + id + "' reçue du réseau mais absente de config.yml : "
                + "le config.yml doit être le même sur tous les serveurs.");
        }
        return false;
    }

    private static String text(JsonObject o, String key) {
        JsonElement element = o.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }
}
