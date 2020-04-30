(ns firedraft.lobby
  (:require [clojure.string :as str]
            [firedraft.common :as com]
            [firedraft.ws :as ws]
            [reagent.core :as r]
            [ajax.core :as ajax]
            [taoensso.timbre :as log]))

(defn pack-controls
  [session]
  (for [n (range 6)]
    ^{:key n}
    [:div.control
     [:div.select
      [:select
       [:option "IKO"]]]]))


(def game-opt-defaults
  {"winston" {:booster ["IKO" "IKO" "IKO"
                        "IKO" "IKO" "IKO"]
              :set-singleton nil}
   "grid" {}})

(defn- ->keyword
  [s]
  (keyword (str/replace s #"\s+" "-")))

(defn- set-game-type!
  [this session]
  (swap! session assoc-in [:game :opts]
         (let [mode (get-in @session [:game :mode])
               v (-> this com/target-value ->keyword)]
           {v (get-in game-opt-defaults [mode v])})))

(defn- create-game!
  [session]
  (ws/send! [:game/create (:game @session)]
            5000
            (fn callback [data]
              (log/info :game-created (pr-str data))
              (swap! session assoc :game data)
              (com/nav! session :game))))

(defn- set-game-mode!
  [this session]
  (swap! session assoc-in [:game :mode]
         (com/target-value this)))

(defn section-create-game
  [session]
  (let [winston? (= "winston" (get-in @session [:game :mode]))]
    [:div.section
     [:div.container
      (com/header)
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
          [:option "winston"]
          [:option "grid"]]]]]

      (when winston?
        [:div.field
         [:div.field
          [:label.label "Type"]
          [:div.control
           {:on-change #(set-game-type! % session)}
           [:div.select
            [:select
             [:option "booster"]
             [:option "set singleton"]]]]]
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
  (let [game-id (com/elem-val "game-id-input")
        payload (-> (:game @session)
                    (assoc :id game-id))]
    (log/info "join game:" game-id)
    (ws/send! [:game/join payload]
              2000
              (fn callback [data]
                (if (:error data)
                  (log/error [:game/join (:error data)])
                  (do (swap! session assoc :game data)
                      (com/nav! session :game)))))))

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
      (swap! com/session assoc-in [:game :players] players))))
