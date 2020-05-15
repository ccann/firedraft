(ns firedraft.routes.ws
  (:require [clojure.set :as set]
            [clojure.tools.logging :as log]
            [firedraft.middleware :as middleware]
            [mount.core :refer [defstate]]
            [ring.middleware.keyword-params :as ring.mw.keyword-params]
            [ring.middleware.params :as ring.mw.params]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]))

(defn client-id [ring-req]
  (get-in ring-req [:params :client-id]))

(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! (get-sch-adapter)
                                  {:user-id-fn client-id})]
  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  ;; ChannelSocket's receive channel
  (def ch-chsk ch-recv)
  ;; ChannelSocket's send API fn
  (def send! send-fn)
  ;; watchable, read-only atom
  (def connected-uids connected-uids))

(add-watch connected-uids
           :connected-uids
           (fn [_ _ old new]
             (let [[old-ws new-ws] [(set (:any old))
                                    (set (:any new))]]
               (when-let [new-uid (first (set/difference new-ws old-ws))]
                 (log/info :uid-connected new-uid)))))

(defmulti handle-event
  "Multimethod to handle Sente `event-msg`s. Dispatch on event ID."
  :id)

(defmethod handle-event :chsk/ws-ping [_] (log/debug "ws ping"))

(defmethod handle-event :chsk/uidport-open
  [{:keys [?data]}]
  (log/debug :uidport-opened {:uid ?data}))

(defmethod handle-event :default
  [{:keys [event ?reply-fn]}]
  (log/debugf "Unhandled event: %s" event)
  (when ?reply-fn
    (?reply-fn {:umatched-event-as-echoed-from-server event})))

(defn event-handler
  "Non-blocking event handler for Sente event messages"
  [msg]
  ;; Handle event-msgs on a single thread
  (handle-event msg))


;; performance note: since your `event-msg-handler` fn will be executed
;; within a simple go block, you'll want this fn to be ~non-blocking
;; (you'll especially want to avoid blocking IO) to avoid starving the
;; core.async thread pool under load. To avoid blocking, you can use futures,
;; agents, core.async, etc. as appropriate.
;; Or for simple automatic future-based threading of every request, enable
;; the `:simple-auto-threading?` opt (disabled by default).

(defstate chsk-router
  :start (sente/start-chsk-router! ch-chsk event-handler
                                   {:simple-auto-threading? true})
  :stop (chsk-router))

(defn routes []
  [""
   ["/ws"
    {:middleware [ring.mw.params/wrap-params
                  ring.mw.keyword-params/wrap-keyword-params
                  middleware/wrap-csrf]
     :get (fn [req] (ring-ajax-get-or-ws-handshake req))
     :post (fn [req] (ring-ajax-post req))}]])
