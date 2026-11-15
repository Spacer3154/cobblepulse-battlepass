# CobblePulse — Passe de Combat

Passe de Combat saisonnier pour **Fabric 1.21.1** et **Cobblemon 1.8.4**, en français. Il comprend :

- une piste **gratuite** et une piste **premium** de paliers à réclamer ;
- des **quêtes** quotidiennes, hebdomadaires et de saison ;
- des **chasses** solo et globales, reprises de CobbleHunts ;
- une **récompense de connexion** chaque jour ;
- un **éditeur graphique** complet pour les admins.

Le mod reprend l'idée et l'écran de *Cobblemon Trainer Pass* (Fredrick Von Burger, licence MIT), réécrits de zéro. Les chasses sont un portage de *CobbleHunts* (Hysocs, LGPL-3.0) : EverlastingUtils n'est plus nécessaire.

## Installation

| Côté | Requis | Recommandé |
|---|---|---|
| Serveur | Fabric Loader ≥ 0.16, Fabric API, Fabric Language Kotlin, Cobblemon 1.8.4, **cobblepulse-battlepass.jar** | LuckPerms, Text Placeholder API, CobblePulse Battletower, Jobs+ (cobblepulse-jobs) |
| Client (modpack) | **cobblepulse-battlepass.jar** | — |

Le mod côté client affiche l'écran du passe et l'éditeur. Sans lui, `/passe` donne un résumé dans le chat et les chasses se remettent par commande.

1. Déposer le `.jar` dans `mods/` (serveur et modpack).
2. Démarrer une fois : les fichiers sont créés dans `config/cobblepulse_battlepass/` avec un passe d'exemple (50 paliers, 30 jours).
3. Ouvrir l'éditeur avec `/passe admin` et adapter la saison, les récompenses, les quêtes et les chasses.

SQLite, MySQL (HikariCP) et NightConfig sont embarqués dans le jar.

## Fichiers (`config/cobblepulse_battlepass/`)

| Fichier | Contenu |
|---|---|
| `saison.json` | nom, durée en jours, paliers, points par palier, ouverture, fin de saison, quêtes tirées, premium (achat en jeu), connexion quotidienne, grades LuckPerms, étoiles du chat |
| `recompenses.json` | récompenses de chaque palier, pistes `gratuit` et `premium` |
| `quetes.json` | réserves `quotidiennes`, `hebdomadaires` et `saison` |
| `chasses.json` | réglages des chasses, Pokémon possibles et butin par difficulté |
| `messages.toml` | tous les textes du chat |
| `storage.toml` | `json` (défaut), `mysql` ou `sqlite`. `share_cobblepulse = true` reprend la base de CobblePulse |
| `data/players/[uuid].json` | progression des joueurs (mode JSON) |

En MySQL ou SQLite, les tables sont `cp_bp_players` et `cp_bp_kv`. Les fichiers JSON peuvent être modifiés à la main, puis rechargés avec `/passe admin reload`. L'éditeur fait la même chose sans redémarrage.

## Fonctionnement

- **Saison** : elle commence au premier démarrage et dure `jours`. À la fin, le passe se ferme, ou une nouvelle saison commence si `fin_de_saison = "nouvelle_saison"`. Après la fin, les joueurs peuvent encore réclamer leurs paliers. Une nouvelle saison (bouton de l'éditeur ou `/passe admin saison nouvelle confirmer`) remet tout le monde à zéro, premium compris.
- **Maintenance** : `/passe admin fermer` arrête la progression et les réclamations. La progression des joueurs est conservée.
- **Paliers** : `paliers` × `points_par_palier`. Un coût particulier par palier est possible (`points_par_palier_liste`). Les récompenses d'un palier se réclament d'un clic, en tout ou rien : si une récompense ne peut pas être donnée, rien n'est donné et le joueur peut réessayer.
- **Points de passe** : ils viennent des quêtes, de la connexion quotidienne (avec bonus de série), des chasses et du staff. Les admins peuvent lancer un bonus temporaire (`/passe admin boost 2 60`).
- **Premium** : il est accordé pour la saison en cours de l'une des façons suivantes :
  - par commande (boutique en ligne) ;
  - par un achat en jeu, au choix en coins Jobs+, en BP de la Tour ou avec un objet ;
  - par la permission `cobblepulse.battlepass.premium`.

### Récompenses

| Type | Détail |
|---|---|
| Objet | Objet complet (enchantements, nom, composants). Dans l'éditeur, **« Prendre l'objet tenu »** copie l'objet de la main. La quantité peut dépasser 64. |
| Pokémon | Espèce ou fakemon (`monpack:volt_mon`), forme et aspects, niveau, chromatique, nature, talent, genre, objet tenu, Poké Ball, surnom, IV, EV, 4 capacités, type Téra. Le bouton **Vérifier** crée le Pokémon côté serveur pour valider la fiche. |
| Commande | Une ou plusieurs commandes console (`%player%`, `%uuid%`), avec une icône et un nom affichés. |
| BP Battletower | Battle Points de CobblePulse Battletower, donnés même à un joueur hors ligne. |
| Coins Jobs+ | Coins de Jobs+ (utilisés par cobblepulse-jobs). |
| Points de passe | Utile dans le butin des chasses. |

### Quêtes

Les quêtes quotidiennes et hebdomadaires sont tirées au sort dans leur réserve. Le tirage est propre à chaque joueur ou commun à tous, selon `selection_quetes`. Les quêtes de saison sont toutes actives. Une quête marquée premium est visible par tout le monde, mais ne progresse que pour les joueurs premium.

| Type | Conditions possibles |
|---|---|
| `capture` | espèces, types (fr/en), catégories (légendaire, fabuleux…), formes, natures, chromatique, genre, niveau, Poké Ball, biome, dimension, jour/nuit |
| `vaincre_sauvage` | conditions Pokémon, biome, dimension, jour/nuit |
| `victoire_combat` | adversaire : sauvage, dresseur (PNJ) ou joueur |
| `evolution`, `eclosion`, `echange`, `niveau`, `peche_pokemon`, `fossile`, `pokedex` | conditions Pokémon |
| `baies`, `noigrumes` | objets (baies et noigrumes de Cobblemon) |
| `minage` | blocs ou tags (`#c:ores`). Les blocs posés par un joueur ne comptent pas. |
| `recolte` | cultures mûres (blé, carottes, baies sucrées, cultures moddées à âge…), melons, citrouilles, canne à sucre |
| `tuer` | créatures ou tags |
| `peche`, `fabrication`, `manger` | objets ou tags |
| `livraison` | objets à déposer depuis l'écran (bouton **Livrer**) |
| `elevage`, `distance` (en blocs), `temps_jeu` (minutes, sans compter l'inactivité), `connexion` (jours) | — |
| `tour_etage`, `tour_complete`, `tour_boss` | Tour de Combat (étage minimum) |
| `chasse` | difficulté, espèce |
| `stat` | n'importe quelle statistique Minecraft (`minecraft:custom/minecraft:jump`…) |
| `manuelle` | validée par le staff (`/passe staff quete valider <joueur> <id>`), par exemple pour un évènement |

### Chasses

Il y a quatre chasses solo : **facile**, **normale**, **moyenne** et **difficile**. Pour chacune, le joueur procède ainsi :

1. Il voit un aperçu du Pokémon demandé, puis lance la chasse.
2. Il capture ce Pokémon avant la fin du temps imparti. Selon la chasse, un genre, une nature ou un nombre d'IV au-dessus du minimum peuvent être exigés.
3. Il le **remet** depuis l'onglet Chasses (Pokémon de l'équipe ou du PC). Il gagne alors les points de la difficulté et un lot de butin.

Une recharge sépare deux chasses. Les **chasses globales** arrivent par vagues, communes à tous les joueurs. Les options suivantes reprennent celles de CobbleHunts :

- capture après le début de la chasse ;
- Pokémon repris lors de la remise ;
- remise automatique à la capture ;
- lancement automatique ;
- mode de butin (poids ou pourcentage).

## LuckPerms et ShadowChat

- À sa connexion pendant une saison, chaque joueur reçoit le groupe **`passe_gratuit`** ou **`passe_premium`**. Ce grade est **temporaire** : il expire à la fin de la saison. Si la durée change, l'expiration est recalculée pour tous les joueurs. Les groupes sont créés automatiquement (nom et poids réglables).
- L'étoile du chat vient du placeholder **`%cobblepulse_battlepass:etoile%`** : `☆` pour le passe gratuit, `★` pour le premium. Les couleurs se règlent dans `saison.json` → `etoiles`. Le fichier `exemples/shadowchat/chat_config.json` reprend la config ShadowChat de CobblePulse avec l'étoile placée devant le pseudo, dans le chat, le tab et le nametag. Il faut Text Placeholder API.
- Autres placeholders : `palier`, `points`, `premium`, `serie`, `a_reclamer`, `points_prochain_palier`, `saison`, `paliers`, `temps_restant`, `statut`.

**Boutique en ligne (Tebex…)** : la commande à exécuter est `passe admin premium donner {username}`. On peut aussi accorder la permission `cobblepulse.battlepass.premium`, qui est détectée à la connexion et dans la minute.

## Commandes et permissions

| Commande | Permission |
|---|---|
| `/passe` (`/battlepass`), `info`, `reclamer [palier]`, `quetes`, `premium acheter`, `top` | `cobblepulse.battlepass.use` |
| `/chasses` (`/hunts`), `lancer`, `abandonner`, `remettre <difficulte> [n°]` | `cobblepulse.battlepass.chasses` |
| `/passe staff points donner\|retirer\|definir <joueur> <montant>` | `cobblepulse.battlepass.staff.points` |
| `/passe staff voir <joueur>` | `cobblepulse.battlepass.staff.inspect` |
| `/passe staff quete valider <joueur> <id>` | `cobblepulse.battlepass.staff.quetes` |
| `/passe staff chasses recharge <joueur>` | `cobblepulse.battlepass.staff.chasses` |
| `/passe admin` (éditeur graphique) | `cobblepulse.battlepass.admin.edit` |
| `/passe admin ouvrir\|fermer` | `cobblepulse.battlepass.admin.toggle` |
| `/passe admin jours <n>`, `/passe admin saison nouvelle confirmer` | `cobblepulse.battlepass.admin.saison` |
| `/passe admin reset <joueur> [complet]`, `/passe admin reset tous confirmer [complet]` | `cobblepulse.battlepass.admin.reset` |
| `/passe admin premium donner\|retirer <joueur>` | `cobblepulse.battlepass.admin.premium` |
| `/passe admin boost <multiplicateur> <minutes>`, `boost stop` | `cobblepulse.battlepass.admin.boost` |
| `/passe admin reload`, `/passe admin chasses relancer` | `cobblepulse.battlepass.admin.reload` / `admin.edit` |

`cobblepulse.battlepass.staff.*` donne toutes les permissions staff, `cobblepulse.battlepass.admin.*` toutes les permissions admin. Sans LuckPerms, les joueurs ont accès au passe, le staff correspond aux opérateurs de niveau 2 et les admins au niveau 3. La touche **B** (modifiable) ouvre le passe.

## Développement

Le code compile et se teste sur GitHub Actions (`.github/workflows/build.yml`) :

- un vrai serveur Fabric démarre avec Cobblemon 1.8.4, LuckPerms, Text Placeholder API et CobblePulse Battletower, en JSON, MySQL puis SQLite ;
- des joueurs factices réclament des paliers, reçoivent des Pokémon personnalisés et des BP, progressent dans les quêtes (capture, minage, récolte, statistiques, livraison, mob tué, Tour de Combat) et font des chasses ;
- les tests vérifient aussi les grades LuckPerms et leur expiration, l'étoile du chat et la persistance après redémarrage ;
- un client est lancé sous xvfb pour capturer les écrans.

Les résultats sont publiés sur la branche `ci-output`.

Licence : LGPL-3.0 (le mod reprend du code de CobbleHunts). Cobblemon Trainer Pass est sous licence MIT (© Fredrick Von Burger).
