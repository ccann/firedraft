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
    [:div.section.main_content
     [:div.container
      [:div.content
       [:h3 "Create A Draft"]]
      [:div.field
       [:label.label "Mode"]
       [:div.control
        {:on-change #(set-game-mode! % session)}
        [:div.select
         [:select
          (for [mode g/modes] ^{:key mode} [:option mode])]]]]

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
        [:button.button.is-primary
         {:on-click #(create-game! session)}
         "Create Draft"]]]]]))

(defn- join-game!
  [session game-id]
  (log/info "join game:" game-id)
  (ws/send! [:game/join game-id]
            2000
            (fn callback [data]
              (if (:error data)
                (log/error [:game/join (:error data)])
                (do (swap! session assoc :game data)
                    (page/nav! session :game))))))

(defn section-join-game
  [session]
  [:div.section.main_content
   [:div.container
    [:div.content
     [:h3 "Drafts"]
     (if (seq (:games @session))
       [:table.table.is-bordered.is-striped.is-hoverable.is-narrow
        [:thead
         [:tr
          [:th "Status"]
          [:th "Name"]
          [:th "Mode"]
          [:th "Code"]]]
        [:tbody
         (doall
          (for [game (->> (:games @session)
                          (sort-by :joinable?))]
            ^{:key game}
            [:tr
             [:td (if (:joinable? game)
                    [:div.field
                     [:div.control
                      [:a {:on-click #(join-game! session (:id game))}
                       "Join Draft"]]]
                    "Full")]
             [:td (:name game)]
             [:td (:mode game)]
             [:td {:id "game-id-input"}
              [:span.is-family-code (:id game)]]]))]]
       [:p "No drafts avaiable. How about creating one?"])]]])

(defn page [session]
  [:div.main
   dom/header
   (section-create-game session)
   (section-join-game session)
   dom/footer])

(defmethod ws/handle-message :game/joined
  [{:keys [message]}]
  (log/info :handle :game/joined)
  (let [players (:players message)]
    (log/info "set players:" (pr-str players))
    (swap! state/session assoc-in [:game :players] players)))

(defmethod ws/handle-message :games/available
  [{:keys [message]}]
  (log/info :handle :games/available)
  (swap! state/session assoc :games message))
