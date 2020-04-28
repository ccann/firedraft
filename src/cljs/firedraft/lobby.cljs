(ns firedraft.lobby
  (:require [clojure.string :as str]
            [firedraft.common :as com]
            [firedraft.ws :as ws]
            [reagent.core :as r]
            [taoensso.timbre :as log]
            [ajax.core :as ajax]))

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

(defn section-create-room
  [session room]
  ;; (ajax/GET "http://localhost:3000/login"
  ;;     {:handler (fn [m]
  ;;                 (js/console.log "login" (pr-str m)))})
  (let [winston? (= "winston" (get-in @room [:game :mode]))]
    [:div.section
     [:h1.title "Firedraft"]
     [:p.subtitle "Lobby"]
     [:div.container
      [:p.subtitle "Create A Room"]
      [:div.field
       [:label.label "Game Mode"]
       [:div.control
        {:on-change #(swap! room assoc-in [:game :mode] (com/target-value %))}
        [:div.select
         [:select
          [:option "winston"]
          [:option "grid"]]]]]

      (when winston?
        [:div.field
         [:div.field
          [:label.label "Game Type"]
          [:div.control
           {:on-change #(swap! room assoc-in [:game :opts]
                               (let [mode (get-in @room [:game :mode])
                                     v (-> % com/target-value ->keyword)]
                                 {v (get-in game-opt-defaults [mode v])}))}
           [:div.select
            [:select
             [:option "booster"]
             [:option "set singleton"]]]]]
         (when (contains? (get-in @room [:game :opts]) :booster)
           [:div.field.is-grouped
            (pack-controls session)])])
      [:div.field
       [:div.control
        [:button.button.is-link
         {:on-click #(ws/send! [:room/create @room]
                               5000
                               (fn [data]
                                 (js/console.log "replied:" (pr-str data))
                                 (reset! room data)
                                 (swap! session assoc :page :room)))}
         "Create Room"]]]]]))

(defn section-join-room
  [session room]
  [:div.section
   [:div.container
    [:p.subtitle "Join A Room"]
    [:div.field
     [:div.control
      [:input.input.is-primary
       {:id "room-id-input"
        :type "text"
        :placeholder "Room ID"}]]]
    [:div.field
     [:div.control
      [:button.button.is-link
       {:on-click
        #(ws/send! [:room/join
                    (assoc @room :id (com/elem-val "room-id-input"))]
                   5000
                   (fn [data]
                     (if (:error data)
                       (log/error [:room/join (:error data)])
                       (do (reset! room data)
                           (swap! session assoc :page :room)))))}
       "Join Room"]]]]])

(defn page [session]
  (r/with-let [room (r/cursor session [:room])]
    [:div
     (section-create-room session room)
     (section-join-room session room)]))


(defmethod ws/handle-message :room/joined
  [{:keys [message]}]
  (let [players (:players message)]
    (js/console.log "set players:" (pr-str players))
    (when-let [players (:players message)]
      (swap! com/session assoc-in [:room :players] players))))
