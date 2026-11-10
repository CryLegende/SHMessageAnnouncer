<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHMessageAnnouncer

*A modern message announcer with MiniMessage and PAPI support.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

A modern message announcer with MiniMessage and PAPI support.

Utilisé sur : **Opale**.

Avec `SHNetwork`, une seule rotation d'annonces tourne pour tous les serveurs Opale (voir « Mode réseau »). Sans lui, le plugin se comporte comme sur un serveur seul : chaque serveur fait tourner ses annonces.

## Dépendances

- Obligatoires : `PlaceholderAPI`
- Facultatives : `SHNetwork` (mode réseau)

## Mode réseau

Le mode réseau ne s'active que si `SHNetwork` est chargé et que `reseau.actif` vaut `true` (valeur par défaut). Une configuration sans la section `reseau` donne exactement le comportement d'un serveur seul.

- **Un seul minuteur.** Le serveur meneur (tâche `shmessageannouncer` de SHNetwork) fait tourner le minuteur `announce_interval`, choisit l'annonce et fait avancer l'ordre de `interval_announcement_list` (ou tire au hasard avec `announcer_random`). Les autres serveurs n'ont pas de minuteur.
- **Même annonce au même moment.** Le meneur publie l'identifiant de l'annonce sur le canal `reseau.canal` ; chaque serveur, le meneur compris, l'envoie à ses propres joueurs, avec le même son. Le texte vient du `config.yml` de chaque serveur, qui doit donc être le même partout : un identifiant absent est signalé une fois dans la console et rien n'est envoyé.
- **Placeholders.** Chaque serveur évalue les placeholders de PlaceholderAPI pour chacun de ses joueurs, sur place. Il n'y a pas de condition de permission ni de monde dans ce plugin : tous les joueurs connectés reçoivent l'annonce.
- **Ordre unique.** Seul le meneur fait avancer la liste. Chaque annonce du minuteur porte le rang de la suivante : si le meneur tombe, le serveur qui le remplace reprend la liste à cet endroit, au lieu de repartir du début. Rien n'est annoncé tant que personne n'est connecté sur le réseau, et l'ordre n'avance pas.
- **Commandes.**
  - `/msa announce <id>` envoie l'annonce à tous les joueurs de tous les serveurs, sans toucher à l'ordre ; chaque serveur évalue les placeholders pour chacun de ses joueurs.
  - `/msa announce <id> <joueur>` emprunte les placeholders d'un joueur, où qu'il soit sur le réseau : seul son serveur connaît ses données, il évalue donc l'annonce pour lui et les autres serveurs reçoivent le texte fini.
  - `/msa send <joueur> <id>` atteint le joueur où qu'il soit sur le réseau.
  - `/msa start` et `/msa stop` valent pour tout le réseau. L'état n'est pas gardé au redémarrage : au démarrage, `announcer_enabled` décide, comme avant.
  - `/msa reload` ne relit la configuration que de ce serveur. Sur le meneur, le minuteur repart du début de la liste.
- **Reprise.** Le verrou de meneur est renouvelé chaque seconde ; si le meneur s'arrête ou perd Redis, un autre serveur prend la main en quelques secondes et relance le minuteur d'un délai `announce_interval` neuf.

Clés de la section `reseau` du `config.yml` (un redémarrage est nécessaire pour les changer) :

| Clé | Rôle | Défaut |
|---|---|---|
| `actif` | Active le mode réseau quand SHNetwork est présent. | `true` |
| `canal` | Canal du bus SHNetwork utilisé entre les serveurs. | `messageannouncer` |

Il n'y a ni table ni import : le plugin ne stocke rien, les annonces sont dans le `config.yml`.

Au démarrage, la console indique `SHNetwork détecté : une seule rotation d'annonces pour tout le réseau`. `/shnetwork info` donne le serveur meneur de `shmessageannouncer`.

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/msa start` | `/shmessageannouncer` | Démarre les annonces automatiques (tout le réseau avec SHNetwork). | `shmessageannouncer.admin` |
| `/msa stop` | `/shmessageannouncer` | Arrête les annonces automatiques (tout le réseau avec SHNetwork). | `shmessageannouncer.admin` |
| `/msa reload` | `/shmessageannouncer` | Recharge `config.yml` et `messages.yml` sur ce serveur. | `shmessageannouncer.admin` |
| `/msa list <active\|all>` | `/shmessageannouncer` | Liste les annonces de la rotation, ou toutes les annonces. | `shmessageannouncer.admin` |
| `/msa announce <id> [joueur]` | `/shmessageannouncer` | Envoie une annonce à tous les joueurs (tous les serveurs avec SHNetwork), avec les placeholders du joueur donné. | `shmessageannouncer.admin` |
| `/msa send <joueur> <id>` | `/shmessageannouncer` | Envoie une annonce à un joueur. | `shmessageannouncer.admin` |

## Configuration

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`). Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHMessageAnnouncer/
├── <a href="src/main/resources/config.yml">config.yml</a>
└── <a href="src/main/resources/messages.yml">messages.yml</a>
</pre>

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
