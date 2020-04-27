(ns firedraft.core
  (:require [ajax.core :refer [GET]]
            [clojure.string :as string]
            [firedraft.ajax :as ajax]
            [firedraft.lobby :as lobby]
            [firedraft.room :as room]
            [firedraft.ws :as ws]
            [goog.events :as events]
            [goog.history.EventType :as HistoryEventType]
            [reagent.core :as r]
            [reagent.dom :as dom]
            [reitit.core :as reitit])
  (:import goog.History))

;; (defn message-list [messages]
;;   [:ul.messages
;;    (for [{:keys [timestamp message name]} messages]
;;      ^{:key timestamp}
;;      [:li
;;       [:time (.toLocaleString timestamp)]
;;       [:p message]
;;       [:p " - " name]])])

;; (defn errors-component [errors id]
;;   (when-let [error (id @errors)]
;;     [:div.notification.is-danger (clojure.string/join error)]))

;; (defn message-form [fields errors]
;;   [:div.content
;;    [:h3 "Messages"]
;;    [:p "Name:"
;;     [:input.input
;;      {:type      :text
;;       :on-change #(swap! fields assoc :name (-> % .-target .-value))
;;       :value     (:name @fields)}]]
;;    [errors-component errors :name]

;;    [:p "Message:"
;;     [:textarea.textarea
;;      {:rows      4
;;       :cols      50
;;       :value     (:message @fields)
;;       :on-change #(swap! fields assoc :message (-> % .-target .-value))}]]
;;    [errors-component errors :message]

;;    [:input.button.is-primary
;;     {:type     :submit
;;      :on-click #(ws/send-message! [:firedraft/add-message @fields] 8000)
;;      :value    "comment"}]])


(defonce session
  (r/atom {:page :lobby
           :room lobby/default-config}))

(add-watch session :session
           (fn [_ _ _ new]
             (js/console.log "session updated:" (pr-str new))))

(def pages
  {:lobby lobby/page
   :room room/page})

(defn page []
  ((pages (:page @session)) session))

(defmethod ws/handle-event :chsk/recv
  [{[_ message] :?data}]
  (r/with-let [errors (r/cursor session [:errors])
               messages (r/cursor session [:messages])]
    (if-let [response-errors (:errors message)]
      (reset! errors response-errors)
      (do
        (reset! errors nil)
        (swap! messages conj message)))))


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
        (swap! session assoc :page (match-route (.-token event)))))
    (.setEnabled true)))

(defn mount-components []
  (dom/render [#'page] (.getElementById js/document "app")))

(defn init! []
  (ws/start-router!)
  (ajax/load-interceptors!)
  (mount-components))
