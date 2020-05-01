(ns firedraft.common.state
  (:require [reagent.core :as r]
            [taoensso.timbre :as log]
            [firedraft.game.util :as g]
            [lambdaisland.deep-diff2 :as ddiff]))

(defonce session
  (r/atom {:page :lobby
           :game g/default-game-config}))

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
