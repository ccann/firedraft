(ns firedraft.common
  (:require [reagent.core :as r]
            [taoensso.timbre :as log]))

(defn elem [id] (.getElementById js/document id))

(defn value
  [x]
  (-> x .-value))

(defn target-value
  [x]
  (-> x .-target .-value))

(defn elem-val [id] (-> (.getElementById js/document id)
                        (value)))

(defn nav!
  [session page]
  (swap! session assoc :page page))

(def game-defaults
  {"winston" {:mode "winston"
              :opts {:booster ["IKO" "IKO" "IKO"
                               "IKO" "IKO" "IKO"]}}
   "grid" {:mode "grid"
           :opts {}}})

(def default-game-mode "winston")

(def default-game-config
  (get game-defaults default-game-mode))

(defonce session
  (r/atom {:page :lobby
           :game default-game-config}))

(add-watch session :session
           (fn [_ _ _ new]
             (log/info "session updated:" (pr-str new))))


(def fire-emoji "🔥")

(defn header []
  [:div.content
   [:h1.title  (str fire-emoji " Firedraft")]])
