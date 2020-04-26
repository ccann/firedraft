(ns firedraft.channel-socket
  (:require-macros
   [cljs.core.async.macros :refer (go go-loop)])
  (:require
   [cljs.core.async :refer (<! >! put! chan)]
   [taoensso.encore :as encore :refer [have]]
   [taoensso.sente :as sente :refer (cb-success?)]))


;;;; Util for logging output to on-screen console

(println "ClojureScript appears to have loaded correctly.")

;; (def ?csrf-token
;;   (when-let [el (.getElementById js/document "sente-csrf-token")]
;;     (.getAttribute el "data-csrf-token")))

;; (if ?csrf-token
;;   (println "CSRF token detected in HTML, great!")
;;   (println "CSRF token NOT detected in HTML, default Sente config will reject requests"))

(let [{:keys [chsk ch-recv send-fn state]}
      ;; assigns a client ID to this browser tab
      (sente/make-channel-socket! "/chsk"
                                  ;; ?csrf-token
                                  {:type :auto})]
  (def chsk chsk)
  ;; receive channel
  (def ch-chsk ch-recv)
  ;;send function
  (def chsk-send! send-fn)
  ;; Watchable, read-only atom
  (def chsk-state state))

;;;; Sente event handlers

(defmulti -event-msg-handler
  "Multimethod to handle Sente `event-msg`s"
  :id ; Dispatch on event-id
  )

(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler
  :default ; Default/fallback case (no other matching handler)
  [{:as ev-msg :keys [event]}]
  (println "Unhandled event: %s" event))

(defmethod -event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (let [[old-state-map new-state-map] (have vector? ?data)]
    (if (:first-open? new-state-map)
      (println "Channel socket successfully established!: %s" new-state-map)
      (println "Channel socket state change: %s"              new-state-map))))

(defmethod -event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (println "Push event from server: %s" ?data))

(defmethod -event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (println "Handshake: %s" ?data)))

(defmethod -event-msg-handler :room/create
  [{:keys [event]}]
  (let [[_ data] event]
    (js/console.log (str data))))
