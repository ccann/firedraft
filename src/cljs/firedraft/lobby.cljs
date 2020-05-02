(ns firedraft.lobby
  (:require [clojure.string :as str]
            [firedraft.common.dom :as dom]
            [firedraft.common.page :as page]
            [firedraft.common.state :as state]
            [firedraft.ws :as ws]
            [taoensso.timbre :as log]
            [firedraft.game.util :as g]))

(defn pack-controls
  [session]
  (for [n (range 6)]
    ^{:key n}
    [:div.control
     [:div.select
      [:select
       {:on-change #(swap! session assoc-in
                           [:game :opts :booster n]
                           (dom/target-value %))}
       (for [set-code g/supported-sets]
         ^{:key set-code}
         [:option set-code])]]]))

(def game-opt-defaults
  (let [sc (first g/supported-sets)]
    {"winston" {:booster [sc sc sc sc sc sc]
                :set-singleton nil}
     "grid" {}}))

(defn- ->keyword
  [s]
  (keyword (str/replace s #"\s+" "-")))

(defn- set-game-type!
  [this session]
  (swap! session assoc-in [:game :opts]
         (let [mode (get-in @session [:game :mode])
               v (-> this dom/target-value ->keyword)]
           {v (get-in game-opt-defaults [mode v])})))

(defn- create-game!
  [session]
  (ws/send! [:game/create (:game @session)]
            5000
            (fn callback [data]
              (log/info :game-created (pr-str data))
              (swap! session assoc :game data)
              (page/nav! session :game))))

(defn- set-game-mode!
  [this session]
  (swap! session assoc-in [:game :mode]
         (dom/target-value this)))

(defn section-create-game
  [session]
  (let [winston? (= "winston" (get-in @session [:game :mode]))]
    [:div.section
     [:div.container
      (dom/header)
      [:div.content
       [:h2 "Lobby"]]
      [:div.content
       [:p.subtitle "Create A Game"]]
      [:div.field
       [:label.label "Mode"]
       [:div.control
        {:on-change #(set-game-mode! % session)}
        [:div.select
         [:select
          [:option "winston"]]]]]

      (when winston?
        [:div.field
         [:div.field
          [:label.label "Type"]
          [:div.control
           {:on-change #(set-game-type! % session)}
           [:div.select
            [:select
             [:option "booster"]]]]]
         (when (contains? (get-in @session [:game :opts]) :booster)
           [:div.field.is-grouped
            (pack-controls session)])])

      [:div.field
       [:div.control
        [:button.button.is-link
         {:on-click #(create-game! session)}
         "Create Game"]]]]]))

(defn- join-game!
  [session]
  (let [game-id (dom/elem-val "game-id-input")
        payload (-> (:game @session)
                    (assoc :id game-id))]
    (log/info "join game:" game-id)
    (ws/send! [:game/join payload]
              2000
              (fn callback [data]
                (if (:error data)
                  (log/error [:game/join (:error data)])
                  (do (swap! session assoc :game data)
                      (page/nav! session :game)))))))

(defn section-join-game
  [session]
  [:div.section
   [:div.container
    [:p.subtitle "Join A Game"]
    [:div.field
     [:div.control
      [:input.input.is-primary
       {:id "game-id-input"
        :type "text"
        :placeholder "Game ID"}]]]
    [:div.field
     [:div.control
      [:button.button.is-link
       {:on-click #(join-game! session)}
       "Join Game"]]]]])

(defn page [session]
  [:div
   (section-create-game session)
   (section-join-game session)])

(defmethod ws/handle-message :game/joined
  [{:keys [message]}]
  (log/info :handle :game/joined)
  (let [players (:players message)]
    (log/info "set players:" (pr-str players))
    (when-let [players (:players message)]
      (swap! state/session assoc-in [:game :players] players))))
