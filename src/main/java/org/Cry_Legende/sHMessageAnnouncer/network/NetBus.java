package org.Cry_Legende.sHMessageAnnouncer.network;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Ce dont les annonces en réseau ont besoin du bus. Seule {@link ShNetworkBus} connaît SHNetwork : sans lui, aucune
 * classe de ce paquet n'est chargée.
 */
public interface NetBus {

    /** Candidature à une tâche de meneur. */
    interface Leader {

        boolean isLeader();

        void release();
    }

    /** Joueurs connectés sur tout le réseau, ce serveur compris. */
    int online();

    /** UUID d'un joueur connecté quelque part sur le réseau (ce serveur compris), s'il l'est. */
    UUID find(String name);

    /** Pseudos des joueurs connectés sur tout le réseau, ce serveur compris. */
    List<String> names();

    /** Publie sur un canal à destination des autres serveurs, pas de celui-ci. Ne bloque jamais. */
    void publish(String channel, JsonObject message);

    /**
     * Écoute un canal.
     *
     * @param mainThread vrai pour recevoir sur le fil principal ; faux pour recevoir sur le fil du bus, où le
     *                   gestionnaire ne doit ni attendre ni toucher au disque ou à la base
     */
    void listen(String channel, Consumer<JsonObject> handler, boolean mainThread);

    /** Se porte candidat pour une tâche qui ne tourne que sur un serveur à la fois. */
    Leader leader(String task, Runnable onAcquire, Runnable onLose);
}
