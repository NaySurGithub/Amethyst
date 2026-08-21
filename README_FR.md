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

`MovementSimulator` choisit le moteur correspondant au milieu où se trouve le joueur - sol et air, eau, lave,
vol plané - et lui confie le tick entier. Chaque moteur fait avancer un état de mouvement appartenant au
serveur dans le vrai ordre de tick de Bedrock pour ce milieu : au sol, friction du bloc sous les pieds,
accélération d'entrée, saut, blocs escaladables, toiles, collision, puis gravité ; dans l'eau, traînée du
fluide, Pas de l'abysse et une gravité au seizième de sa valeur. Les constantes sont celles du jeu - friction
de l'air `0.91`, multiplicateur de gravité `0.98`, hauteur de pas `0.5625` - et la trigonométrie passe par une
réimplémentation de la table de sinus à 65536 entrées de Mojang, pour que l'erreur flottante colle au client
au lieu de simplement s'en approcher.

Elle est autoritative, pas contemplative : le paquet entrant est réécrit avec la position simulée avant que le
serveur ne le voie.

### Mesurer un tick, pas une vie entière

La simulation repart de la position annoncée par le client à la fin de chaque tick vérifié. Sans ça, un seul
tick inexpliqué - un paquet perdu, une impulsion arrivée en retard - reste inscrit dans la position du serveur
pendant des centaines de ticks, et **chacun d'eux est recompté comme une faute neuve**. L'offset doit vouloir
dire *de combien ce tick-ci s'est trompé*, pas *de combien le serveur a dérivé depuis la connexion*.

La conséquence, c'est que la position du serveur n'est jamais à plus d'un tick de là où le joueur prétend être,
et qu'elle ne peut donc pas servir de cible à une correction. C'est une position vérifiée séparée, enregistrée
uniquement au sol et avec un tampon de violations vide, que vise un setback.

### Pourquoi un tampon plutôt qu'un seuil

Un seuil unique de 0,5 bloc n'attrape rien : un cheat qui reste en dessous, ou qui ne triche qu'un tick sur
deux, ne le déclenche jamais. L'excédent inexpliqué de chaque tick est accumulé à la place, dans un seau qui
fuit à chaque tick. Le vol et la vitesse sont de petites divergences *persistantes*, ce qu'un tampon voit et
qu'un seuil ne voit pas.

### Essayer plus d'une explication

Le client nous dit qu'un saut ou un sprint a commencé, mais le drapeau et le tick auquel il s'applique ne
coïncident pas toujours, et une impulsion serveur est appliquée quand le paquet *arrive*, pas quand le serveur
l'a armée. Ne simuler que la lecture littérale de l'entrée fait rater au serveur un vrai saut de 0,42, qui
ressemble alors à un gros écart sur un mouvement parfaitement légitime.

Un tick ambigu est donc simulé dans les deux sens et la branche la plus proche du client gagne ; une impulsion
que le client n'a pas encore dépensée reste armée pour le tick suivant. Un tricheur n'y gagne rien, puisque
chaque branche reste un mouvement légal - le résidu qu'il doit justifier est inchangé.

### Voir le monde tel que le client le voit

L'état est comparé au monde **tel que le client le connaissait**, pas tel que le serveur le détient :

- Un changement de bloc reste en attente jusqu'à ce que le client l'acquitte, et `resolve()` renvoie l'ancien
  état jusque-là - la première source de faux positifs sur Bedrock.
- Une modification faite par le joueur lui-même est appliquée immédiatement, parce que son client prédit ses
  propres poses et casses sans attendre personne.
- Le combat rembobine les cibles à leurs positions interpolées côté client, en reproduisant la fenêtre
  d'interpolation du client, au lieu de tracer un rayon vers l'endroit où le serveur les croit maintenant.

Chaque changement d'état envoyé par le serveur - capacités, attributs, effets, armure, knockback, mises à jour
de blocs - est conditionné à un acquittement `NetworkStackLatencyPacket`, pour que la simulation n'adopte un
changement qu'une fois que le client l'a réellement vu.

## 🔎 Les checks

| Check | Ce que ça veut dire |
| --- | --- |
| `Simulation` | Un déplacement que la simulation physique n'a pas pu expliquer. Alimente le tampon et pilote les setbacks. |
| `Velocity-A` | Un knockback de mêlée que le joueur n'a pas parcouru, ou largement dépassé. La part manquante lui est rendue en le déplaçant. |
| `Timer` | Plus de frames client que de ticks écoulés, signature d'un client qui accélère sa propre simulation. |
| `Vehicle-A` | Un bateau, un wagonnet ou une monture qui ne correspond pas à sa prédiction. C'est le véhicule qui est renvoyé, pas son passager. |
| `NoFall-A` | Des dégâts de chute qui ne correspondent pas à la chute simulée. |
| `GroundSpoof-A` | Le client annonce une collision verticale avec rien sous lui. Ignoré sur les blocs partiels et sous élytre. |
| `Sprint-A…C` | Un sprint que le client ne peut pas légitimement tenir : trop peu de nourriture, un objet en cours d'usage, ou démarré sous Cécité. |
| `Elytra-A…B` | Un vol plané démarré en véhicule, ou relancé moins de deux ticks après le précédent. |
| `KillAura-A` | Cible ou séquence d'attaque invalide. |
| `Reach-A` | Cible frappée au-delà de la portée, mesurée contre sa boîte rembobinée. |
| `Hitbox-A` | Le rayon de visée n'a jamais croisé la cible. Joueurs uniquement : la boîte rembobinée d'un mob est trop lissée pour un rayon. |
| `BreakReach-A` | Bloc cassé au-delà de la distance autorisée. |
| `PlaceReach-A` | Bloc posé au-delà de cette même distance. |
| `FastBreak-A` | Bloc détruit avant le temps de minage calculé par le serveur. |
| `WeirdPlace-A` | Bloc posé contre quelque chose que le joueur ne regardait pas, ou sans le tenir. La pose est refusée. |
| `Scaffold-A` | Vecteur de clic nul sur une pose initiale déclenchée par l'entrée joueur. |
| `Cobweb-A` | Déplacement dans une toile plus rapide que son propre ralentissement ne l'autorise. |
| `BadSlot-A` | Potion ou perle de l'Ender utilisée depuis un emplacement hors de la barre d'action. La transaction est refusée. |
| `FastUse-A` | Un consommable terminé en moins de ticks qu'aucun aliment ou potion n'en demande. |
| `InvMove-A` | Déplacement dirigé pendant une interaction d'inventaire. |
| `BedrockTool-A` | L'identité du client correspond à un outil connu : modèle figé, version de géométrie vide et skin uniforme. |
| `BadPacket-A…J` | Champs de paquet malformés ou impossibles : valeurs non finies, ticks périmés, slots, faces, canaux et énumérations invalides. |

Les paquets invalides sont annulés. Des violations de mouvement répétées provoquent un setback vers la dernière
position vérifiée **au sol** - jamais dans un fluide ; un joueur qui n'en a pas encore atteint une reçoit
seulement des alertes.
`Timer` au-delà de quinze violations, `BedrockTool-A` dès la détection, ainsi que `BadPacket-D` et
`BadPacket-E`, expulsent ; rien d'autre ne le fait, et Amethyst ne bannit jamais.

## 🔌 Pour les développeurs

`PlayerViolationEvent` est déclenché à chaque flag, avant l'envoi de l'alerte. Il porte le joueur, le check, le
niveau de violation et la même chaîne de détail que l'alerte. L'annuler supprime l'alerte — c'est ainsi qu'un
autre plugin peut exempter un cas qu'Amethyst ne peut pas connaître.

## ⚙️ Configuration

| Réglage | Rôle |
| --- | --- |
| `alerts` | Active les alertes de violation. |
| `dev-logs` | Ajoute l'écart, les deux positions et l'état de la simulation à chaque alerte. Désactivé par défaut ; nécessaire pour signaler un faux positif. |
| `updates.check` | Vérifie au démarrage s'il existe une version plus récente. |
| `setback-violations` | Violations nécessaires avant un setback de mouvement. |
| `max-packet-actions` | Nombre maximum d'actions de bloc acceptées dans un paquet d'entrée. |
| `prediction.tolerance` | Écart, en blocs, ignoré sur un tick isolé. |
| `prediction.buffer-threshold` | Excédent accumulé nécessaire avant de flag. |
| `prediction.buffer-decay` | Retiré du tampon à chaque tick ; fixe donc l'erreur constante tolérée. |
| `vehicle.tolerance` | Erreur acceptée sur le modèle de véhicule. |
| `vehicle.buffer-threshold` | Erreur de véhicule accumulée nécessaire pour une violation. |
| `vehicle.buffer-decay` | Tampon de véhicule retiré après un mouvement valide. |
| `combat.bbox-expansion` | Agrandissement appliqué aux boîtes de cible rembobinées. |
| `combat.reach-leniency` | Tolérance supplémentaire appliquée au seul rayon prédit. |
| `combat.interpolation-steps` | Échantillons de tick partiel pour le rembobinage de l'attaquant et de la cible. |
| `combat.maximum-attack-angle` | Angle maximum accepté vers la boîte de la cible. |
| `combat.close-range-fallback` | Repli à courte portée accepté sans raycast. |
| `combat.close-range-angle` | Angle maximum autorisé par ce repli. |
| `blocks.max-reach` | Distance maximum acceptée pour une interaction de bloc. |
| `blocks.break-leniency-ms` | Tolérance réseau sur le temps de minage calculé par le serveur. |
| `inventory-move.input-threshold` | Entrée directionnelle minimale, dérive de manette exclue. |
| `inventory-move.request-window-ms` | Délai maximum entre une action d'item et la confirmation de mouvement. |
| `inventory-move.buffer-threshold` | Actions d'inventaire suspectes consécutives nécessaires. |

`prediction.tolerance` et `prediction.buffer-threshold` sont les deux qui méritent d'être réglés. Leurs valeurs
par défaut sont un point de départ, pas une mesure.

## 🧭 Commandes et permissions

`/amethyst status` indique le nombre de joueurs suivis et l'état des alertes, `/amethyst reload` recharge
`config.yml`, et `/amethyst alerts` bascule les alertes globalement jusqu'au prochain rechargement.

| Permission | Par défaut | Description |
| --- | --- | --- |
| `amethyst.alerts` | OP | Reçoit les alertes et autorise `/amethyst`. |
| `amethyst.bypass` | Personne | Contourne tous les checks. |

## ⚠️ Là où la simulation renonce

Certaines situations ne sont pas reproduites, et la vérification est suspendue plutôt que devinée. Ce sont de
vrais trous, et ils sont listés parce qu'un tricheur qui les connaît peut s'en servir :

| Situation | Pourquoi |
| --- | --- |
| Nage | C'est là que le modèle de fluide dérive le plus, donc elle est ignorée - mais seulement si de l'eau est réellement présente, si bien que prétendre nager en plein ciel ne rapporte rien. |
| La seconde qui suit la fin de la Lévitation | Le client reprend sa chute seul pendant que le serveur porte encore la vitesse verticale de l'effet. |
| Bambou, scaffolding | Comportements côté client que la simulation ne reproduit pas. |
| Pistons | Un joueur poussé est déplacé par le serveur, pas par son entrée. |
| Riptide | Une poussée d'environ 3 blocs par tick que la simulation ne peut pas produire. |
| Encastré dans un bloc | La façon dont le client s'extrait d'un bloc posé sur lui n'appartient qu'à lui. |
| À moins de 1,5 bloc d'un bateau ou d'un wagonnet | Il flotte et bouge seul, et sa position suivie a un tick de retard. |
| Téléports, respawns, connexions | L'état est reconstruit à destination, avec deux à trois secondes de grâce. |
| Une vitesse de déplacement au-dessus de 0,5 | Le joueur parcourt en un tick plus de terrain que n'en contient l'instantané capturé, donc la simulation marcherait dans des blocs qu'elle n'a jamais vus. |

L'eau, la lave et le vol en élytres sont simulés et non ignorés, chacun par son propre moteur, mais aucun ne
déclenche de setback et leur tolérance vaut huit fois la normale. Le vol plané conserve ce traitement une
seconde après sa fin, l'atterrissage emportant la vitesse du vol dans les premiers ticks au sol. Sous ce plafond de vitesse, la tolérance suit aussi la
vitesse accordée par le serveur : une potion de Célérité élargit la marge d'exactement ce dont elle élargit le
pas.

Les bateaux, wagonnets et shulkers sont de **vraies collisions** et non des trous : leur type est porté jusqu'au
suivi d'entités, donc un joueur se tient dessus comme le fait son client.

Le waterwalk reste détecté malgré l'exemption de la nage, parce que le cheat maintient le joueur *au-dessus* de
la surface, là où aucun fluide ne croise sa boîte et où le moteur terrestre tourne normalement.

## 🚧 Ce qui n'est pas fini

- **Le modèle d'eau est jeune.** Il a été écrit face à une implémentation qui fonctionne et ses constantes sont
  celles du jeu, mais il est né en une seule séance. C'est pour cette raison que la nage est ignorée ; patauger
  ne l'est pas.
- **Les coins d'escalier sont déduits, pas lus.** Le serveur modélise tout escalier comme droit, donc la forme
  est recalculée depuis les voisins comme le fait le jeu. Ce code n'a pas encore rencontré un vrai escalier.
- **`Cobweb-A`, `BadSlot-A`, `Sprint-A…C`, `Elytra-A…B`, `GroundSpoof-A`, `FastUse-A`, `WeirdPlace-A`,
  `PlaceReach-A` et `BedrockTool-A` sont neufs** et n'ont pas subi de passe de faux positifs. Les familles
  `Sprint` et `Elytra` viennent d'un anticheat Java Edition et seul `Sprint-A` est vérifié dans le source du
  serveur ; les autres ne décrivent peut-être pas Bedrock.
- **Aucun test.** La physique est exactement le genre de code qu'une suite de tests figerait, et il n'y en a
  aucune.
- **Aucun banc de rejeu.** Chaque diagnostic demande aujourd'hui un redémarrage de serveur et un humain qui
  reproduit le bug. Enregistrer les entrées et les frames du monde sur disque, puis les rejouer hors ligne,
  ferait passer un cycle de diagnostic de plusieurs minutes à quelques secondes, et donnerait à chaque bug
  corrigé son cas de non-régression.
- **Deux solveurs de collision** coexistent - un pour les véhicules, un pour le joueur.
- **Aucune mesure de performance.** Le chemin chaud est compris et a été allégé, mais jamais profilé.

## 🐛 Signaler un faux positif

Passe d'abord `dev-logs` à `true` : sans lui une alerte ne donne que le nom du check, ce qui est
insignalable. La ligne détaillée porte l'écart, les deux positions, le drapeau de sol, ce que la
simulation croyait avoir sous les pieds du joueur, sa vitesse verticale et le tick :

```
failed Simulation (VL 1.0) offset=0.420 client=[…] predicted=[…] ground=true
  support=minecraft:sand@61 below=minecraft:sand@60/1 vy=-0.078 kb-age=4 tick=334
```

`support` est ce que la simulation croyait avoir sous les pieds du joueur, `below` ce que le frame capturé
contient réellement sous lui et combien de boîtes de collision il en a gardées, et `kb-age` l'ancienneté de la
dernière impulsion parvenue à la simulation. Dans un fluide, la ligne porte en plus `sub`, de combien la
surface dépasse les pieds du joueur, et le mot `fluid` quand le tick est passé par un moteur de fluide.

Lis-la avant de la signaler - la forme des nombres nomme souvent le bug :

| Signature | Veut généralement dire |
| --- | --- |
| Un multiple de 0,216 | Des ticks entiers manquent (0,216 = un tick de marche, 0,28 en sprint) |
| La même valeur qui se répète exactement | Une impulsion appliquée au mauvais tick - et la valeur dit laquelle |
| Une série qui décroît régulièrement | La simulation ne se ré-ancre pas |
| Un écart uniquement en `y` | Une chute, ou un litige sur le sol |
| `client` égal à `predicted` | Un vieux build |

Joins la ligne non modifiée, ce que tu étais en train de faire, la version du client et le mode d'entrée, le
ping approximatif, et tout ce qu'il y avait d'inhabituel autour - véhicule, effets, équipement, blocs. **Dis ce
que tu faisais** : « juste le spear » a valu plus que dix lignes de log sans contexte.

Évite d'élargir un seuil pour faire taire un rapport. Un faux positif est un bug dans le modèle, et c'est dans
le modèle que la correction doit aller.

## 🔨 Compilation

Place `powernukkitx.jar` dans le dossier parent, puis :

```
./gradlew clean build
```

Le plugin se retrouve dans `build/libs/`.

## Auteur

Nay
