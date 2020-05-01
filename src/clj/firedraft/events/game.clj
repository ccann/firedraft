(ns firedraft.events.game
  (:require [clojure.tools.logging :as log]
            [crypto.random :as random]
            [firedraft.cards :as cards]
            [firedraft.routes.ws :as ws]
            [firedraft.game.util :refer [whose-turn]]
            [medley.core :refer [find-first]]
            [mount.core :refer [defstate]]))

;; {:title "Our Fun Game"
;;  :id "8njcTLq0ZVnebaPtXrXhpXG1+bEnCyK8vtY8wt+EAuk="
;;  :mode "winston"
;;  :opts {:booster ["IKO" "IKO" "IKO"
;;                   "IKO" "IKO" "IKO"]
;;         :cube "abcdefg"}
;;  :players ["uid1" "uid2"]}

(def game-modes
  #{"winston"})

(defstate *games
  :start (atom {}))

(defn new-id []
  (random/hex 16))

(defn join-game
  [{:keys [event uid ?data ?reply-fn]}]
  (log/info (first event) (:id (second event)))
  (let [id (:id ?data)]
    (if-let [game (get @*games id)]
      (let [game (update game :players conj uid)
            players (:players game)]
        (log/info "join game:" uid)
        (swap! *games assoc-in [id :players] players)
        (doseq [uid players]
          (ws/send! uid [:game/joined game]))
        (?reply-fn (assoc game :player uid)))
      (do (log/error :game-404 id)
          (?reply-fn {:error :not-found})))))

(defn create-game
  [{:keys [event ?data uid ?reply-fn]}]
  (log/info "handle" (first event) uid)
  (when ?reply-fn
    (let [id (new-id)
          game (assoc ?data
                      :id id
                      :players [uid])]
      (swap! *games assoc id game)
      (log/info "create game:" game)
      (?reply-fn (assoc game :player uid)))))

(defn- toggle-turn
  [game]
  (assoc game :turn
         (if-let [turn (:turn game)]
           (if (= 0 turn) 1 0)
           (rand-int 2))))

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
   * toggle the turn"
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
                       (toggle-turn))))))

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
                       (assoc-in [:piles pile-ix] [top-deck])
                       (assoc :deck new-deck)
                       (toggle-turn))))))

(defn find-next-pickable-stack
  ([game]
   (find-next-pickable-stack game nil))
  ([game min]
   (let [min (or min -1)]
     (or (some->> (find-first (fn [[i cs]]
                                (when (and (seq cs) (< min i))
                                  i))
                              (map vector (range) (:piles game)))
                  (first)
                  (vector :pile))
         (and (seq (:deck game))
              [:deck 0])))))

(defn get-oppo
  [game]
  (find-first #(not= (whose-turn game) %) (:players game)))

(defn- inc-client-game-state!
  [game upcoming-player & [pile-ix]]
  ;; TODO: if `pickable` is nil, the game is over
  (let [pickable (find-next-pickable-stack game pile-ix)]
    (log/info :pickable pickable)
    (doseq [uid (:players game)]
      (log/info :send uid :game/step)
      (ws/send! uid [:game/step
                     {:piles-count (mapv count (:piles game))
                      :deck-count (count (:deck game))
                      :turn (:turn game)
                      :pickable pickable}]))
    ;; send the card data for the card that is about to be revealed by oppo
    ;; if they're choosing from the deck, send the top card
    ;; if they're choosing from the pile, send those cards in pile
    (log/info :send upcoming-player :game/cards)
    (ws/send! upcoming-player
              [:game/cards
               (case (first pickable)
                 :deck {:cards [(first (:deck game))]}
                 :pile {:cards (get-in game [:piles (second pickable)])})])))

(defn- pick-cards
  [{:keys [?data ?reply-fn]}]
  (let [{:keys [picked game-id]} ?data
        [pick-type pile-ix] picked
        game (get @*games game-id)
        [player oppo] ((juxt whose-turn get-oppo) game)]
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
           (fn [g] (-> g
                       ;; add the top of the deck to the passed pile
                       (update-in [:piles pile-ix] conj top-deck)
                       ;; reset the deck
                       (assoc :deck new-deck))))
    (let [game (get @*games game-id)]
      (inc-client-game-state! game player pile-ix))))

(defn start-game
  [{:keys [event ?data]}]
  (log/info event)
  (let [id ?data]
    (if-let [game (get @*games id)]
      (let [game (-> (cards/add-deck game)
                     (cards/shuffle-deck)
                     (add-piles)
                     (toggle-turn))]
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
