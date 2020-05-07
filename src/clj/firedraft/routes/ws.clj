(ns firedraft.routes.ws
  (:require [clojure.tools.logging :as log]
            [mount.core :refer [defstate]]
            [ring.middleware.keyword-params :as ring.mw.keyword-params]
            [ring.middleware.params :as ring.mw.params]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit  :refer [get-sch-adapter]]
            [clojure.set :as set]))

(defn client-id [ring-req]
  (get-in ring-req [:params :client-id]))

(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! (get-sch-adapter)
                                  ;; TODO: add CSRF protection
                                  {:csrf-token-fn nil
                                   :user-id-fn client-id})]
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
  (handle-event msg)
  ;; (future (handle-event msg)) ; Handle event-msgs on a thread pool
  )

(defstate chsk-router
  :start (sente/start-chsk-router! ch-chsk event-handler)
  :stop (chsk-router))

;; (defn login-handler
;;   "Here's where you'll add your server-side login/auth procedure (Friend, etc.).
;;   In our simplified example we'll just always successfully authenticate the user
;;   with whatever user-id they provided in the auth request."
;;   [ring-req]
;;   (let [{:keys [session params]} ring-req
;;         {:keys [user-id]} params]
;;     (log/debugf "Login request: %s" params)
;;     {:status 200 :session (assoc session :uid user-id)}))

(defn routes []
  [""
   ["/ws"
    {:middleware [ring.mw.params/wrap-params
                  ring.mw.keyword-params/wrap-keyword-params
                  ;; middleware/wrap-csrf
                  ;; middleware/wrap-formats
                  ]
     :get (fn [req] (ring-ajax-get-or-ws-handshake req))
     :post (fn [req] (ring-ajax-post req))}]
   #_["/login" {:get login-handler}]])
