(ns firedraft.core
  (:require [clojure.string :as string]
            [firedraft.ajax :as ajax]
            [firedraft.lobby :as lobby]
            [firedraft.room :as room]
            [firedraft.ws :as ws]
            [goog.events :as events]
            [goog.history.EventType :as HistoryEventType]
            [reagent.dom :as dom]
            [reitit.core :as reitit]
            [firedraft.common :as com])
  (:import goog.History))

(def pages
  {:lobby lobby/page
   :room room/page})

(defn page []
  ((pages (:page @com/session)) com/session))


;; -------------------------
;; Routes

(def router
  (reitit/router
   [["/" :lobby]
    ["/room" :room]]))

(defn match-route [uri]
  (->> (or (not-empty (string/replace uri #"^.*#" "")) "/")
       (reitit/match-by-path router)
       :data
       :name))
;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
      HistoryEventType/NAVIGATE
      (fn [event]
        (swap! com/session assoc :page (match-route (.-token event)))))
    (.setEnabled true)))

(defn mount-components []
  (dom/render [#'page] (.getElementById js/document "app")))

(defn init! []
  (ws/start-router!)
  (ajax/load-interceptors!)
  (mount-components))
