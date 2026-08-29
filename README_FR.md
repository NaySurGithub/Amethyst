<div align="center">
  <h1>💎 Amethyst 🛡️</h1>
  <p>Un anti-cheat par prédiction pour PowerNukkitX</p>
  <p><a href="README.md">🇬🇧 Read in English</a></p>

  <p><i>La prédiction de mouvement - le serveur rejoue le déplacement du joueur et corrige ce qu'il ne peut pas expliquer.</i></p>
</div>

https://github.com/user-attachments/assets/3234cf7e-ab7a-4094-abf4-8962ffd592f8

<div align="center">
  <p><i>La prédiction de véhicule - un bateau renvoyé en arrière avec son passager toujours à bord.</i></p>
</div>

https://github.com/user-attachments/assets/3801e2b6-ccc6-463d-b7f6-ccfa25b209c6

<div align="center">
  <p><sub>Les deux enregistrements ont été faits sur un serveur de test privé pour montrer les checks réagir.
  Rien ici ne fait la promotion de la triche : aucun client de triche n'est lié ni distribué.</sub></p>
</div>

## 📖 De quoi il s'agit

La plupart des anti-cheats Bedrock comparent le déplacement d'un joueur à un seuil : bouge de plus de *n* blocs
en un tick et tu es flag. Ça n'attrape rien de subtil, et ça punit quiconque a une mauvaise connexion.

Amethyst rejoue le mouvement à la place. À chaque tick, il fait passer l'entrée envoyée par le client dans une
réimplémentation de la physique de Bedrock, puis compare le résultat à la position que le client annonce. Ce
qu'on mesure n'est pas *à quelle vitesse tu t'es déplacé*, mais **quelle part de ton déplacement les règles du
jeu ne peuvent pas expliquer**.

## 📥 Installation

Dépose le jar dans le dossier `plugins/` de ton serveur. Aucune dépendance.

Nécessite PowerNukkitX et Java 21.

## 🧠 Comment ça marche

### La simulation

Un moteur de prédiction est choisi selon le milieu où se trouve le joueur - sol et air, eau, lave, vol plané -
et il fait avancer un état de mouvement appartenant au serveur dans le vrai ordre de tick de Bedrock. Les
constantes sont celles du jeu, et la trigonométrie passe par une réimplémentation de la table de sinus de
Mojang, pour que l'erreur flottante colle au client au lieu de simplement s'en approcher.

Elle est autoritative, pas contemplative : le paquet entrant est réécrit avec la position simulée avant que le
serveur ne le voie.

### Mesurer un tick, pas une vie entière

La simulation repart de la position annoncée par le client à la fin de chaque tick validé. Sans ça, un seul
tick inexpliqué reste dans la position du serveur pendant des centaines de ticks, et **chacun d'eux est
recompté comme un nouvel échec**. L'écart doit signifier *de combien ce tick-ci a manqué sa cible*, pas *de
combien le serveur a dérivé depuis la connexion*.

### Pourquoi un tampon plutôt qu'un seuil

Un seuil unique n'attrape rien : un cheat qui reste juste en dessous, ou qui ne fraude qu'un tick sur deux, ne
le franchit jamais. On accumule à la place l'excès inexpliqué de chaque tick, et il décroît à chaque tick qui
correspond. Le fly et le speed sont faibles mais *persistants*, ce qu'un tampon voit et un seuil non.

### Essayer plus d'une explication

Le client annonce qu'un saut ou un sprint a commencé, mais le drapeau et le tick auquel il s'applique ne
coïncident pas toujours, et une impulsion serveur est appliquée quand le paquet *arrive*, pas quand le serveur
l'a armée. Un tick ambigu est donc simulé de plusieurs façons, et la branche qui tombe au plus près du client
l'emporte. Un cheat n'y gagne rien : chaque branche reste un mouvement légal, donc le résidu qu'il doit
justifier est inchangé.

### Voir le monde que le client voit

L'état est comparé au monde **tel que le client le connaissait**, pas tel que le serveur le détient. Un
changement de bloc reste en attente jusqu'à ce que le client l'acquitte ; un changement fait par le joueur
lui-même s'applique immédiatement, puisque le client prédit ses propres poses et cassages ; le combat rembobine
les cibles à leur position interpolée côté client au lieu de lancer un rayon là où le serveur les croit
maintenant.

Chaque mutation d'état envoyée par le serveur - capacités, attributs, effets, armure, knockback, mises à jour
de blocs - est conditionnée à un acquittement, si bien que la simulation n'adopte un changement qu'une fois que
le client l'a réellement vu.

## 🔎 Les checks

| Check | Ce qu'il signifie |
| --- | --- |
| `Simulation` | Un mouvement que la simulation physique n'a pas pu expliquer. Alimente le tampon et déclenche les setbacks. |
| `Velocity-A` | Un knockback de mêlée que le joueur n'a pas parcouru, ou bien au-delà. La part manquante lui est rendue en le déplaçant. |
| `Timer` | Plus de frames client que de ticks écoulés, signe d'un client qui fait tourner sa simulation trop vite. |
| `Vehicle-A` | Un mouvement de bateau, wagonnet ou monture qui n'a pas suivi sa propre prédiction. C'est le véhicule qui est renvoyé, pas son passager. |
| `NoFall-A` | Des dégâts de chute qui ne correspondent pas à la chute simulée. |
| `GroundSpoof-A` | Le client annonce une collision verticale avec rien sous lui. |
| `Sprint-A…C` | Un état de sprint que le client ne peut pas légitimement tenir. |
| `Elytra-A…B` | Un vol plané démarré dans des conditions que le client refuse. |
| `KillAura-A` | Cible ou séquence d'attaque invalide. |
| `Autoclicker-A` | Clics sur la dernière seconde au-delà du plafond configuré. Le coup est refusé. |
| `Reach-A` | Cible attaquée au-delà de la portée autorisée, mesurée contre sa hitbox rembobinée. |
| `Hitbox-A` | Le rayon de visée n'a jamais croisé la cible. |
| `BreakReach-A` | Bloc cassé au-delà de la distance autorisée. |
| `PlaceReach-A` | Bloc posé au-delà de cette même distance. |
| `FastBreak-A` | Bloc détruit avant le temps de minage calculé par le serveur. |
| `Scaffold-A` | Vecteur de clic nul sur une pose initiale déclenchée par l'entrée joueur. |
| `Cobweb-A` | Déplacement dans une toile plus rapide que son propre ralentissement ne l'autorise. |
| `BadSlot-A` | Potion ou perle de l'Ender utilisée depuis un emplacement hors de la barre d'action. La transaction est refusée. |
| `FastUse-A` | Un consommable terminé en moins de ticks qu'aucun aliment ou potion n'en demande. |
| `InvMove-A` | Déplacement dirigé pendant une interaction d'inventaire. |
| `BedrockTool-A` | L'identité du client correspond à un outil connu. |
| `ChestStealer-A` | Objets déplacés hors d'un conteneur plus vite qu'un humain ne peut cliquer. |
| `AutoTotem-A` | Échange de totem dans la main secondaire quelques millisecondes après que le précédent ait pop. |
| `BadPacket-A…Q` | Champs de paquet malformés ou impossibles : valeurs, états et identifiants que le protocole ne peut pas produire. Un bloc posé sans le regarder est également refusé ici. |

Les paquets invalides sont annulés. Des violations de mouvement répétées provoquent un setback vers la dernière
position vérifiée **au sol** ; un joueur qui n'en a pas encore atteint une reçoit seulement des alertes. `Timer`
après une série soutenue de violations, `BedrockTool-A` dès la détection et deux variantes de `BadPacket`
expulsent ; rien d'autre ne le fait, et Amethyst ne bannit jamais.

Les situations que le jeu lui-même rend imprévisibles - pistons, riptide, un joueur poussé dans un bloc,
l'instant qui suit un téléport - suspendent la vérification du mouvement au lieu de la deviner. Elles sont
traitées de façon conservatrice, pour qu'un joueur légitime n'en soit jamais puni.

## 🔌 Pour les développeurs

`PlayerViolationEvent` est déclenché à chaque flag, avant l'envoi de l'alerte. Il porte le joueur, le check, le
niveau de violation et la même chaîne de détail que l'alerte. L'annuler supprime l'alerte : c'est ainsi qu'un
autre plugin exempte un cas qu'Amethyst ne peut pas connaître.

## ⚙️ Configuration

| Réglage | Rôle |
| --- | --- |
| `alerts` | Active les alertes de violation. |
| `dev-logs` | Ajoute les valeurs de diagnostic à chaque alerte. Désactivé par défaut ; nécessaire pour signaler un faux positif. |
| `disabled-checks` | Identifiants de checks à désactiver entièrement, écrits comme dans les alertes. |
| `updates.check` | Vérifie l'existence d'une version plus récente au démarrage. |
| `setback-violations` | Violations nécessaires avant un setback de mouvement. |
| `max-packet-actions` | Nombre maximal d'actions de bloc acceptées dans un paquet d'entrée. |
| `prediction.tolerance` | Écart, en blocs, ignoré sur un tick isolé. |
| `prediction.buffer-threshold` | Excès d'écart accumulé nécessaire avant de flagger. |
| `prediction.buffer-decay` | Retiré du tampon à chaque tick qui correspond. |
| `vehicle.tolerance` | Erreur de modèle acceptée pour un véhicule. |
| `vehicle.buffer-threshold` | Erreur de véhicule accumulée nécessaire pour une violation. |
| `vehicle.buffer-decay` | Tampon de véhicule retiré après un mouvement valide. |
| `combat.bbox-expansion` | Agrandissement appliqué aux hitbox rembobinées. |
| `combat.reach-leniency` | Tolérance supplémentaire appliquée au seul rayon prédit. |
| `combat.interpolation-steps` | Échantillons de tick partiel utilisés pour le rembobinage. |
| `combat.maximum-attack-angle` | Angle maximal accepté vers la hitbox de la cible. |
| `combat.close-range-fallback` | Repli courte portée accepté sans lancer de rayon. |
| `combat.close-range-angle` | Angle maximal autorisé par ce repli. |
| `combat.cps-limit` | Clics par seconde acceptés au clavier ou à la manette. |
| `combat.touch-cps-limit` | Clics par seconde acceptés en tactile. |
| `blocks.max-reach` | Distance maximale acceptée pour une interaction avec un bloc. |
| `blocks.break-leniency-ms` | Marge réseau sur le temps de minage calculé par le serveur. |
| `inventory-move.input-threshold` | Entrée directionnelle minimale, dérive de manette exclue. |
| `inventory-move.request-window-ms` | Délai maximal entre une action d'objet et la confirmation du mouvement. |
| `inventory-move.buffer-threshold` | Actions d'inventaire suspectes consécutives nécessaires. |

`prediction.tolerance` et `prediction.buffer-threshold` sont les deux qui méritent d'être ajustés.

## 🧭 Commandes et permissions

`/amethyst status` indique le nombre de joueurs suivis et l'état des alertes, `/amethyst reload` recharge
`config.yml`, et `/amethyst alerts` bascule les alertes globalement jusqu'au prochain rechargement.

| Permission | Par défaut | Description |
| --- | --- | --- |
| `amethyst.alerts` | OP | Reçoit les alertes et autorise `/amethyst`. |
| `amethyst.bypass` | Personne | Ignore tous les checks. |

## 🐛 Signaler un faux positif

Passe d'abord `dev-logs` à `true` : sans lui une alerte ne donne que le nom du check, ce qui est insignalable.
La ligne détaillée porte l'écart mesuré, les deux positions et l'état dans lequel se trouvait la simulation.

Ouvre une issue avec la ligne non modifiée, ce que tu étais en train de faire, la version du client et le mode
d'entrée, le ping approximatif, et tout élément inhabituel à proximité - véhicule, effets, équipement, blocs.
**Dis ce que tu faisais** : ça vaut plus que dix lignes de log sans cette précision.

Évite d'élargir un seuil pour faire disparaître un signalement. Un faux positif est un bug du modèle, et le
correctif appartient au modèle.

## 🔨 Compilation

Place `powernukkitx.jar` dans le dossier parent, puis :

```
./gradlew clean build
```

Le plugin arrive dans `build/libs/`.

## Auteur

Nay
