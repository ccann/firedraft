(ns firedraft.events.game
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [crypto.random :as random]
            [firedraft.cards :as cards]
            [firedraft.game.util
             :as
             g
             :refer
             [get-next-player in-progress? increment-turn joinable? whose-turn]]
            [firedraft.routes.ws :as ws]
            [medley.core :refer [find-first]]
            [mount.core :refer [defstate]]))

{:name "Our Fun Draft"
 :id "8njcTLq0ZVnebaPtXrXhpXG1+bEnCyK8vtY8wt+EAuk="
 :max-players 2
 :joinable? false
 :mode "winston"
 :opts {:booster ["IKO" "IKO" "IKO"
                  "IKO" "IKO" "IKO"]
        :cube "abcdefg"}
 :turn 0
 :players #{"uid1" "uid2"}
 :picks {"uid1" []
         "uid2" []}}

(defstate *games
  :start (atom {}))

(defn available-games []
  (->> (vals @*games)
       (remove (comp empty? :players))
       (map #(assoc % :joinable? (joinable? %)))
       (map #(select-keys % [:id :mode :name :joinable?]))))

(defn export-picks
  [{:keys [game-id player-id]}]
  (->> (get-in @*games [game-id :picks player-id])
       (map #(cards/get-card (:name %)))
       (map (juxt :name :set :number))
       (group-by first)
       (map (fn [[-name cards]]
              (let [[_ -set number] (first cards)]
                (format "%s %s (%s) %s" (count cards) -name -set number))))
       (str/join "\n")))

(defn new-id []
  (random/hex 16))

(defn broadcast-available-games! []
  (doseq [uid (:any @ws/connected-uids)]
    (ws/send! uid [:games/available (available-games)])))

(defn create-game
  [{:keys [event ?data uid ?reply-fn]}]
  (log/info "handle" (first event) uid)
  (when ?reply-fn
    (let [id (new-id)
          game (assoc ?data
                      :max-players 2
                      :name (g/random-name)
                      :id id
                      :players [uid])]
      (swap! *games assoc id game)
      (log/info "create game:" game)
      (broadcast-available-games!)
      (?reply-fn (assoc game :player uid)))))

(defn find-next-pickable-stack
  ([game]
   (find-next-pickable-stack game nil))
  ([game min]
   (let [min (or min -1)]
     (or (some->> (find-first (fn [[i cs]]
                                (when (and (seq (remove nil? cs))
                                           (< min i))
                                  i))
                              (map vector (range) (:piles game)))
                  (first)
                  (vector :pile))
         (and (seq (:deck game))
              [:deck 0])))))

(defn- inc-client-game-state!
  [game upcoming-player & [pile-ix]]
  (if-let [pickable (find-next-pickable-stack game pile-ix)]
    (do
      (log/info :pickable pickable)
      (doseq [uid (:players game)]
        (log/info :send uid :game/step)
        (ws/send! uid [:game/step
                       {:piles-count (->> (:piles game)
                                          (remove nil?)
                                          (mapv count))
                        :deck-count (count (:deck game))
                        :turn-number (:turn-number game)
                        :my-turn? (= uid upcoming-player)
                        :pickable pickable}]))
      ;; send the card data for the card that is about to be revealed by oppo
      ;; if they're choosing from the deck, send the top card
      ;; if they're choosing from the pile, send those cards in pile
      (log/info :send upcoming-player :game/cards)
      (ws/send! upcoming-player
                [:game/cards
                 (case (first pickable)
                   :deck {:cards [(first (:deck game))]}
                   :pile {:cards (get-in game [:piles (second pickable)])})]))
    ;; TODO: if `pickable` is nil, the game is over
    (doseq [uid (:players game)]
        (log/info :send uid :game/end)
        (ws/send! uid [:game/end (export-picks {:game-id (:id game)
                                                :player-id uid})]))))

(defn- join-game-in-progress!
  [game uid reply-fn]
  (log/info :join-game-in-progress uid)
  (if-let [ix (.indexOf (:players game) nil)]
    (let [picks (get-in game [:picks nil])
          other-players (remove nil? (:players game))
          game (-> game
                   ;; add the new player
                   (update :players assoc ix uid)
                   ;; give the new player any picks made by a dropped player
                   (assoc-in [:picks uid] picks)
                   (update :picks dissoc nil))]
      (reply-fn (-> game
                    (select-keys [:mode :opts :max-players
                                  :name :id :players
                                  ;; todo
                                  :sideboard])
                    (assoc :picks picks)))
      (doseq [uid other-players]
        (ws/send! uid [:game/update-players (:players game)]))
      (swap! *games update (:id game)
             #(assoc %
                     :players (:players game)
                     :picks (:picks game)))
      (inc-client-game-state! game (whose-turn game) (:passed-ix game)))
    (do (log/error :unjoinable-game-in-progress
                   {:id (:id game)
                    :new-player uid
                    :players (:players game)})
        (reply-fn {:error :failed-to-join}))))

(defn- join-new-game!
  [game uid reply-fn]
  (let [game (update game :players conj uid)
        players (:players game)]
    (log/info :join-game {:player uid})
    (swap! *games assoc-in [(:id game) :players] players)
    (doseq [uid players]
      (ws/send! uid [:game/joined game]))
    (broadcast-available-games!)
    ;; :mode, :opts, :max-players, :name, :id, :players
    (reply-fn game)))

(defn join-game
  [{:keys [uid ?data ?reply-fn]}]
  (log/info :game/join ?data)
  (let [id ?data]
    (if-let [game (get @*games id)]
      (if (in-progress? game)
        (join-game-in-progress! game uid ?reply-fn)
        (join-new-game! game uid ?reply-fn))
      (do (log/error :game-404 id)
          (?reply-fn {:error :not-found})))))

(defn- add-piles
  [game]
  (let [deck (:deck game)
        [c1 c2 c3 & deck] deck]
    (assoc game
           :piles [[c1] [c2] [c3]]
           :deck (vec deck))))

(defn add-picks
  [game player-id picks]
  (update-in game [:picks player-id]
             #(if (nil? %) picks (into % picks))))

(defn get-picks
  [game player-id]
  (get-in game [:picks player-id]))

(defmulti pick!
  "Update the game state with this pick:
   * add the picked cards to the player's picks
   * if the pick was from the deck, remove that card from the top
   * if the pick was from a pile, take the top of deck and put it in that pile
   * increment the turn"
  :pick-type)

(defmethod pick! :deck
  [{:keys [game-id]}]
  (let [game (get @*games game-id)
        player-id (whose-turn game)
        [pick & new-deck] (:deck game)]
    (swap! *games update game-id
           (fn [g] (-> g
                       (add-picks player-id [pick])
                       (assoc :deck new-deck)
                       (increment-turn))))))

(defmethod pick! :pile
  [{:keys [pile-ix game-id]}]
  (let [game (get @*games game-id)
        player-id (whose-turn game)
        picks (vec (get-in game [:piles pile-ix]))
        [top-deck & new-deck] (:deck game)]
    (swap! *games update game-id
           (fn [g] (-> g
                       (add-picks player-id picks)
                       ;; reset the picked pile to the top card from the deck
                       (assoc-in [:piles pile-ix] (if top-deck [top-deck] []))
                       (assoc :deck new-deck)
                       (increment-turn))))))

(defn- pick-cards
  [{:keys [?data ?reply-fn]}]
  (let [{:keys [picked game-id]} ?data
        _ (log/info :picked picked)
        [pick-type pile-ix] picked
        game (get @*games game-id)
        [player oppo] ((juxt whose-turn get-next-player) game)]
    ;; update the game state with this pick
    (pick! {:pick-type pick-type
            :pile-ix pile-ix
            :game-id game-id})
    (let [game (get @*games game-id)
          picks (get-picks game player)]
      (?reply-fn {:picks picks})
      (inc-client-game-state! game oppo))))

(defn- pass-cards
  [{:keys [?data]}]
  (let [{:keys [pile-ix game-id]} ?data
        game (get @*games game-id)
        player (whose-turn game)
        [top-deck & new-deck] (:deck game)]
    (swap! *games update game-id
           (fn [g] (cond-> g
                     ;; add the top of the deck to the passed pile
                     top-deck (update-in [:piles pile-ix] conj top-deck)
                     true (assoc :passed-ix pile-ix)
                     ;; reset the deck
                     true (assoc :deck new-deck))))
    (let [game (get @*games game-id)]
      (inc-client-game-state! game player pile-ix))))

(defn start-game
  [{:keys [event ?data]}]
  (log/info event)
  (let [id ?data]
    (if-let [game (get @*games id)]
      (let [game (-> (cards/add-deck game)
                     (add-piles)
                     (increment-turn))]
        (swap! *games assoc id game)
        (inc-client-game-state! game (whose-turn game)))
      (log/error :game-404 id))))

(defstate event-handlers
  :start
  (do (defmethod ws/handle-event :game/create [msg] (create-game msg))
      (defmethod ws/handle-event :game/join [msg] (join-game msg))
      (defmethod ws/handle-event :game/start [msg] (start-game msg))
      (defmethod ws/handle-event :game/pick [msg] (pick-cards msg))
      (defmethod ws/handle-event :game/pass [msg] (pass-cards msg))))
