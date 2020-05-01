(ns firedraft.common
  (:require [reagent.core :as r]
            [taoensso.timbre :as log]
            [lambdaisland.deep-diff2 :as ddiff]))

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

(def printer
  (ddiff/printer {:print-color nil}))

(defn diff-games
  [g1 g2]
  (with-out-str
    (ddiff/pretty-print (ddiff/diff g1 g2) printer)))

(add-watch session :session
           (fn [_ _ old new]
             (let [g1 (:game old)
                   g2 (:game new)]
               (when-not (= g1 g2)
                 (log/info :session/game "\n"
                           (diff-games g1 g2))))))

(def fire-emoji "🔥")

(defn header []
  [:div.content
   [:h1.title  (str fire-emoji " Firedraft")]])
