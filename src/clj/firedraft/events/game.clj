(ns firedraft.events.game
  (:require [clojure.tools.logging :as log]
            [crypto.random :as random]
            [firedraft.cards :as cards]
            [firedraft.routes.ws :as ws]
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
        (?reply-fn game))
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
      (?reply-fn game))))

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

(defn- whose-turn
  [game]
  (get-in game [:players (:turn game)]))

;; - add the pile cards to the player's picks
;; - draw one from the deck to reset the pile
;; - toggle turn
;; - reply with updated player's picks and which pile is now pickable
;; - send to all players `:game/step` to update facedown cards in UI
;; - if it's the last pickable pile, send `:game/turn` to toggle the turn
(defn- pick-cards
  [{:keys [?data ?reply-fn]}]
  (let [{:keys [picked game-id]} ?data
        [pick-type pile-ix] picked
        game (get @*games game-id)
        player-id (whose-turn game)
        picks (case pick-type
                :deck [(:top-deck game)]
                :pile (vec (get-in game [:piles pile-ix])))
        [card & deck] (:deck game)
        pickable (or (some->> (find-first (fn [[i cs]]
                                            (when (seq cs) i))
                                          (map vector (range) (:piles game)))
                              (first)
                              (vector :pile))
                     [:deck 0])]
    (def cody pickable)
    (swap! *games update game-id
           (fn [g] (-> g
                       ;; add the picks the player made
                       (update-in [:picks player-id]
                                  #(if (nil? %) picks (into % picks)))
                       ;; reset the deck with 1 fewer cards
                       (assoc :deck deck)
                       ;; add the drawn card to the pile
                       (assoc-in [:piles pile-ix] [card])
                       (toggle-turn))))
    (?reply-fn {:picks picks})
    (let [game (get @*games game-id)
          oppo (find-first #(not= player-id %) (:players game))]
      (doseq [uid (:players game)]
        (log/info :send uid :game/turn)
        (ws/send! uid [:game/turn
                       {:piles-count (mapv count (:piles game))
                        :deck-count (count (:deck game))
                        :turn (:turn game)
                        :pickable pickable}]))
      ;; send the card data for the card that is about to be revealed by oppo
      ;; if they're choosing from the deck, send the top card
      ;; if they're choosing from the pile, send those cards in pile
      (let [card (first (:deck game))]
        (ws/send! oppo
                  [:game/cards
                   (case (first pickable)
                     :deck {:cards [card]}
                     :pile {:cards (get-in game [:piles (second pickable)])})])))))

(defn- pass-cards
  [{:keys [?data ?reply-fn]}]
  nil)

(defn start-game
  [{:keys [event ?data ?reply-fn]}]
  (log/info event)
  (let [id ?data]
    (if-let [game (get @*games id)]
      (let [game (-> (cards/add-deck game)
                     (cards/shuffle-deck)
                     (add-piles)
                     (toggle-turn))]
        (swap! *games assoc id game)
        (ws/send! (whose-turn game)
                  [:game/cards {:cards (get-in game [:piles 0])}])
        (doseq [uid (:players game)]
          (log/info :send uid :game/turn)
          (ws/send! uid [:game/turn
                         {:piles-count [1 1 1]
                          :deck-count (count (:deck game))
                          :turn (:turn game)
                          :pickable [:pile 0]}])))
      (log/error :game-404 id))))

(defstate event-handlers
  :start
  (do (defmethod ws/handle-event :game/create [msg] (create-game msg))
      (defmethod ws/handle-event :game/join [msg] (join-game msg))
      (defmethod ws/handle-event :game/start [msg] (start-game msg))
      (defmethod ws/handle-event :game/pick [msg] (pick-cards msg))
      (defmethod ws/handle-event :game/pass [msg] (pass-cards msg))))
